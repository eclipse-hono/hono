/**
 * Copyright (c) 2019, 2023 Contributors to the Eclipse Foundation
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
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.SecretKey;

import org.eclipse.californium.elements.auth.AdditionalInfo;
import org.eclipse.californium.scandium.dtls.ConnectionId;
import org.eclipse.californium.scandium.dtls.HandshakeResultHandler;
import org.eclipse.californium.scandium.dtls.PskPublicInformation;
import org.eclipse.californium.scandium.dtls.PskSecretResult;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedPskStore;
import org.eclipse.californium.scandium.util.SecretUtil;
import org.eclipse.californium.scandium.util.ServerNames;
import org.eclipse.hono.adapter.auth.device.DeviceCredentials;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;


/**
 * A PSK store that looks up keys using Hono's Credentials service.
 *
 */
public class DeviceRegistryBasedPskStore implements AdvancedPskStore {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistryBasedPskStore.class);

    private final CoapProtocolAdapter adapter;
    private final Tracer tracer;

    private volatile HandshakeResultHandler californiumResultHandler;

    /**
     * Creates a new resolver.
     *
     * @param adapter The protocol adapter to use for accessing Hono services.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public DeviceRegistryBasedPskStore(
            final CoapProtocolAdapter adapter,
            final Tracer tracer) {

        this.adapter = Objects.requireNonNull(adapter);
        this.tracer = Objects.requireNonNull(tracer);
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

    /**
     * Load credentials for an identity used by a device in a PSK based DTLS handshake.
     *
     * @param cid the connection id to report the result.
     * @param identity the psk identity of the device.
     */
    private void loadCredentialsForDevice(final ConnectionId cid, final PskPublicInformation identity) {
        final String publicInfo = identity.getPublicInfoAsString();
        LOG.debug("getting PSK secret for identity [{}]", publicInfo);

        final Span span = tracer.buildSpan("look up pre-shared key")
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(Tags.COMPONENT.getKey(), adapter.getTypeName())
                .start();

        final PreSharedKeyDeviceIdentity handshakeIdentity = getHandshakeIdentity(publicInfo, span);
        if (handshakeIdentity == null) {
            TracingHelper.logError(span, "could not determine auth-id from PSK identity");
            span.finish();
            return;
        }

        TracingHelper.TAG_TENANT_ID.set(span, handshakeIdentity.getTenantId());
        TracingHelper.TAG_AUTH_ID.set(span, handshakeIdentity.getAuthId());

        applyTraceSamplingPriority(handshakeIdentity, span)
                .compose(v -> adapter.getCredentialsClient().get(
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
                        // set AdditionalInfo as customArgument here
                        final AdditionalInfo info = DeviceInfoSupplier.createDeviceInfo(
                                new DeviceUser(handshakeIdentity.getTenantId(), credentials.getDeviceId()),
                                handshakeIdentity.getAuthId());
                        return new PskSecretResult(cid, identity, key, info);
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

    private Future<Void> applyTraceSamplingPriority(final DeviceCredentials deviceCredentials, final Span span) {
        return adapter.getTenantClient().get(deviceCredentials.getTenantId(), span.context())
                .map(tenantObject -> {
                    TracingHelper.setDeviceTags(span, tenantObject.getTenantId(), null, deviceCredentials.getAuthId());
                    TenantTraceSamplingHelper.applyTraceSamplingPriority(tenantObject, deviceCredentials.getAuthId(), span);
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
        return PreSharedKeyDeviceIdentity.create(identity, adapter.getConfig().getIdSplitRegex(), span);
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
            final byte[] seed,
            final boolean useExtendedMasterSecret) {

        adapter.runOnContext((v) -> loadCredentialsForDevice(cid, identity));
        return null;
    }

    @Override
    public void setResultHandler(final HandshakeResultHandler resultHandler) {
        californiumResultHandler = resultHandler;
    }
}
