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
import org.eclipse.hono.adapter.client.registry.CredentialsClient;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;


/**
 * A Hono Credentials service based implementation of Scandium's authentication related interfaces.
 *
 */
public class DefaultDeviceResolver implements ApplicationLevelInfoSupplier, AdvancedPskStore, AdvancedApplicationLevelInfoSupplier {

    /**
     * Key in the extended info of the peer identity containing the auth-id used for authentication.
     */
    public static final String EXT_INFO_KEY_HONO_AUTH_ID = "hono-auth-id";
    /**
     * Key in the extended info of the peer identity containing the authenticated Device.
     */
    public static final String EXT_INFO_KEY_HONO_DEVICE = "hono-device";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDeviceResolver.class);

    /**
     * The vert.x context to run interactions with Hono services on.
     */
    private final Context context;
    private final Tracer tracer;
    private final String adapterName;
    private final CoapAdapterProperties config;
    private final CredentialsClient credentialsClient;
    private final TenantClient tenantClient;
    private volatile PskSecretResultHandler californiumResultHandler;

    /**
     * Creates a new resolver.
     *
     * @param vertxContext The vert.x context to run on.
     * @param tracer The OpenTracing tracer.
     * @param adapterName The name of the protocol adapter.
     * @param config The configuration properties.
     * @param credentialsClientFactory The factory to use for creating clients to the Credentials service.
     * @param tenantClient The client to use for accessing the Tenant service.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public DefaultDeviceResolver(
            final Context vertxContext,
            final Tracer tracer,
            final String adapterName,
            final CoapAdapterProperties config,
            final CredentialsClient credentialsClientFactory,
            final TenantClient tenantClient) {

        this.context = Objects.requireNonNull(vertxContext);
        this.tracer = Objects.requireNonNull(tracer);
        this.adapterName = Objects.requireNonNull(adapterName);
        this.config = Objects.requireNonNull(config);
        this.credentialsClient = Objects.requireNonNull(credentialsClientFactory);
        this.tenantClient = tenantClient;
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
            if (customArgument instanceof String) {
                // device id from previous lookup
                final String deviceId = (String) customArgument;
                final PreSharedKeyDeviceIdentity deviceIdentity = getHandshakeIdentity(clientIdentity.getName(), null);
                result.put(EXT_INFO_KEY_HONO_DEVICE, new Device(deviceIdentity.getTenantId(), deviceId));
                result.put(EXT_INFO_KEY_HONO_AUTH_ID, deviceIdentity.getAuthId());
            } else {
                // session resumption, so no custom-argument, because there was no previous lookup
                final Span span = newSpan("PSK-getDeviceIdentityInfo");
                final PreSharedKeyDeviceIdentity deviceIdentity = getHandshakeIdentity(clientIdentity.getName(), span);
                TracingHelper.TAG_TENANT_ID.set(span, deviceIdentity.getTenantId());
                TracingHelper.TAG_AUTH_ID.set(span, deviceIdentity.getAuthId());
                final CompletableFuture<CredentialsObject> credentialsResult = new CompletableFuture<>();
                context.runOnContext(go -> {
                    applyTraceSamplingPriority(deviceIdentity, span)
                            .compose(v -> credentialsClient.get(
                                    deviceIdentity.getTenantId(),
                                    CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY,
                                    deviceIdentity.getAuthId(),
                                    new JsonObject(),
                                    span.context()))
                            .onSuccess(credentials -> credentialsResult.complete(credentials))
                            .onFailure(t -> credentialsResult.completeExceptionally(t));
                });
                try {
                    // client will only wait a limited period of time,
                    // so no need to use get(Long, TimeUnit) here
                    final CredentialsObject credentials = credentialsResult.join();
                    result.put(EXT_INFO_KEY_HONO_DEVICE, new Device(deviceIdentity.getTenantId(), credentials.getDeviceId()));
                    result.put(EXT_INFO_KEY_HONO_AUTH_ID, deviceIdentity.getAuthId());
                    span.log("successfully resolved device identity");
                    TracingHelper.TAG_DEVICE_ID.set(span, credentials.getDeviceId());
                } catch (final CompletionException e) {
                    TracingHelper.logError(span, "could not resolve authenticated principal", e);
                    LOG.debug("could not resolve authenticated principal [type: {}, tenant-id: {}, auth-id: {}]",
                            clientIdentity.getClass(), deviceIdentity.getTenantId(), deviceIdentity.getAuthId(), e);
                }
                span.finish();
            }
        } else {
            LOG.info("unsupported Principal type: {}", clientIdentity.getClass());
        }
        return AdditionalInfo.from(result);
    }

    /**
     * Load credentials for an identity used by a device in a PSK based DTLS handshake.
     *
     * @param cid the connection id to report the result.
     * @param identity the psk identity of the device.
     */
    private void loadCredentialsForDevice(final ConnectionId cid, final PskPublicInformation identity) {
        final String publicInfo = identity.getPublicInfoAsString();
        LOG.debug("getting PSK secret for identity [{}]", publicInfo);

        final Span span = newSpan("PSK-getDeviceCredentials");

        final PreSharedKeyDeviceIdentity handshakeIdentity = getHandshakeIdentity(publicInfo, span);
        if (handshakeIdentity == null) {
            span.finish();
            return;
        }

        TracingHelper.TAG_TENANT_ID.set(span, handshakeIdentity.getTenantId());
        TracingHelper.TAG_AUTH_ID.set(span, handshakeIdentity.getAuthId());

        applyTraceSamplingPriority(handshakeIdentity, span)
                .compose(v -> credentialsClient.get(
                        handshakeIdentity.getTenantId(),
                        handshakeIdentity.getType(),
                        handshakeIdentity.getAuthId(),
                        new JsonObject(),
                        span.context()))
                .map(credentials -> {
                    final String deviceId = credentials.getDeviceId();
                    TracingHelper.TAG_DEVICE_ID.set(span, deviceId);
                    final SecretKey key = getCandidateKey(credentials);
                    if (key == null) {
                        TracingHelper.logError(span, "PSK credentials for device do not contain proper key");
                        return new PskSecretResult(cid, identity, null, null);
                    } else {
                        span.log("successfully retrieved PSK for device");
                        // set deviceId as customArgument here
                        return new PskSecretResult(cid, identity, key, deviceId);
                    }
                })
                .otherwise(t -> {
                    TracingHelper.logError(span, "could not retrieve PSK credentials for device", t);
                    LOG.debug("error retrieving credentials for PSK identity [{}]", publicInfo, t);
                    return new PskSecretResult(cid, identity, null, null);
                })
                .onSuccess(result -> {
                    span.finish();
                    californiumResultHandler.apply(result);
                });
    }

    private Future<Void> applyTraceSamplingPriority(final PreSharedKeyDeviceIdentity deviceIdentity, final Span span) {
        return tenantClient.get(deviceIdentity.getTenantId(), span.context())
                .map(tenantObject -> {
                    TracingHelper.setDeviceTags(span, tenantObject.getTenantId(), null, deviceIdentity.getAuthId());
                    TenantTraceSamplingHelper.applyTraceSamplingPriority(tenantObject, deviceIdentity.getAuthId(), span);
                    return (Void) null;
                })
                .recover(t -> Future.succeededFuture());
    }

    @Override
    public PskPublicInformation getIdentity(final InetSocketAddress peerAddress, final ServerNames virtualHost) {
        throw new UnsupportedOperationException("this adapter does not support DTLS client role");
    }

    /**
     * Create tenant aware identity based on the provided pre-shared-key handshake identity.
     *
     * @param identity pre-shared-key handshake identity.
     * @param span The current open tracing span or {@code null}.
     * @return tenant aware identity.
     */
    private PreSharedKeyDeviceIdentity getHandshakeIdentity(final String identity, final Span span) {
        return PreSharedKeyDeviceIdentity.create(identity, config.getIdSplitRegex(), span);
    }

    @Override
    public boolean hasEcdhePskSupported() {
        return true;
    }

    @Override
    public PskSecretResult requestPskSecretResult(
            final ConnectionId cid,
            final ServerNames serverName,
            final PskPublicInformation identity,
            final String hmacAlgorithm,
            final SecretKey otherSecret,
            final byte[] seed) {

        context.runOnContext((v) -> loadCredentialsForDevice(cid, identity));
        return null;
    }

    @Override
    public void setResultHandler(final PskSecretResultHandler resultHandler) {
        californiumResultHandler = resultHandler;
    }
}
