/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.client.impl;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.client.DeviceConnectionClient;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.DeviceConnectionResult;
import org.eclipse.hono.util.MessageHelper;
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
 * A Vertx-Proton based client for Hono's Device Connection API.
 *
 */
public class DeviceConnectionClientImpl extends AbstractRequestResponseClient<DeviceConnectionResult> implements DeviceConnectionClient {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceConnectionClientImpl.class);

    /**
     * Creates a new client for accessing the Device Connection service.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks()} or
     * {@link #createLinks(Handler, Handler)} only.
     *
     * @param connection The connection to the Device Connection service.
     * @param tenantId The identifier of the tenant for which the client should be created.
     */
    protected DeviceConnectionClientImpl(final HonoConnection connection, final String tenantId) {
        super(connection, tenantId);
    }

    /**
     * Creates a new client for accessing the Device Connection service.
     *
     * @param connection The connection to the Device Connection service.
     * @param tenantId The identifier of the tenant for which the client should be created.
     * @param sender The AMQP link to use for sending requests to the service.
     * @param receiver The AMQP link to use for receiving responses from the service.
     */
    protected DeviceConnectionClientImpl(
            final HonoConnection connection,
            final String tenantId,
            final ProtonSender sender,
            final ProtonReceiver receiver) {

        super(connection, tenantId, sender, receiver);
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending requests to Hono's Device Connection API endpoint.
     * 
     * @param tenantId The tenant to upload data for.
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static final String getTargetAddress(final String tenantId) {
        return String.format("%s/%s", DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT, Objects.requireNonNull(tenantId));
    }

    @Override
    protected final String getName() {

        return DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT;
    }

    @Override
    protected final String createMessageId() {

        return String.format("devcon-client-%s", UUID.randomUUID());
    }

    @Override
    protected final DeviceConnectionResult getResult(
            final int status,
            final String contentType,
            final Buffer payload,
            final CacheDirective cacheDirective,
            final ApplicationProperties applicationProperties) {

        if (payload == null) {
            return DeviceConnectionResult.from(status, null, null, applicationProperties);
        } else {
            try {
                // ignoring given cacheDirective param here - device connection results shall not be cached
                return DeviceConnectionResult.from(status, new JsonObject(payload), CacheDirective.noCacheDirective(), applicationProperties);
            } catch (final DecodeException e) {
                LOG.warn("received malformed payload from Device Connection service", e);
                return DeviceConnectionResult.from(HttpURLConnection.HTTP_INTERNAL_ERROR, null, null, applicationProperties);
            }
        }
    }

    /**
     * Creates a new device connection client for a tenant.
     * 
     * @param con The connection to the server.
     * @param tenantId The tenant to consumer events for.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @return A future indicating the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters other than cache provider is {@code null}.
     */
    public static final Future<DeviceConnectionClient> create(
            final HonoConnection con,
            final String tenantId,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook) {

        LOG.debug("creating new device connection client for [{}]", tenantId);
        final DeviceConnectionClientImpl client = new DeviceConnectionClientImpl(con, tenantId);
        // no response cache being set on client here - device connection results shall not be cached
        return client.createLinks(senderCloseHook, receiverCloseHook)
                .map(ok -> {
                    LOG.debug("successfully created device connection client for [{}]", tenantId);
                    return (DeviceConnectionClient) client;
                }).recover(t -> {
                    LOG.debug("failed to create device connection client for [{}]", tenantId, t);
                    return Future.failedFuture(t);
                });
    }

    private Map<String, Object> createDeviceIdProperties(final String deviceId) {
        final Map<String, Object> properties = new HashMap<>();
        properties.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        return properties;
    }

    /**
     * Invokes the <em>Set Last Known Gateway for Device</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/docs/api/device-connection-api">Device Connection API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     */
    @Override
    public Future<Void> setLastKnownGatewayForDevice(final String deviceId, final String gatewayId, final SpanContext context) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);

        final Map<String, Object> properties = createDeviceIdProperties(deviceId);
        properties.put(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);

        // using FollowsFrom instead of ChildOf reference here as invoking methods usually don't depend and wait on the result of this method
        final Span currentSpan = newFollowingSpan(context, "set last known gateway for device");
        final Future<DeviceConnectionResult> resultTracker = Future.future();
        createAndSendRequest(
                DeviceConnectionConstants.DeviceConnectionAction.SET_LAST_GATEWAY.getSubject(),
                properties,
                null,
                null,
                resultTracker,
                null,
                currentSpan);
        return mapResultAndFinishSpan(resultTracker, result -> {
            switch (result.getStatus()) {
            case HttpURLConnection.HTTP_NO_CONTENT:
                return null;
            default:
                throw StatusCodeMapper.from(result);
            }
        }, currentSpan);
    }

    /**
     * Invokes the <em>Get Last Known Gateway for Device</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/docs/api/device-connection-api">Device Connection API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     */
    @Override
    public Future<JsonObject> getLastKnownGatewayForDevice(final String deviceId, final SpanContext context) {
        Objects.requireNonNull(deviceId);
        final Future<DeviceConnectionResult> resultTracker = Future.future();

        final Span currentSpan = newChildSpan(context, "get last known gateway for device");
        createAndSendRequest(
                DeviceConnectionConstants.DeviceConnectionAction.GET_LAST_GATEWAY.getSubject(),
                createDeviceIdProperties(deviceId),
                null,
                null,
                resultTracker,
                null,
                currentSpan);
        return mapResultAndFinishSpan(resultTracker, result -> {
            switch (result.getStatus()) {
            case HttpURLConnection.HTTP_OK:
                return result.getPayload();
            default:
                throw StatusCodeMapper.from(result);
            }
        }, currentSpan);
    }
}
