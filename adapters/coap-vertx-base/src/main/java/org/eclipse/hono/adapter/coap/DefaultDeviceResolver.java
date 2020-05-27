/**
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.crypto.SecretKey;

import org.eclipse.californium.elements.auth.AdditionalInfo;
import org.eclipse.californium.elements.auth.PreSharedKeyIdentity;
import org.eclipse.californium.scandium.auth.AdvancedApplicationLevelInfoSupplier;
import org.eclipse.californium.scandium.auth.ApplicationLevelInfoSupplier;
import org.eclipse.californium.scandium.dtls.ConnectionId;
import org.eclipse.californium.scandium.dtls.PskPublicInformation;
import org.eclipse.californium.scandium.dtls.PskSecretResult;
import org.eclipse.californium.scandium.dtls.PskSecretResultHandler;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedPskStore;
import org.eclipse.californium.scandium.util.SecretUtil;
import org.eclipse.californium.scandium.util.ServerNames;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;


/**
 * A Hono Credentials service based implementation of Scandium's authentication related interfaces.
 *
 */
public class DefaultDeviceResolver implements ApplicationLevelInfoSupplier, AdvancedPskStore, AdvancedApplicationLevelInfoSupplier {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDeviceResolver.class);

    /**
     * The vert.x context to run interactions with Hono services on.
     */
    private final Context context;
    private final Tracer tracer;
    private final String adapterName;
    private final CoapAdapterProperties config;
    private final CredentialsClientFactory credentialsClientFactory;
    private volatile PskSecretResultHandler californiumResultHandler;

    /**
     * Creates a new resolver.
     *
     * @param vertxContext The vert.x context to run on.
     * @param tracer The OpenTracing tracer.
     * @param adapterName The name of the protocol adapter.
     * @param config The configuration properties.
     * @param credentialsClientFactory The factory to use for creating clients to the Credentials service.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public DefaultDeviceResolver(
            final Context vertxContext,
            final Tracer tracer,
            final String adapterName,
            final CoapAdapterProperties config,
            final CredentialsClientFactory credentialsClientFactory) {

        this.context = Objects.requireNonNull(vertxContext);
        this.tracer = Objects.requireNonNull(tracer);
        this.adapterName = Objects.requireNonNull(adapterName);
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
            final SecretKey key = SecretUtil.create(encodedKey, "PSK");
            Arrays.fill(encodedKey, (byte) 0);
            return key;
        } catch (IllegalArgumentException | ClassCastException e) {
            return null;
        }
    }

    private Span newSpan(final String operation) {
        return tracer.buildSpan(operation)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(Tags.COMPONENT.getKey(), adapterName)
                .start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdditionalInfo getInfo(final Principal clientIdentity) {
        return getInfo(clientIdentity, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdditionalInfo getInfo(final Principal clientIdentity, final Object customArgument) {
        final Map<String, Object> result = new HashMap<>();

        if (clientIdentity instanceof PreSharedKeyIdentity) {
            final Span span = newSpan("PSK-getDeviceIdentityInfo");
            final PreSharedKeyDeviceIdentity deviceIdentity = getHandshakeIdentity(span, clientIdentity.getName());
            if (customArgument instanceof String) {
                // device id from previous lookup
                final String deviceId = (String) customArgument;
                result.put("hono-device", new Device(deviceIdentity.getTenantId(), deviceId));
                TracingHelper.TAG_TENANT_ID.set(span, deviceIdentity.getTenantId());
                TracingHelper.TAG_AUTH_ID.set(span, deviceIdentity.getAuthId());
                TracingHelper.TAG_DEVICE_ID.set(span, deviceId);
                span.log("successfully recovered device identity");
            } else {
                // session resumption, so no custom-argument, because there was no previous lookup 
                TracingHelper.TAG_TENANT_ID.set(span, deviceIdentity.getTenantId());
                TracingHelper.TAG_AUTH_ID.set(span, deviceIdentity.getAuthId());
                final CompletableFuture<CredentialsObject> credentialsResult = new CompletableFuture<>();
                context.runOnContext(go -> {
                    credentialsClientFactory.getOrCreateCredentialsClient(deviceIdentity.getTenantId())
                            .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY,
                                    deviceIdentity.getAuthId(), new JsonObject(), span.context()))
                            .onSuccess(credentials -> credentialsResult.complete(credentials))
                            .onFailure(t -> credentialsResult.completeExceptionally(t));
                });
                try {
                    // client will only wait a limited period of time,
                    // so no need to use get(Long, TimeUnit) here
                    final CredentialsObject credentials = credentialsResult.join();
                    result.put("hono-device", new Device(deviceIdentity.getTenantId(), credentials.getDeviceId()));
                    span.log("successfully resolved device identity");
                    TracingHelper.TAG_DEVICE_ID.set(span, credentials.getDeviceId());
                } catch (final CompletionException e) {
                    TracingHelper.logError(span, "could not resolve auhenticated principal", e);
                    LOG.debug("could not resolve authenticated principal [type: {}, tenant-id: {}, auth-id: {}]",
                            clientIdentity.getClass(), deviceIdentity.getTenantId(), deviceIdentity.getAuthId(), e);
                }
            }
            span.finish();
        } else {
            LOG.info("unsupported Principal type: {}", clientIdentity.getClass());
        }
        return AdditionalInfo.from(result);
    }

    /**
     * Load credentials for an identity used by a device in a PSK based DTLS
     * handshake.
     * <p>
     *
     * @param cid the connection id to report the result.
     * @param identity the psk identity of the device.
     */
    private void loadCredentialsForDevice(final ConnectionId cid, final PskPublicInformation identity) {
        final String publicInfo = identity.getPublicInfoAsString();
        LOG.debug("getting PSK secret for identity [{}]", publicInfo);

        final Span span = newSpan("PSK-getSecretKey");

        final PreSharedKeyDeviceIdentity handshakeIdentity = getHandshakeIdentity(span, publicInfo);
        if (handshakeIdentity == null) {
            span.finish();
            return;
        }

        TracingHelper.TAG_TENANT_ID.set(span, handshakeIdentity.getTenantId());
        TracingHelper.TAG_AUTH_ID.set(span, handshakeIdentity.getAuthId());

        credentialsClientFactory
                .getOrCreateCredentialsClient(handshakeIdentity.getTenantId())
                .compose(client -> client.get(handshakeIdentity.getType(), handshakeIdentity.getAuthId(),
                        new JsonObject(), span.context()))
                .onComplete(credentialsResult -> {

                    SecretKey key = null;
                    String deviceId = null;

                    if (credentialsResult.succeeded()) {
                        final CredentialsObject credentials = credentialsResult.result();
                        deviceId = credentials.getDeviceId();
                        TracingHelper.TAG_DEVICE_ID.set(span, deviceId);
                        key = getCandidateKey(credentials);
                    }
                    if (key != null && deviceId != null) {
                        span.log("successfully retrieved PSK for device");
                    } else {
                        final Throwable cause = credentialsResult.cause();
                        if (cause != null) {
                            TracingHelper.logError(span, "could not find PSK for device", cause);
                        } else {
                            TracingHelper.logError(span, "could not find PSK for device");
                        }
                        LOG.debug("error retrieving credentials for PSK identity [{}]",
                                publicInfo);
                        SecretUtil.destroy(key);
                        key = null;
                        deviceId = null;
                    }
                    span.finish();
                    californiumResultHandler.apply(new PskSecretResult(cid, identity, key, deviceId));
                });
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
    private PreSharedKeyDeviceIdentity getHandshakeIdentity(final Span span, final String identity) {
        final String splitRegex = config.isSingleTenant() ? null : config.getIdSplitRegex();
        return PreSharedKeyDeviceIdentity.create(span, identity, splitRegex);
    }

    @Override
    public boolean hasEcdhePskSupported() {
        return true;
    }

    @Override
    public PskSecretResult requestPskSecretResult(final ConnectionId cid, final ServerNames serverName,
            final PskPublicInformation identity, final String hmacAlgorithm, final SecretKey otherSecret,
            final byte[] seed) {

        context.runOnContext((v) -> loadCredentialsForDevice(cid, identity));
        return null;
    }

    @Override
    public void setResultHandler(final PskSecretResultHandler resultHandler) {
        californiumResultHandler = resultHandler;
    }

}
