/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.service.deviceconnection;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.AbstractDelegatingRequestResponseEndpoint;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * An {@code AmqpEndpoint} for managing device connection information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/docs/api/device-connection/">Device
 * Connection API</a>. It receives AMQP 1.0 messages representing requests and sends them to an address on the vertx
 * event bus for processing. The outcome is then returned to the peer in a response message.
 *
 * @param <S> The type of service this endpoint delegates to.
 */
public class DelegatingDeviceConnectionAmqpEndpoint<S extends DeviceConnectionService> extends AbstractDelegatingRequestResponseEndpoint<S, ServiceConfigProperties> {

    private static final String SPAN_NAME_GET_LAST_GATEWAY = "get last known gateway";
    private static final String SPAN_NAME_SET_LAST_GATEWAY = "set last known gateway";
    private static final String SPAN_NAME_GET_CMD_HANDLING_ADAPTER_INSTANCES = "get command handling adapter instances";
    private static final String SPAN_NAME_SET_CMD_HANDLING_ADAPTER_INSTANCE = "set command handling adapter instance";
    private static final String SPAN_NAME_REMOVE_CMD_HANDLING_ADAPTER_INSTANCE = "remove command handling adapter instance";

    /**
     * Creates an endpoint for a service instance.
     *
     * @param vertx The vert.x instance to use.
     * @param service The service to delegate to.
     * @throws NullPointerException if any of the parameters are {@code null};
     */
    public DelegatingDeviceConnectionAmqpEndpoint(final Vertx vertx, final S service) {
        super(vertx, service);
    }

    @Override
    protected Future<Message> handleRequestMessage(final Message requestMessage, final ResourceIdentifier targetAddress,
            final SpanContext spanContext) {
        Objects.requireNonNull(requestMessage);

        switch (DeviceConnectionConstants.DeviceConnectionAction.from(requestMessage.getSubject())) {
        case GET_LAST_GATEWAY:
            return processGetLastGatewayRequest(requestMessage, targetAddress, spanContext);
        case SET_LAST_GATEWAY:
            return processSetLastGatewayRequest(requestMessage, targetAddress, spanContext);
        case GET_CMD_HANDLING_ADAPTER_INSTANCES:
            return processGetCmdHandlingAdapterInstances(requestMessage, targetAddress, spanContext);
        case SET_CMD_HANDLING_ADAPTER_INSTANCE:
            return processSetCmdHandlingAdapterInstance(requestMessage, targetAddress, spanContext);
        case REMOVE_CMD_HANDLING_ADAPTER_INSTANCE:
            return processRemoveCmdHandlingAdapterInstance(requestMessage, targetAddress, spanContext);
        default:
            return processCustomOperationMessage(requestMessage, spanContext);
        }
    }

    /**
     * Processes a <em>get last known gateway</em> request message.
     *
     * @param request The request message.
     * @param targetAddress The address the message is sent to.
     * @param spanContext The span context representing the request to be processed.
     * @return The response to send to the client via the event bus.
     */
    protected Future<Message> processGetLastGatewayRequest(final Message request,
            final ResourceIdentifier targetAddress, final SpanContext spanContext) {
        final String tenantId = targetAddress.getTenantId();
        final String deviceId = MessageHelper.getDeviceId(request);

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                spanContext,
                SPAN_NAME_GET_LAST_GATEWAY,
                getClass().getSimpleName()
        ).start();

