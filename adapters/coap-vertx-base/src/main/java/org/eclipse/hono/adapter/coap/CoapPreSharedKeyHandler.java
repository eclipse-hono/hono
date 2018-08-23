/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.coap;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.auth.PreSharedKeyIdentity;
import org.eclipse.californium.scandium.dtls.pskstore.PskStore;
import org.eclipse.californium.scandium.util.ServerNames;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.service.auth.device.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * A coap pre-shared-key store based on a credentials service client.
 */
public class CoapPreSharedKeyHandler implements PskStore, CoapAuthenticationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CoapPreSharedKeyHandler.class);

    /**
     * Vertx to be used by this pre-shared-key store. The {@link PskStore} callbacks are execute in a other threading
     * context and therefore they must be passed into vertx by {@link Vertx#runOnContext(io.vertx.core.Handler)}.
     */
    private final Vertx vertx;
    /**
     * Credentials provider for pre-shared-key secrets.
     */
    private final HonoClient credentialsServiceClient;
    /**
     * Cache mapping principal information to hono devices.
     */
    private final Cache<PreSharedKeyDeviceIdentity, Device> devices;
    /**
     * Configuration used to split identity into authentication id and tenant.
     */
    private final CoapAdapterProperties config;

    /**
     * Creates a new coap pre-shared-key for a given configuration.
     * 
     * @param vertx The vertx instance to use.
     * @param config The adapter configuration. Specify the minimum and maximum cache size and the split of the identity
     *            into authentication id and tenant
     * @param credentialsServiceClient The credentials service client.
     * @throws NullPointerException if any of the params is {@code null}.
     */
    @Autowired
    public CoapPreSharedKeyHandler(final Vertx vertx, final CoapAdapterProperties config,
            final HonoClient credentialsServiceClient) {
        this.vertx = Objects.requireNonNull(vertx);
        this.config = Objects.requireNonNull(config);
        this.credentialsServiceClient = Objects.requireNonNull(credentialsServiceClient);
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
    protected final Future<byte[]> getSharedKeyForDevice(final PreSharedKeyDeviceIdentity handshakeIdentity) {
        Objects.requireNonNull(handshakeIdentity);
        return credentialsServiceClient.getOrCreateCredentialsClient(handshakeIdentity.getTenantId())
                .compose(client -> client.get(handshakeIdentity.getType(), handshakeIdentity.getAuthId()))
                .compose((credentials) -> {
                    final byte[] key = handshakeIdentity.getCredentialsSecret(credentials);
                    if (key != null) {
                        devices.put(handshakeIdentity,
                                new Device(handshakeIdentity.getTenantId(), credentials.getDeviceId()));
                        return Future.succeededFuture(key);
                    } else {
                        return Future.failedFuture("secret key missing!");
                    }
                });
    }

    /**
     * Create tenant aware identity based on the provided pre-shared-key handshake identity.
     * 
     * @param identity pre-shared-key handshake identity.
     * @return tenant aware identity.
     */
    public PreSharedKeyDeviceIdentity getHandshakeIdentity(final String identity) {
        final String splitRegex = config.isSingleTenant() ? null : config.getIdSplitRegex();
        return PreSharedKeyDeviceIdentity.create(identity, splitRegex);
    }

    @Override
    public byte[] getKey(final String identity) {
        LOG.debug("get secret key for {}", identity);
        final PreSharedKeyDeviceIdentity handshakeIdentity = getHandshakeIdentity(identity);
        if (handshakeIdentity == null) {
            return null;
        }
        //
        final CompletableFuture<byte[]> secret = new CompletableFuture<>();
        vertx.runOnContext((v) -> {
            getSharedKeyForDevice(handshakeIdentity).setHandler((result) -> {
                if (result.succeeded()) {
                    secret.complete(result.result());
                } else {
                    secret.completeExceptionally(result.cause());
                }
            });
        });
        try {
            // timeout, don't block handshake too long
            return secret.get(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        } catch (TimeoutException e) {
        } catch (CancellationException e) {
        } catch (ExecutionException e) {
        }
        LOG.warn("missing secret key for {}!", identity);
        return null;
    }

    @Override
    public byte[] getKey(final ServerNames serverNames, final String identity) {
        // for now, don't support serverNames indication
        // maybe extended in the future to provide tenant identity
        return getKey(identity);
    }

    @Override
    public String getIdentity(final InetSocketAddress inetAddress) {
        // not used by dtls server, and role exchange is not supported!
        return null;
    }

    @Override
    public String getIdentity(final InetSocketAddress peerAddress, final ServerNames virtualHost) {
        // not used by dtls server, and role exchange is not supported!
        return null;
    }

    /**
     * Get hono device for pre-shared-key handshake identity from cache.
     * 
     * @param identity identity provided by pre-shared-key handshake. Contains tenant and authentication id.
     * @return hono device, or {@code null}, if not available in cache.
     */
    public Device getCachedDevice(final String identity) {
        final PreSharedKeyDeviceIdentity handshakeIdentity = getHandshakeIdentity(identity);
        if (handshakeIdentity != null) {
            return devices.getIfPresent(handshakeIdentity);
        }
        return null;
    }

    @Override
    public Class<PreSharedKeyIdentity> getType() {
        return PreSharedKeyIdentity.class;
    }

    @Override
    public Future<Device> getAuthenticatedDevice(final CoapExchange exchange) {
        final Principal peer = exchange.advanced().getRequest().getSourceContext().getPeerIdentity();
        if (PreSharedKeyIdentity.class.isInstance(peer)) {
            LOG.debug("authenticate psk identity {}", peer.getName());
            final PreSharedKeyDeviceIdentity handshakeIdentity = getHandshakeIdentity(
                    ((PreSharedKeyIdentity) peer).getIdentity());
            if (handshakeIdentity != null) {
                final Device authorizedDevice = devices.getIfPresent(handshakeIdentity);
                if (authorizedDevice != null) {
                    return Future.succeededFuture(authorizedDevice);
                }
            }
            return Future.failedFuture("missing device for " + peer + "!");
        }
        return Future.failedFuture(new IllegalArgumentException("Principal not supported by this handler!"));
    }
}
