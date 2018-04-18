/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.adapter.coap;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.auth.PreSharedKeyIdentity;
import org.eclipse.californium.elements.auth.RawPublicKeyIdentity;
import org.eclipse.californium.elements.auth.X509CertPath;
import org.eclipse.californium.scandium.dtls.pskstore.PskStore;
import org.eclipse.californium.scandium.util.ServerNames;
import org.eclipse.hono.service.auth.device.CredentialsApiAuthProvider;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.DeviceCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * A coap credentials provider based on a credentials service client.
 * <p>
 * Implements a {@link PskStore} to support pre-shared-key handshakes.
 */
public class CoapCredentialsStore implements PskStore {

    private static final Logger LOG = LoggerFactory.getLogger(CoapCredentialsStore.class);

    /**
     * Vertx to be used by this store.
     */
    private final Vertx vertx;
    /**
     * Credentials service client.
     */
    private final PreSharedKeyCredentialsProvider credentialsProvider;
    /**
     * Cache mapping principal information into hono devices.
     */
    private final Cache<Principal, Device> devices;
    /**
     * Configuration used to split identity into authentication id and tenant.
     */
    private final CoapAdapterProperties config;

    /**
     * Creates a new coap credentails store for a given configuration.
     * 
     * @param vertx The vertx instance to use.
     * @param config The configuration.
     * @param authProvider extended pre-shared-key authentication provider.
     * @throws NullPointerException if any of the params is {@code null}.
     */
    @Autowired
    public CoapCredentialsStore(final Vertx vertx, final CoapAdapterProperties config,
            final PreSharedKeyCredentialsProvider authProvider) {
        this.vertx = Objects.requireNonNull(vertx);
        this.config = Objects.requireNonNull(config);
        this.credentialsProvider = Objects.requireNonNull(authProvider);
        final CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder()
                .concurrencyLevel(1)
                .softValues()
                .initialCapacity(config.getDeviceCacheMinSize())
                .maximumSize(config.getDeviceCacheMaxSize());
        this.devices = builder.build();
    }

    /**
     * Get pre-shared-key for device from credentials service.
     * <p>
     * On success, add hono device to cache.
     * 
     * @param handshakeIdentity pre-shared-key identity of device.
     * @return future with pre-shared-key.
     */
    protected final Future<byte[]> getSharedKeyForDevice(final PreSharedKeyTenantIdentity handshakeIdentity) {
        Objects.requireNonNull(handshakeIdentity);
        if (credentialsProvider == null) {
            return Future.failedFuture(new IllegalStateException("Credentials API client is not set"));
        } else {
            final Future<byte[]> secret = Future.future();
            return credentialsProvider.getCredentialsForDevice(handshakeIdentity).compose((credentials) -> {
                final byte[] key = PreSharedKeyCredentials.getCredentialsSecret(handshakeIdentity, credentials);
                if (key != null) {
                    devices.put(handshakeIdentity,
                            new Device(handshakeIdentity.getTenantId(), credentials.getDeviceId()));
                }
                secret.complete(key);
            }, secret);
        }
    }

    /**
     * Create tenant aware identity based on the provided pre-shared-key handshake identity.
     * 
     * @param identity pre-shared-key handshake identity.
     * @return tenant aware identity.
     */
    public PreSharedKeyTenantIdentity getHandshakeIdentity(final String identity) {
        final String splitRegex = config.isSingleTenant() ? null : config.getIdSplitRegex();
        return PreSharedKeyTenantIdentity.create(identity, splitRegex);
    }

