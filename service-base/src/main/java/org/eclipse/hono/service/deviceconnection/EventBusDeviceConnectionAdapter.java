/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.service.deviceconnection;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.EventBusService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Adapter to bind {@link DeviceConnectionService} to the vertx event bus.
 * <p>
 * This base class provides support for receiving service invocation request messages
 * via vert.x' event bus and routing them to specific methods accepting the
 * query parameters contained in the request message.
 */
public abstract class EventBusDeviceConnectionAdapter extends EventBusService implements Verticle {

    private static final String SPAN_NAME_GET_LAST_GATEWAY = "get last known gateway";
    private static final String SPAN_NAME_SET_LAST_GATEWAY = "set last known gateway";
    private static final String SPAN_NAME_GET_CMD_HANDLING_ADAPTER_INSTANCES = "get command handling adapter instances";
    private static final String SPAN_NAME_SET_CMD_HANDLING_ADAPTER_INSTANCE = "set command handling adapter instance";
    private static final String SPAN_NAME_REMOVE_CMD_HANDLING_ADAPTER_INSTANCE = "remove command handling adapter instance";

    /**
     * The service to forward requests to.
     *
     * @return The service to bind to, must never return {@code null}.
     */
    protected abstract DeviceConnectionService getService();

    @Override
    protected final String getEventBusAddress() {
        return DeviceConnectionConstants.EVENT_BUS_ADDRESS_DEVICE_CONNECTION_IN;
    }

    /**
     * Processes a Device Connection API request message received via the vert.x event bus.
     * <p>
     * This method validates the request payload against the Device Connection API specification
     * before invoking the corresponding {@code DeviceConnectionService} methods.
     * 
     * @param request The request message.
     * @return A future indicating the outcome of the service invocation.
     * @throws NullPointerException If the request message is {@code null}.
     */
    @Override
    public Future<EventBusMessage> processRequest(final EventBusMessage request) {

        Objects.requireNonNull(request);

        switch (DeviceConnectionConstants.DeviceConnectionAction.from(request.getOperation())) {
        case GET_LAST_GATEWAY:
            return processGetLastGatewayRequest(request);
        case SET_LAST_GATEWAY:
            return processSetLastGatewayRequest(request);
        case GET_CMD_HANDLING_ADAPTER_INSTANCES:
            return processGetCmdHandlingAdapterInstances(request);
        case SET_CMD_HANDLING_ADAPTER_INSTANCE:
            return processSetCmdHandlingAdapterInstance(request);
        case REMOVE_CMD_HANDLING_ADAPTER_INSTANCE:
            return processRemoveCmdHandlingAdapterInstance(request);
        default:
            return processCustomOperationMessage(request);
        }
    }

    /**
     * Processes a <em>get last known gateway</em> request message.
     *
     * @param request The request message.
     * @return The response to send to the client via the event bus.
     */
    protected Future<EventBusMessage> processGetLastGatewayRequest(final EventBusMessage request) {
        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                request.getSpanContext(),
                SPAN_NAME_GET_LAST_GATEWAY,
                getClass().getSimpleName()
        ).start();

