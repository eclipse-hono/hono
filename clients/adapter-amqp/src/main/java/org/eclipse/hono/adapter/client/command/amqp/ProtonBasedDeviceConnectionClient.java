/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.client.command.amqp;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.adapter.client.amqp.AbstractRequestResponseServiceClient;
import org.eclipse.hono.adapter.client.amqp.RequestResponseClient;
import org.eclipse.hono.adapter.client.command.DeviceConnectionClient;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler.Factory;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.DeviceConnectionResult;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


/**
 * A vertx-proton based client for accessing Hono's <em>Device Connection</em> API.
 *
 */
public class ProtonBasedDeviceConnectionClient extends AbstractRequestResponseServiceClient<JsonObject, DeviceConnectionResult>
        implements DeviceConnectionClient {

    private static final Logger LOG = LoggerFactory.getLogger(ProtonBasedDeviceConnectionClient.class);

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the Device Connection service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtonBasedDeviceConnectionClient(
            final HonoConnection connection,
            final Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {

        super(connection, samplerFactory, adapterConfig, new CachingClientFactory<>(
                connection.getVertx(), RequestResponseClient::isOpen), null);
        connection.getVertx().eventBus().consumer(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getKey(final String tenantId) {
        return String.format("%s-%s", DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT, tenantId);
    }

    private Future<RequestResponseClient<DeviceConnectionResult>> getOrCreateClient(final String tenantId) {

        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    clientFactory.getOrCreateClient(
                            getKey(tenantId),
                            () -> {
                                return RequestResponseClient.forEndpoint(
                                        connection,
                                        DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT,
                                        tenantId,
                                        samplerFactory.create(DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT),
                                        this::removeClient,
                                        this::removeClient);
                            },
                            result);
                }));
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
     * {@inheritDoc}
     */
    @Override
    public Future<JsonObject> getLastKnownGatewayForDevice(
            final String tenant,
            final String deviceId,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);

        final Span currentSpan = newChildSpan(context, "get last known gateway for device");
        TracingHelper.setDeviceTags(currentSpan, tenant, deviceId);

        final Future<DeviceConnectionResult> resultTracker = getOrCreateClient(tenant)
                .compose(client -> client.createAndSendRequest(
                    DeviceConnectionConstants.DeviceConnectionAction.GET_LAST_GATEWAY.getSubject(),
                    createDeviceIdProperties(deviceId),
                    null,
                    null,
                    this::getRequestResponseResult,
                    currentSpan));
        return mapResultAndFinishSpan(
                resultTracker,
                result -> {
                    switch (result.getStatus()) {
                    case HttpURLConnection.HTTP_OK:
                        return result.getPayload();
                    default:
                        throw StatusCodeMapper.from(result);
                    }
                },
                currentSpan);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> setLastKnownGatewayForDevice(
            final String tenant,
            final String deviceId,
            final String gatewayId,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);

        final Map<String, Object> properties = createDeviceIdProperties(deviceId);
        properties.put(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);

        // using FollowsFrom instead of ChildOf reference here as invoking methods usually don't depend and wait on the result of this method
        final Span currentSpan = newFollowingSpan(context, "set last known gateway for device");
        TracingHelper.setDeviceTags(currentSpan, tenant, deviceId);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);

        final Future<DeviceConnectionResult> resultTracker = getOrCreateClient(tenant)
                .compose(client -> client.createAndSendRequest(
                        DeviceConnectionConstants.DeviceConnectionAction.SET_LAST_GATEWAY.getSubject(),
                        properties,
                        null,
                        null,
                        this::getRequestResponseResult,
                        currentSpan));
        return mapResultAndFinishSpan(
                resultTracker,
                result -> {
                    switch (result.getStatus()) {
                    case HttpURLConnection.HTTP_NO_CONTENT:
                        return null;
                    default:
                        throw StatusCodeMapper.from(result);
                    }
                },
                currentSpan).mapEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> setCommandHandlingAdapterInstance(
            final String tenantId,
            final String deviceId,
            final String adapterInstanceId,
            final Duration lifespan,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);

        final int lifespanSeconds = lifespan != null && lifespan.getSeconds() <= Integer.MAX_VALUE ? (int) lifespan.getSeconds() : -1;
        final Map<String, Object> properties = createDeviceIdProperties(deviceId);
        properties.put(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        properties.put(MessageHelper.APP_PROPERTY_LIFESPAN, lifespanSeconds);

        final Span currentSpan = newChildSpan(context, "set command handling adapter instance");
        TracingHelper.setDeviceTags(currentSpan, tenantId, deviceId);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_LIFESPAN, lifespanSeconds);

        final Future<DeviceConnectionResult> resultTracker = getOrCreateClient(tenantId)
                .compose(client -> client.createAndSendRequest(
                        DeviceConnectionConstants.DeviceConnectionAction.SET_CMD_HANDLING_ADAPTER_INSTANCE.getSubject(),
                        properties,
                        null,
                        null,
                        this::getRequestResponseResult,
                        currentSpan));
        return mapResultAndFinishSpan(resultTracker, result -> {
            switch (result.getStatus()) {
                case HttpURLConnection.HTTP_NO_CONTENT:
                    return null;
                default:
                    throw StatusCodeMapper.from(result);
            }
        }, currentSpan).mapEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> removeCommandHandlingAdapterInstance(
            final String tenantId,
            final String deviceId,
            final String adapterInstanceId,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);

        final Map<String, Object> properties = createDeviceIdProperties(deviceId);
        properties.put(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);

        final Span currentSpan = newChildSpan(context, "remove command handling adapter instance");
        TracingHelper.setDeviceTags(currentSpan, tenantId, deviceId);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);

        return getOrCreateClient(tenantId)
                .compose(client -> client.createAndSendRequest(
                        DeviceConnectionConstants.DeviceConnectionAction.REMOVE_CMD_HANDLING_ADAPTER_INSTANCE.getSubject(),
                        properties,
                        null,
                        null,
                        this::getRequestResponseResult,
                        currentSpan))
                // not using mapResultAndFinishSpan() in order to skip logging PRECON_FAILED result as error
                // (may have been trying to remove an expired entry)
                .recover(t -> {
                    Tags.HTTP_STATUS.set(currentSpan, ServiceInvocationException.extractStatusCode(t));
                    TracingHelper.logError(currentSpan, t);
                    return Future.failedFuture(t);
                })
                .map(resultValue -> {
                    Tags.HTTP_STATUS.set(currentSpan, resultValue.getStatus());
                    if (resultValue.isError() && resultValue.getStatus() != HttpURLConnection.HTTP_PRECON_FAILED) {
                        Tags.ERROR.set(currentSpan, Boolean.TRUE);
                    }

                    switch (resultValue.getStatus()) {
                    case HttpURLConnection.HTTP_NO_CONTENT:
                        return null;
                    default:
                        throw StatusCodeMapper.from(resultValue);
                    }
                })
                .onComplete(v -> currentSpan.finish())
                .mapEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<JsonObject> getCommandHandlingAdapterInstances(
            final String tenant,
            final String deviceId,
            final List<String> viaGateways,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(viaGateways);

        final Map<String, Object> properties = createDeviceIdProperties(deviceId);
        final JsonObject payload = new JsonObject();
        payload.put(DeviceConnectionConstants.FIELD_GATEWAY_IDS, new JsonArray(viaGateways));

        final Span currentSpan = newChildSpan(context, "get command handling adapter instances");
        TracingHelper.setDeviceTags(currentSpan, tenant, deviceId);

        final Future<DeviceConnectionResult> resultTracker = getOrCreateClient(tenant)
                .compose(client -> client.createAndSendRequest(
                        DeviceConnectionConstants.DeviceConnectionAction.GET_CMD_HANDLING_ADAPTER_INSTANCES.getSubject(),
                        properties,
                        payload.toBuffer(),
                        RequestResponseApiConstants.CONTENT_TYPE_APPLICATION_JSON,
                        this::getRequestResponseResult,
                        currentSpan));
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
