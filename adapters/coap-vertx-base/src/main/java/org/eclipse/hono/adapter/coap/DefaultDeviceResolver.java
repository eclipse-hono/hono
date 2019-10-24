/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.crypto.SecretKey;

import org.eclipse.californium.elements.auth.AdditionalInfo;
import org.eclipse.californium.elements.auth.PreSharedKeyIdentity;
import org.eclipse.californium.scandium.auth.ApplicationLevelInfoSupplier;
import org.eclipse.californium.scandium.dtls.PskPublicInformation;
import org.eclipse.californium.scandium.dtls.pskstore.PskStore;
import org.eclipse.californium.scandium.util.SecretUtil;
import org.eclipse.californium.scandium.util.ServerNames;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;


/**
 * A Hono Credentials service based implementation of Scandium's authentication related interfaces.
 *
 */
public class DefaultDeviceResolver implements ApplicationLevelInfoSupplier, PskStore {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDeviceResolver.class);

    /**
     * The vert.x context to run interactions with Hono services on.
     */
    private final Context context;
    private final CoapAdapterProperties config;
    private final CredentialsClientFactory credentialsClientFactory;

    /**
     * Creates a new resolver.
     * 
     * @param vertxContext The vert.x context to run on.
     * @param config The configuration properties.
     * @param credentialsClientFactory The factory to use for creating clients to the Credentials service.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public DefaultDeviceResolver(
            final Context vertxContext,
            final CoapAdapterProperties config,
            final CredentialsClientFactory credentialsClientFactory) {

        this.context = Objects.requireNonNull(vertxContext);
        this.config = Objects.requireNonNull(config);
        this.credentialsClientFactory = Objects.requireNonNull(credentialsClientFactory);
    }

    /**
     * Extracts the (pre-shared) key from the candidate secret(s) on record for the device.
     * 
     * @param credentialsOnRecord The credentials on record as returned by the Credentials service.
     * @return The key or {@code null} if no candidate secret is on record.
     */
    private static SecretKey getCandidateKey(final CredentialsObject credentialsOnRecord) {

        return credentialsOnRecord.getCandidateSecrets(candidateSecret -> getKey(candidateSecret))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static SecretKey getKey(final JsonObject candidateSecret) {
        try {
            final byte[] encodedKey = candidateSecret.getBinary(CredentialsConstants.FIELD_SECRETS_KEY);
            return SecretUtil.create(encodedKey, "AES");
        } catch (IllegalArgumentException | ClassCastException e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdditionalInfo getInfo(final Principal clientIdentity) {
        final Map<String, Object> result = new HashMap<>();

        if (clientIdentity instanceof PreSharedKeyIdentity) {
            final PreSharedKeyDeviceIdentity deviceIdentity = getHandshakeIdentity(clientIdentity.getName());
            final CompletableFuture<CredentialsObject> credentialsResult = new CompletableFuture<>();
            context.runOnContext(go -> {
                credentialsClientFactory
                .getOrCreateCredentialsClient(deviceIdentity.getTenantId())
                .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, deviceIdentity.getAuthId()))
                .setHandler(attempt -> {
                    if (attempt.succeeded()) {
                        credentialsResult.complete(attempt.result());
                    } else {
                        credentialsResult.completeExceptionally(attempt.cause());
                    }
                });
            });
            try {
                // client will only wait a limited period of time,
                // so no need to use get(Long, TimeUnit) here
                final CredentialsObject credentials = credentialsResult.join();
                result.put("hono-device", new Device(deviceIdentity.getTenantId(), credentials.getDeviceId()));
            } catch (final CompletionException e) {
                LOG.debug("could not resolve authenticated principal [type: {}, tenant-id: {}, auth-id: {}]",
                        clientIdentity.getClass(), deviceIdentity.getTenantId(), deviceIdentity.getAuthId(), e);
            }
        } else {
            LOG.info("unsupported Principal type: {}", clientIdentity.getClass());
        }
        return AdditionalInfo.from(result);
    }

    @Override
    public SecretKey getKey(final PskPublicInformation identity) {

        LOG.debug("getting PSK secret for identity [{}]", identity);
        final PreSharedKeyDeviceIdentity handshakeIdentity = getHandshakeIdentity(identity.getPublicInfoAsString());
        if (handshakeIdentity == null) {
            return null;
        }

        final CompletableFuture<SecretKey> secret = new CompletableFuture<>();
        context.runOnContext((v) -> {
            getSharedKeyForDevice(handshakeIdentity)
            .setHandler((getAttempt) -> {
                if (getAttempt.succeeded()) {
                    secret.complete(getAttempt.result());
                } else {
                    secret.completeExceptionally(getAttempt.cause());
                }
            });
        });
        try {
            // credentials client will wait limited time only
            return secret.join();
        } catch (final CompletionException e) {
            LOG.debug("error retrieving credentials for PSK identity [{}]", identity);
            return null;
        }
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
    private Future<SecretKey> getSharedKeyForDevice(final PreSharedKeyDeviceIdentity handshakeIdentity) {

        return credentialsClientFactory
                .getOrCreateCredentialsClient(handshakeIdentity.getTenantId())
                .compose(client -> client.get(handshakeIdentity.getType(), handshakeIdentity.getAuthId()))
                .compose((credentials) -> Optional.ofNullable(getCandidateKey(credentials))
                        .map(secret -> Future.succeededFuture(secret))
                        .orElseGet(() -> Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED,
                                "no shared key registered for identity"))));
    }

    @Override
    public SecretKey getKey(final ServerNames serverNames, final PskPublicInformation identity) {
        // for now, don't support serverNames indication
        // maybe extended in the future to provide tenant identity
        return getKey(identity);
    }

    @Override
    public PskPublicInformation getIdentity(final InetSocketAddress inetAddress) {
        throw new UnsupportedOperationException("this adapter does not support DTLS client role");
    }

    @Override
    public PskPublicInformation getIdentity(final InetSocketAddress peerAddress, final ServerNames virtualHost) {
        throw new UnsupportedOperationException("this adapter does not support DTLS client role");
    }

    /**
     * Create tenant aware identity based on the provided pre-shared-key handshake identity.
     * 
     * @param identity pre-shared-key handshake identity.
     * @return tenant aware identity.
     */
    private PreSharedKeyDeviceIdentity getHandshakeIdentity(final String identity) {
        final String splitRegex = config.isSingleTenant() ? null : config.getIdSplitRegex();
        return PreSharedKeyDeviceIdentity.create(identity, splitRegex);
    }
}
