/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Bosch Software Innovations GmbH - add caching of results
 */

package org.eclipse.hono.client.impl;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.JwtHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.SpringBasedExpiringValueCache;
import org.eclipse.hono.util.TriTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for Hono's Registration API.
 *
 */
public final class RegistrationClientImpl extends AbstractRequestResponseClient<RegistrationResult> implements RegistrationClient {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationClientImpl.class);

    RegistrationClientImpl(final Context context, final ClientConfigProperties config, final String tenantId) {

        super(context, config, tenantId);
    }

    RegistrationClientImpl(final Context context, final ClientConfigProperties config, final String tenantId,
            final ProtonSender sender, final ProtonReceiver receiver) {

        super(context, config, tenantId, sender, receiver);
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending requests to Hono's Device Registration API endpoint.
     * 
     * @param tenantId The tenant to upload data for.
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static String getTargetAddress(final String tenantId) {
        return String.format("%s/%s", RegistrationConstants.REGISTRATION_ENDPOINT, Objects.requireNonNull(tenantId));
    }

    @Override
    protected String getName() {

        return RegistrationConstants.REGISTRATION_ENDPOINT;
    }

    @Override
    protected String createMessageId() {

        return String.format("reg-client-%s", UUID.randomUUID());
    }

    @Override
    protected RegistrationResult getResult(final int status, final String payload) {

        return RegistrationResult.from(status, payload);
    }

    /**
     * Creates a new registration client for a tenant.
     * 
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param cacheManager A factory for cache instances for registration results. If {@code null}
     *                     the client will not cache any results from the Device Registration service.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant to consumer events for.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters other than cache manager is {@code null}.
     */
    public static void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final CacheManager cacheManager,
            final ProtonConnection con,
            final String tenantId,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook,
            final Handler<AsyncResult<RegistrationClient>> creationHandler) {

        LOG.debug("creating new registration client for [{}]", tenantId);
        final RegistrationClientImpl client = new RegistrationClientImpl(context, clientConfig, tenantId);
        if (cacheManager != null) {
            final Cache cache = cacheManager.getCache(RegistrationClientImpl.getTargetAddress(tenantId));
            client.setResponseCache(new SpringBasedExpiringValueCache<>(cache));
        }
        client.createLinks(con, senderCloseHook, receiverCloseHook).setHandler(s -> {
            if (s.succeeded()) {
                LOG.debug("successfully created registration client for [{}]", tenantId);
                creationHandler.handle(Future.succeededFuture(client));
            } else {
                LOG.debug("failed to create registration client for [{}]", tenantId, s.cause());
                creationHandler.handle(Future.failedFuture(s.cause()));
            }
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
    public Future<Void> register(final String deviceId, final JsonObject data) {

        Objects.requireNonNull(deviceId);

        final Future<RegistrationResult> regResultTracker = Future.future();
        createAndSendRequest(
                RegistrationConstants.ACTION_REGISTER,
                createDeviceIdProperties(deviceId),
                data,
                regResultTracker.completer());
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
    public Future<Void> update(final String deviceId, final JsonObject data) {

        Objects.requireNonNull(deviceId);

        final Future<RegistrationResult> regResultTracker = Future.future();
        createAndSendRequest(
                RegistrationConstants.ACTION_UPDATE,
                createDeviceIdProperties(deviceId),
                data,
                regResultTracker.completer());
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
    public Future<Void> deregister(final String deviceId) {

        Objects.requireNonNull(deviceId);

        final Future<RegistrationResult> regResultTracker = Future.future();
        createAndSendRequest(
                RegistrationConstants.ACTION_DEREGISTER,
                createDeviceIdProperties(deviceId),
                null,
                regResultTracker.completer());
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
    public Future<JsonObject> get(final String deviceId) {

        Objects.requireNonNull(deviceId);
        final Future<RegistrationResult> resultTracker = Future.future();
        createAndSendRequest(
                RegistrationConstants.ACTION_GET,
                createDeviceIdProperties(deviceId),
                null,
                resultTracker.completer());
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
    public Future<JsonObject> assertRegistration(final String deviceId) {
        return assertRegistration(deviceId, null);
    }

    /**
     * Invokes the <em>Assert Device Registration</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/api/Device-Registration-API">Device Registration API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     */
    @Override
    public Future<JsonObject> assertRegistration(final String deviceId, final String gatewayId) {

        Objects.requireNonNull(deviceId);

        final TriTuple<String, String, String> key = TriTuple.of("assert", deviceId, gatewayId);
        return getCachedRegistrationAssertion(key).recover(t -> {
            final Future<RegistrationResult> regResult = Future.future();
            final Map<String, Object> properties = createDeviceIdProperties(deviceId);
            if (gatewayId != null) {
                properties.put(RegistrationConstants.APP_PROPERTY_GATEWAY_ID, gatewayId);
            }
            createAndSendRequest(RegistrationConstants.ACTION_ASSERT, properties, null, regResult.completer());
            return regResult.map(response -> {
                switch(response.getStatus()) {
                case HttpURLConnection.HTTP_OK:
                    if (isCachingEnabled()) {
                        final String token = response.getPayload().getString(RegistrationConstants.FIELD_ASSERTION);
                        putResponseToCache(key, response, JwtHelper.getExpiration(token).toInstant());
                    }
                default:
                    return response;
                }
            });
        }).map(result -> {
            switch(result.getStatus()) {
            case HttpURLConnection.HTTP_OK:
                return result.getPayload();
            default:
                throw StatusCodeMapper.from(result);
            }
        });
    }

    private Future<RegistrationResult> getCachedRegistrationAssertion(final TriTuple<String, String, String> key) {

        final RegistrationResult result = getResponseFromCache(key);
        if (result == null) {
            return Future.failedFuture("cache miss");
        } else {
            return Future.succeededFuture(result);
        }
    }
}