    @Override
    public byte[] getKey(final String identity) {
        LOG.debug("get secret key for {}", identity);
        final PreSharedKeyTenantIdentity handshakeIdentity = getHandshakeIdentity(identity);
        if (handshakeIdentity == null) {
            return null;
        }
        //
        final CompletableFuture<byte[]> secret = new CompletableFuture<>();
        vertx.runOnContext((v) -> {
            getSharedKeyForDevice(handshakeIdentity).setHandler((result) -> secret.complete(result.result()));
        });
        try {
            // timeout, don't block handshake too long
            return secret.get(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        } catch (ExecutionException e) {
        } catch (TimeoutException e) {
        }
        LOG.warn("missing secret key for {}!", identity);
        return null;
    }

    @Override
    public byte[] getKey(final ServerNames serverNames, final String identity) {
        // for now, don't support serverNames indication
        // maybe extended in the future to provide tenant
        // would require solution for californium issue #511
        return getKey(identity);
    }

    @Override
    public String getIdentity(final InetSocketAddress inetAddress) {
        // not used by dtls server, and role exchange not supported!
        return null;
    }

    /**
     * Get hono device for pre-shared-key handshake identity from cache.
     * 
     * @param identity identity provided by pre-shared-key handshake. Contains tenant and authentication id.
     * @return hono device, or {@code null}, if not available in cache.
     */
    public Device getCachedDevice(final String identity) {
        final PreSharedKeyTenantIdentity handshakeIdentity = getHandshakeIdentity(identity);
        if (handshakeIdentity != null) {
            return devices.getIfPresent(handshakeIdentity);
        }
        return null;
    }

    /**
     * Get authorized hono device from URI identity.
     * 
     * Uses {@link #credentialsProvider}'s
     * {@link CredentialsApiAuthProvider#authenticate(DeviceCredentials, io.vertx.core.Handler)} to authenticate the
     * identity extracted from the provided URI.
     * 
     * @param exchange coap exchange with URI.
     * @param authLevel path level to extract URI identity. e.g. for "/telemetry/tenant/authid/passbase64" the level is
     *            1.
     * @return future of hono device.
     */
    public Future<Device> getAuthorizedDeviceFromUri(final CoapExchange exchange, final int authLevel) {
        final Future<Device> result = Future.future();
        final List<String> path = exchange.getRequestOptions().getUriPath();
        if (path.size() == authLevel + 3) {
            final DeviceCredentials credentials = new PreSharedKeyCredentials(path.get(authLevel),
                    path.get(authLevel + 1), path.get(authLevel + 2));
            credentialsProvider.authenticate(credentials, result);
        } else {
            result.fail("URI doesn't contain authentication!");
        }
        return result;
    }

    /**
     * Get indirect (gateway) authorized hono device from URI identity.
     * 
     * @param exchange coap exchange with URI.
     * @param authLevel path level to extract URI identity. e.g. for "/telemetry/tenant/deviceid" the level is 1.
     * @return the indirect authorized hono device.
     */
    public Device getIndirectAuthorizedDeviceFromUri(final CoapExchange exchange, final int authLevel) {
        final List<String> path = exchange.getRequestOptions().getUriPath();
        if (path.size() == authLevel + 2) {
            return new Device(path.get(authLevel), path.get(authLevel + 1));
        }
        return null;
    }

    /**
     * Get extended hono device.
     * 
     * @param exchange coap exchange with URI.
     * @param authLevel path level to extract URI identity. e.g. for "/telemetry/tenant/deviceid" the level is 1.
     * @return future with extended device.
     */
    public Future<ExtendedDevice> getExtendedDevice(final CoapExchange exchange, final int authLevel) {
        Device authenticatedDevice = null;
        final Principal peer = exchange.advanced().getRequest().getSourceContext().getPeerIdentity();
        if (PreSharedKeyIdentity.class.isInstance(peer)) {
            LOG.debug("psk {}", peer.getName());
            authenticatedDevice = getCachedDevice(peer.getName());
        } else if (RawPublicKeyIdentity.class.isInstance(peer)) {
            // currently not supported by credentials service,
            // but maybe extended in the future.
            // RPK is currently used for anonymous encryption,
            // therefore additional authentication is required.
            LOG.debug("rpk {}", peer.getName());
        } else if (X509CertPath.class.isInstance(peer)) {
            // currently not supported by credentials service,
            // but maybe extended in the future.
            // x509 is currently used for anonymous encryption,
            // therefore additional authentication is required.
            LOG.debug("x509 {}", peer.getName());
        }
        if (authLevel > 0) {
            if (authenticatedDevice != null) {
                final Device device = getIndirectAuthorizedDeviceFromUri(exchange, authLevel);
                if (device == null) {
                    LOG.debug("device from peer {}", authenticatedDevice.getDeviceId());
                    return Future.succeededFuture(new ExtendedDevice(authenticatedDevice, authenticatedDevice));
                } else {
                    LOG.debug("device from uri {}", device.getDeviceId());
                    return Future.succeededFuture(new ExtendedDevice(authenticatedDevice, device));
                }
            } else {
                return getAuthorizedDeviceFromUri(exchange, authLevel).compose((dev) -> {
                    LOG.debug("device authorized by uri {}", dev.getDeviceId());
                    return Future.succeededFuture(new ExtendedDevice(dev, dev));
                });
            }
        } else if (authenticatedDevice != null) {
            LOG.debug("device from peer {}", authenticatedDevice.getDeviceId());
            return Future.succeededFuture(new ExtendedDevice(authenticatedDevice, authenticatedDevice));
        } else {
            return Future.failedFuture("no authentication info available!");
        }
    }
}