        final Future<Message> resultFuture;
        if (tenantId == null || deviceId == null) {
            TracingHelper.logError(span, "missing tenant and/or device");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            log.debug("getting last known gateway for tenant [{}], device [{}]", tenantId, deviceId);

            TracingHelper.TAG_TENANT_ID.set(span, tenantId);
            TracingHelper.TAG_DEVICE_ID.set(span, deviceId);

            resultFuture = getService().getLastKnownGatewayForDevice(tenantId, deviceId, span)
                    .map(res -> DeviceConnectionConstants.getAmqpReply(
                            DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT,
                            tenantId,
                            request,
                            res)
                    );
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    /**
     * Processes a <em>set last known gateway</em> request message.
     *
     * @param request The request message.
     * @param targetAddress The address the message is sent to.
     * @param spanContext The span context representing the request to be processed.
     * @return The response to send to the client via the event bus.
     */
    protected Future<Message> processSetLastGatewayRequest(final Message request,
            final ResourceIdentifier targetAddress, final SpanContext spanContext) {
        final String tenantId = targetAddress.getTenantId();
        final String deviceId = MessageHelper.getDeviceId(request);
        final String gatewayId = MessageHelper.getGatewayId(request);

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                spanContext,
                SPAN_NAME_SET_LAST_GATEWAY,
                getClass().getSimpleName()
        ).start();

        final Future<Message> resultFuture;
        if (tenantId == null || deviceId == null || gatewayId == null) {
            TracingHelper.logError(span, "missing tenant, device and/or gateway");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            log.debug("setting last known gateway for tenant [{}], device [{}] to {}", tenantId, deviceId, gatewayId);

            TracingHelper.TAG_TENANT_ID.set(span, tenantId);
            TracingHelper.TAG_DEVICE_ID.set(span, deviceId);
            TracingHelper.TAG_GATEWAY_ID.set(span, gatewayId);

            resultFuture = getService().setLastKnownGatewayForDevice(tenantId, deviceId, gatewayId, span)
                    .map(res -> DeviceConnectionConstants.getAmqpReply(
                            DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT,
                            tenantId,
                            request,
                            res)
                    );
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    /**
     * Processes a <em>get command handling protocol adapter instance</em> request message.
     *
     * @param request The request message.
     * @param targetAddress The address the message is sent to.
     * @param spanContext The span context representing the request to be processed.
     * @return The response to send to the client via the event bus.
     */
    protected Future<Message> processGetCmdHandlingAdapterInstances(final Message request,
            final ResourceIdentifier targetAddress, final SpanContext spanContext) {
        final String tenantId = targetAddress.getTenantId();
        final String deviceId = MessageHelper.getDeviceId(request);

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                spanContext,
                SPAN_NAME_GET_CMD_HANDLING_ADAPTER_INSTANCES,
                getClass().getSimpleName()
        ).start();

        final JsonObject payload;
        try {
            payload = MessageHelper.getJsonPayload(request);
        } catch (DecodeException e) {
            logger.debug("failed to decode AMQP request message", e);
            return finishSpanOnFutureCompletion(span, Future.failedFuture(
                    new ClientErrorException(
                            HttpURLConnection.HTTP_BAD_REQUEST,
                            "request message body contains malformed JSON")));
        }

        final Future<Message> resultFuture;
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
                        .map(res -> DeviceConnectionConstants.getAmqpReply(
                                DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT,
                                tenantId,
                                request,
                                res)
                        );
            }
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    /**
     * Processes a <em>set protocol adapter instance for command handler</em> request message.
     *
     * @param request The request message.
     * @param targetAddress The address the message is sent to.
     * @param spanContext The span context representing the request to be processed.
     * @return The response to send to the client via the event bus.
     */
    protected Future<Message> processSetCmdHandlingAdapterInstance(final Message request,
            final ResourceIdentifier targetAddress, final SpanContext spanContext) {
        final String tenantId = targetAddress.getTenantId();
        final String deviceId = MessageHelper.getDeviceId(request);
        final String adapterInstanceId = MessageHelper.getApplicationProperty(request.getApplicationProperties(), MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, String.class);

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                spanContext,
                SPAN_NAME_SET_CMD_HANDLING_ADAPTER_INSTANCE,
                getClass().getSimpleName()
        ).start();

        final Future<Message> resultFuture;
        if (tenantId == null || deviceId == null || adapterInstanceId == null) {
            TracingHelper.logError(span, "missing tenant, device and/or adapter instance id");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            TracingHelper.TAG_TENANT_ID.set(span, tenantId);
            TracingHelper.TAG_DEVICE_ID.set(span, deviceId);
            span.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
            log.debug("setting command handling adapter instance for tenant [{}], device [{}] to {}", tenantId, deviceId, adapterInstanceId);

            resultFuture = getService().setCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, span)
                    .map(res -> DeviceConnectionConstants.getAmqpReply(
                            DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT,
                            tenantId,
                            request,
                            res)
                    );
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    /**
     * Processes a <em>remove command handling protocol adapter instance</em> request message.
     *
     * @param request The request message.
     * @param targetAddress The address the message is sent to.
     * @param spanContext The span context representing the request to be processed.
     * @return The response to send to the client via the event bus.
     */
    protected Future<Message> processRemoveCmdHandlingAdapterInstance(final Message request,
            final ResourceIdentifier targetAddress, final SpanContext spanContext) {
        final String tenantId = targetAddress.getTenantId();
        final String deviceId = MessageHelper.getDeviceId(request);
        final String adapterInstanceId = MessageHelper.getApplicationProperty(request.getApplicationProperties(), MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, String.class);

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                spanContext,
                SPAN_NAME_REMOVE_CMD_HANDLING_ADAPTER_INSTANCE,
                getClass().getSimpleName()
        ).start();

        final Future<Message> resultFuture;
        if (tenantId == null || deviceId == null || adapterInstanceId == null) {
            TracingHelper.logError(span, "missing tenant, device and/or adapter instance id");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            TracingHelper.TAG_TENANT_ID.set(span, tenantId);
            TracingHelper.TAG_DEVICE_ID.set(span, deviceId);
            span.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
            log.debug("removing command handling adapter instance for tenant [{}], device [{}] with value {}", tenantId, deviceId, adapterInstanceId);

            resultFuture = getService().removeCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, span)
                    .map(res -> DeviceConnectionConstants.getAmqpReply(
                            DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT,
                            tenantId,
                            request,
                            res)
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
     * @param spanContext The span context representing the request to be processed.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<Message> processCustomOperationMessage(final Message request, final SpanContext spanContext) {
        log.debug("invalid operation in request message [{}]", request.getSubject());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }


    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier linkTarget, final Message msg) {
        return DeviceConnectionMessageFilter.verify(linkTarget, msg);
    }

    @Override
    public final String getName() {
        return DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT;
    }
}
