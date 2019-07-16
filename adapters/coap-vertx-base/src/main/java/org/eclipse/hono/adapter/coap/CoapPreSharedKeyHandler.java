/**
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.auth.PreSharedKeyIdentity;
import org.eclipse.californium.scandium.dtls.PskPublicInformation;
import org.eclipse.californium.scandium.dtls.pskstore.PskStore;
import org.eclipse.californium.scandium.util.ServerNames;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.vertx.core.Context;
import io.vertx.core.Future;

/**
 * A coap pre-shared-key store based on a credentials service client.
 */
public class CoapPreSharedKeyHandler implements PskStore, CoapAuthenticationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CoapPreSharedKeyHandler.class);

    /**
     * The vert.x context to run interactions with Hono services on.
     */
    private final Context context;
    /**
     * Credentials provider for pre-shared-key secrets.
     */
    private final CredentialsClientFactory credentialsClientFactory;
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
     * @param context The vert.x context to run on.
     * @param config The adapter configuration. Specify the minimum and maximum cache size and the split of the identity
     *            into authentication id and tenant
     * @param credentialsClientFactory The factory to use for creating a Credentials service client.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    @Autowired
    public CoapPreSharedKeyHandler(
            final Context context,
            final CoapAdapterProperties config,
            final CredentialsClientFactory credentialsClientFactory) {

        this.context = Objects.requireNonNull(context);
        this.config = Objects.requireNonNull(config);
        this.credentialsClientFactory = Objects.requireNonNull(credentialsClientFactory);
        this.devices = Caffeine.newBuilder()
                .softValues()
                .initialCapacity(config.getDeviceCacheMinSize())
                .maximumSize(config.getDeviceCacheMaxSize())
                .build();
    }

    /**
     * Gets the pre-shared key for an identity used by a device in a PSK based DTLS
     * handshake.
     * <p>
     * On success, add hono device to cache.
     * 
     * @param handshakeIdentity The identity used by the device.
     * @return A future completed with the key or failed with a {@link ServiceInvocationException}.
     */
    protected final Future<byte[]> getSharedKeyForDevice(final PreSharedKeyDeviceIdentity handshakeIdentity) {

        Objects.requireNonNull(handshakeIdentity);
        return credentialsClientFactory.getOrCreateCredentialsClient(handshakeIdentity.getTenantId())
                .compose(client -> client.get(handshakeIdentity.getType(), handshakeIdentity.getAuthId()))
                .compose((credentials) -> {
                    final byte[] key = getCandidateKey(credentials);
                    if (key != null) {
                        devices.put(handshakeIdentity,
                                new Device(handshakeIdentity.getTenantId(), credentials.getDeviceId()));
                        return Future.succeededFuture(key);
                    } else {
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED,
                                "no shared key registered for identity"));
                    }
                });
    }

    /**
     * Extracts the (pre-shared) key from the candidate secret(s) on record for the device.
     * 
     * @param credentialsOnRecord The credentials on record as returned by the Credentials service.
     * @return The key or {@code null} if no candidate secret is on record.
     */
    private static byte[] getCandidateKey(final CredentialsObject credentialsOnRecord) {

        final List<byte[]> keys = credentialsOnRecord.getCandidateSecrets(candidateSecret -> {
            return candidateSecret.getBinary(CredentialsConstants.FIELD_SECRETS_KEY, new byte[0]);
        });
        if (keys.isEmpty()) {
            return null;
        } else {
            return keys.get(0);
        }
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
    public byte[] getKey(final PskPublicInformation identity) {
        LOG.debug("getting PSK secret for identity [{}]", identity);
        final PreSharedKeyDeviceIdentity handshakeIdentity = getHandshakeIdentity(identity.getPublicInfoAsString());
        if (handshakeIdentity == null) {
            return null;
        }

        final CompletableFuture<byte[]> secret = new CompletableFuture<>();
        context.runOnContext((v) -> {
            getSharedKeyForDevice(handshakeIdentity).setHandler((getAttempt) -> {
                if (getAttempt.succeeded()) {
                    secret.complete(getAttempt.result());
                } else {
                    secret.completeExceptionally(getAttempt.cause());
                }
            });
        });
        try {
            // credentials client will time out
            return secret.join();
        } catch (final CompletionException | CancellationException e) {
            LOG.info("failed to look up shared secret for key", e);
        }
        LOG.debug("no candidate PSK secret found for identity [{}]", identity);
        return null;
    }

    @Override
    public byte[] getKey(final ServerNames serverNames, final PskPublicInformation identity) {
        // for now, don't support serverNames indication
        // maybe extended in the future to provide tenant identity
        return getKey(identity);
    }

    @Override
    public PskPublicInformation getIdentity(final InetSocketAddress inetAddress) {
        // not used by dtls server, and role exchange is not supported!
        return null;
    }

    @Override
    public PskPublicInformation getIdentity(final InetSocketAddress peerAddress, final ServerNames virtualHost) {
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
