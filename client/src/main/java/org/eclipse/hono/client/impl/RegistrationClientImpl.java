/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.client.impl;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.TriTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for Hono's Registration API.
 *
 */
public class RegistrationClientImpl extends AbstractRequestResponseClient<RegistrationResult> implements RegistrationClient {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationClientImpl.class);

    /**
     * Creates a new client for accessing the Device Registration service.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks()} or
     * {@link #createLinks(Handler, Handler)} only.
     * 
     * @param connection The connection to Hono.
     * @param tenantId The identifier of the tenant for which the client should be created.
     */
    protected RegistrationClientImpl(final HonoConnection connection, final String tenantId) {
        super(connection, tenantId);
    }

    /**
     * Creates a new client for accessing the Device Registration service.
     * 
     * @param connection The connection to Hono.
     * @param tenantId The identifier of the tenant for which the client should be created.
     * @param sender The AMQP link to use for sending requests to the service.
     * @param receiver The AMQP link to use for receiving responses from the service.
     */
    protected RegistrationClientImpl(
            final HonoConnection connection,
            final String tenantId,
            final ProtonSender sender,
            final ProtonReceiver receiver) {

        super(connection, tenantId, sender, receiver);
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending requests to Hono's Device Registration API endpoint.
     * 
     * @param tenantId The tenant to upload data for.
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static final String getTargetAddress(final String tenantId) {
        return String.format("%s/%s", RegistrationConstants.REGISTRATION_ENDPOINT, Objects.requireNonNull(tenantId));
    }

    @Override
    protected final String getName() {

        return RegistrationConstants.REGISTRATION_ENDPOINT;
    }

    @Override
    protected final String createMessageId() {

        return String.format("reg-client-%s", UUID.randomUUID());
    }

    @Override
    protected final RegistrationResult getResult(
            final int status,
            final String contentType,
            final Buffer payload,
            final CacheDirective cacheDirective,
            final ApplicationProperties applicationProperties) {

        if (payload == null) {
            return RegistrationResult.from(status, null, null, applicationProperties);
        } else {
            try {
                return RegistrationResult.from(status, new JsonObject(payload), cacheDirective, applicationProperties);
            } catch (final DecodeException e) {
                LOG.warn("received malformed payload from Device Registration service", e);
                return RegistrationResult.from(HttpURLConnection.HTTP_INTERNAL_ERROR, null, null, applicationProperties);
            }
        }
    }

    /**
     * Creates a new registration client for a tenant.
     * 
     * @param cacheProvider A factory for cache instances for registration results. If {@code null}
     *                     the client will not cache any results from the Device Registration service.
     * @param con The connection to the server.
     * @param tenantId The tenant to consumer events for.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @return A future indicating the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters other than cache provider is {@code null}.
     */
    public static final Future<RegistrationClient> create(
            final CacheProvider cacheProvider,
            final HonoConnection con,
            final String tenantId,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook) {

        LOG.debug("creating new registration client for [{}]", tenantId);
        final RegistrationClientImpl client = new RegistrationClientImpl(con, tenantId);
        if (cacheProvider != null) {
            client.setResponseCache(cacheProvider.getCache(RegistrationClientImpl.getTargetAddress(tenantId)));
        }
        return client.createLinks(senderCloseHook, receiverCloseHook)
                .map(ok -> {
                    LOG.debug("successfully created registration client for [{}]", tenantId);
                    return (RegistrationClient) client;
                }).recover(t -> {
                    LOG.debug("failed to create registration client for [{}]", tenantId, t);
                    return Future.failedFuture(t);
                });
    }

    private Map<String, Object> createDeviceIdProperties(final String deviceId) {
        final Map<String, Object> properties = new HashMap<>();
        properties.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        return properties;
    }

    /**
     * Invokes the <em>Register Device</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/api/Device-Registration-API">Device Registration API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     */
    @Override
    public final Future<Void> register(final String deviceId, final JsonObject data) {

        Objects.requireNonNull(deviceId);

        final Future<RegistrationResult> regResultTracker = Future.future();
        createAndSendRequest(
                RegistrationConstants.ACTION_REGISTER,
                createDeviceIdProperties(deviceId),
                data != null ? data.toBuffer() : null,
                regResultTracker);
        return regResultTracker.map(response -> {
            switch(response.getStatus()) {
            case HttpURLConnection.HTTP_CREATED:
                return null;
            default:
                throw StatusCodeMapper.from(response);
            }
        });
    }

    /**
     * Invokes the <em>Update Device Registration</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/api/Device-Registration-API">Device Registration API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     */
    @Override
    public final Future<Void> update(final String deviceId, final JsonObject data) {

        Objects.requireNonNull(deviceId);

        final Future<RegistrationResult> regResultTracker = Future.future();
        createAndSendRequest(
                RegistrationConstants.ACTION_UPDATE,
                createDeviceIdProperties(deviceId),
                data != null ? data.toBuffer() : null,
                regResultTracker);
        return regResultTracker.map(response -> {
            switch(response.getStatus()) {
            case HttpURLConnection.HTTP_NO_CONTENT:
                return null;
            default:
                throw StatusCodeMapper.from(response);
            }
        });
    }

    /**
     * Invokes the <em>Deregister Device</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/api/Device-Registration-API">Device Registration API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     */
    @Override
    public final Future<Void> deregister(final String deviceId) {

        Objects.requireNonNull(deviceId);

        final Future<RegistrationResult> regResultTracker = Future.future();
        createAndSendRequest(
                RegistrationConstants.ACTION_DEREGISTER,
                createDeviceIdProperties(deviceId),
                null,
                regResultTracker);
        return regResultTracker.map(response -> {
            switch(response.getStatus()) {
            case HttpURLConnection.HTTP_NO_CONTENT:
                return null;
            default:
                throw StatusCodeMapper.from(response);
            }
        });
    }

    /**
     * Invokes the <em>Get Registration Information</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/api/Device-Registration-API">Device Registration API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     */
    @Override
    public final Future<JsonObject> get(final String deviceId) {
        return get(deviceId, null);
    }

    /**
     * Invokes the <em>Get Registration Information</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/api/Device-Registration-API">Device Registration API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     */
    @Override
    public final Future<JsonObject> get(final String deviceId, final SpanContext context) {

        Objects.requireNonNull(deviceId);
        final Future<RegistrationResult> resultTracker = Future.future();

        createAndSendRequest(
                RegistrationConstants.ACTION_GET,
                createDeviceIdProperties(deviceId),
                null,
                resultTracker,
                null,
                context);

        return resultTracker.map(regResult -> {
            switch (regResult.getStatus()) {
            case HttpURLConnection.HTTP_OK:
                return regResult.getPayload();
            default:
                throw StatusCodeMapper.from(regResult);
            }
        });
    }

    /**
     * Invokes the <em>Assert Device Registration</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/api/Device-Registration-API">Device Registration API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     * <p>
     * This method delegates to {@link #assertRegistration(String, String)} with {@code null}
     * as the <em>gatewayId</em>.
     */
    @Override
    public final Future<JsonObject> assertRegistration(final String deviceId) {
        return assertRegistration(deviceId, null);
    }

    /**
     * Invokes the <em>Assert Device Registration</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/api/Device-Registration-API">Device Registration API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     */
    @Override
    public final Future<JsonObject> assertRegistration(final String deviceId, final String gatewayId) {

        return assertRegistration(deviceId, gatewayId, null);
    }

    /**
     * Invokes the <em>Assert Device Registration</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/api/Device-Registration-API">Device Registration API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     * <p>
     * This method delegates to {@link #assertRegistration(String, String)} with {@code null}
     * as the <em>gatewayId</em>.
     */
    @Override
    public final Future<JsonObject> assertRegistration(
            final String deviceId,
            final String gatewayId,
            final SpanContext parent) {

        Objects.requireNonNull(deviceId);

        final TriTuple<String, String, String> key = TriTuple.of(RegistrationConstants.ACTION_ASSERT, deviceId, gatewayId);
        final Span span = newChildSpan(parent, "assert Device Registration");
        span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, getTenantId());
        span.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        span.setTag(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);

        final AtomicBoolean cacheHit = new AtomicBoolean(true);

        return getResponseFromCache(key).recover(t -> {
            cacheHit.set(false);
            final Future<RegistrationResult> regResult = Future.future();
            final Map<String, Object> properties = createDeviceIdProperties(deviceId);
            if (gatewayId != null) {
                properties.put(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);
            }
            createAndSendRequest(
                    RegistrationConstants.ACTION_ASSERT,
                    properties,
                    null,
                    RegistrationConstants.CONTENT_TYPE_APPLICATION_JSON,
                    regResult,
                    key,
                    span);
            return regResult;
        }).recover(t -> {
            TracingHelper.logError(span, t);
            span.finish();
            return Future.failedFuture(t);
        }).map(result -> {
            TracingHelper.TAG_CACHE_HIT.set(span, cacheHit.get());
            if (result.isError()) {
                TracingHelper.logError(span, "error code: " + result.getStatus());
            }
            span.finish();
            switch(result.getStatus()) {
            case HttpURLConnection.HTTP_OK:
                return result.getPayload();
            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new ClientErrorException(result.getStatus(), "device unknown or disabled");
            case HttpURLConnection.HTTP_FORBIDDEN:
                throw new ClientErrorException(
                        result.getStatus(),
                        "gateway unknown, disabled or not authorized to act on behalf of device");
            default:
                throw StatusCodeMapper.from(result);
            }
        });
    }

    /**
     * Invokes the <em>Set Last-Used Gateway</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/api/Device-Registration-API">Device Registration API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     */
    @Override
    public Future<Void> setLastUsedGateway(final String deviceId, final String gatewayId, final SpanContext context) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);

        final Map<String, Object> properties = createDeviceIdProperties(deviceId);
        properties.put(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);

        final Future<RegistrationResult> resultTracker = Future.future();
        createAndSendRequest(
                RegistrationConstants.ACTION_SET_LAST_USED_GATEWAY,
                properties,
                null,
                resultTracker,
                null,
                context);
        return resultTracker.map(result -> {
            switch (result.getStatus()) {
            case HttpURLConnection.HTTP_NO_CONTENT:
                return null;
            default:
                throw StatusCodeMapper.from(result);
            }
        });
    }

    /**
     * Invokes the <em>Get Last-Used Gateway</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/api/Device-Registration-API">Device Registration API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     */
    @Override
    public Future<JsonObject> getLastUsedGateway(final String deviceId, final SpanContext context) {
        Objects.requireNonNull(deviceId);
        final Future<RegistrationResult> resultTracker = Future.future();

        createAndSendRequest(
                RegistrationConstants.ACTION_GET_LAST_USED_GATEWAY,
                createDeviceIdProperties(deviceId),
                null,
                resultTracker,
                null,
                context);

        return resultTracker.map(regResult -> {
            switch (regResult.getStatus()) {
            case HttpURLConnection.HTTP_OK:
                return regResult.getPayload();
            default:
                throw StatusCodeMapper.from(regResult);
            }
        });
    }
}