        final Future<EventBusMessage> resultFuture;
        if (tenantId == null || deviceId == null) {
            TracingHelper.logError(span, "missing tenant and/or device");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            log.debug("getting last known gateway for tenant [{}], device [{}]", tenantId, deviceId);

            TracingHelper.TAG_TENANT_ID.set(span, tenantId);
            TracingHelper.TAG_DEVICE_ID.set(span, deviceId);

            resultFuture = getService().getLastKnownGatewayForDevice(tenantId, deviceId, span)
                    .map(res -> request.getResponse(res.getStatus())
                            .setJsonPayload(res.getPayload())
                            .setCacheDirective(res.getCacheDirective()));
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    /**
     * Processes a <em>set last known gateway</em> request message.
     *
     * @param request The request message.
     * @return The response to send to the client via the event bus.
     */
    protected Future<EventBusMessage> processSetLastGatewayRequest(final EventBusMessage request) {
        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final String gatewayId = request.getGatewayId();

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                request.getSpanContext(),
                SPAN_NAME_SET_LAST_GATEWAY,
                getClass().getSimpleName()
        ).start();

        final Future<EventBusMessage> resultFuture;
        if (tenantId == null || deviceId == null || gatewayId == null) {
            TracingHelper.logError(span, "missing tenant, device and/or gateway");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            log.debug("setting last known gateway for tenant [{}], device [{}] to {}", tenantId, deviceId, gatewayId);

            TracingHelper.TAG_TENANT_ID.set(span, tenantId);
            TracingHelper.TAG_DEVICE_ID.set(span, deviceId);
            TracingHelper.TAG_GATEWAY_ID.set(span, gatewayId);

            resultFuture = getService().setLastKnownGatewayForDevice(tenantId, deviceId, gatewayId, span)
                    .map(res -> request.getResponse(res.getStatus())
                            .setJsonPayload(res.getPayload())
                            .setCacheDirective(res.getCacheDirective()));
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    /**
     * Processes a <em>get command handling protocol adapter instance</em> request message.
     *
     * @param request The request message.
     * @return The response to send to the client via the event bus.
     */
    protected Future<EventBusMessage> processGetCmdHandlingAdapterInstances(final EventBusMessage request) {
        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final JsonObject payload = request.getJsonPayload();

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                request.getSpanContext(),
                SPAN_NAME_GET_CMD_HANDLING_ADAPTER_INSTANCES,
                getClass().getSimpleName()
        ).start();

        final Future<EventBusMessage> resultFuture;
        if (tenantId == null || deviceId == null || payload == null) {
            TracingHelper.logError(span, "missing tenant, device and/or payload");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            TracingHelper.TAG_TENANT_ID.set(span, tenantId);
            TracingHelper.TAG_DEVICE_ID.set(span, deviceId);

            final Object gatewaysValue = payload.getValue(DeviceConnectionConstants.FIELD_GATEWAY_IDS);
            if (!(gatewaysValue instanceof JsonArray)) {
                TracingHelper.logError(span, "payload JSON is missing valid '" + DeviceConnectionConstants.FIELD_GATEWAY_IDS + "' field value");
                resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
            } else {
                log.debug("getting command handling adapter instances for tenant [{}], device [{}]", tenantId, deviceId);

                @SuppressWarnings("unchecked")
                final List<String> list = ((JsonArray) gatewaysValue).getList();
                resultFuture = getService().getCommandHandlingAdapterInstances(tenantId, deviceId, list, span)
                        .map(res -> request.getResponse(res.getStatus())
                                    .setJsonPayload(res.getPayload())
                                    .setCacheDirective(res.getCacheDirective())
                        );
            }
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    /**
     * Processes a <em>set protocol adapter instance for command handler</em> request message.
     *
     * @param request The request message.
     * @return The response to send to the client via the event bus.
     */
    protected Future<EventBusMessage> processSetCmdHandlingAdapterInstance(final EventBusMessage request) {
        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final String adapterInstanceId = request.getProperty(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID);

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                request.getSpanContext(),
                SPAN_NAME_SET_CMD_HANDLING_ADAPTER_INSTANCE,
                getClass().getSimpleName()
        ).start();

        final Future<EventBusMessage> resultFuture;
        if (tenantId == null || deviceId == null || adapterInstanceId == null) {
            TracingHelper.logError(span, "missing tenant, device and/or adapter instance id");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            TracingHelper.TAG_TENANT_ID.set(span, tenantId);
            TracingHelper.TAG_DEVICE_ID.set(span, deviceId);
            span.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
            log.debug("setting command handling adapter instance for tenant [{}], device [{}] to {}", tenantId, deviceId, adapterInstanceId);

            resultFuture = getService().setCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, span)
                    .map(res -> request.getResponse(res.getStatus())
                        .setJsonPayload(res.getPayload())
                        .setCacheDirective(res.getCacheDirective())
            );
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    /**
     * Processes a <em>remove command handling protocol adapter instance</em> request message.
     *
     * @param request The request message.
     * @return The response to send to the client via the event bus.
     */
    protected Future<EventBusMessage> processRemoveCmdHandlingAdapterInstance(final EventBusMessage request) {
        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final String adapterInstanceId = request.getProperty(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID);

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                request.getSpanContext(),
                SPAN_NAME_REMOVE_CMD_HANDLING_ADAPTER_INSTANCE,
                getClass().getSimpleName()
        ).start();

        final Future<EventBusMessage> resultFuture;
        if (tenantId == null || deviceId == null || adapterInstanceId == null) {
            TracingHelper.logError(span, "missing tenant, device and/or adapter instance id");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            TracingHelper.TAG_TENANT_ID.set(span, tenantId);
            TracingHelper.TAG_DEVICE_ID.set(span, deviceId);
            span.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
            log.debug("removing command handling adapter instance for tenant [{}], device [{}] with value {}", tenantId, deviceId, adapterInstanceId);

            resultFuture = getService().removeCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, span)
                    .map(res -> request.getResponse(res.getStatus())
                        .setJsonPayload(res.getPayload())
                        .setCacheDirective(res.getCacheDirective())
            );
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    /**
     * Processes a request for a non-standard operation.
     * <p>
     * Subclasses should override this method in order to support additional, custom
     * operations that are not defined by Hono's Device Connection API.
     * <p>
     * This default implementation simply returns a future that is failed with a
     * {@link ClientErrorException} with an error code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<EventBusMessage> processCustomOperationMessage(final EventBusMessage request) {
        log.debug("invalid operation in request message [{}]", request.getOperation());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }

}
