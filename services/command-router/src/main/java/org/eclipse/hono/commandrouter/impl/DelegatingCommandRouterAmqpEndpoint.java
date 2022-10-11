/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.commandrouter.impl;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.commandrouter.CommandRouterResult;
import org.eclipse.hono.commandrouter.CommandRouterService;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.AbstractDelegatingRequestResponseEndpoint;
import org.eclipse.hono.service.amqp.AbstractRequestResponseEndpoint;
import org.eclipse.hono.service.amqp.GenericRequestMessageFilter;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.CommandRouterConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * An {@code AmqpEndpoint} for managing command router information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/docs/api/command-router/">Command
 * Router API</a>. It receives AMQP 1.0 messages representing requests and forwards them to the command router service
 * implementation. The outcome is then returned to the peer in a response message.
 *
 * @param <S> The type of service this endpoint delegates to.
 */
public class DelegatingCommandRouterAmqpEndpoint<S extends CommandRouterService> extends AbstractDelegatingRequestResponseEndpoint<S, ServiceConfigProperties> {

    private static final String SPAN_NAME_SET_LAST_GATEWAY = "set last known gateway";
    private static final String SPAN_NAME_REGISTER_COMMAND_CONSUMER = "register command consumer";
    private static final String SPAN_NAME_UNREGISTER_COMMAND_CONSUMER = "unregister command consumer";
    private static final String SPAN_NAME_ENABLE_COMMAND_ROUTING = "enable command routing";

    /**
     * Creates an endpoint for a service instance.
     *
     * @param vertx The vert.x instance to use.
     * @param service The service to delegate to.
     * @throws NullPointerException if any of the parameters is {@code null};
     */
    public DelegatingCommandRouterAmqpEndpoint(final Vertx vertx, final S service) {
        super(vertx, service);
    }

    @Override
    protected Future<Message> handleRequestMessage(final Message requestMessage, final ResourceIdentifier targetAddress,
            final SpanContext spanContext) {
        Objects.requireNonNull(requestMessage);
        Objects.requireNonNull(targetAddress);

        switch (CommandRouterConstants.CommandRouterAction.from(requestMessage.getSubject())) {
        case SET_LAST_KNOWN_GATEWAY:
            return processSetLastKnownGatewayRequest(requestMessage, targetAddress, spanContext);
        case REGISTER_COMMAND_CONSUMER:
            return processRegisterCommandConsumer(requestMessage, targetAddress, spanContext);
        case UNREGISTER_COMMAND_CONSUMER:
            return processUnregisterCommandConsumer(requestMessage, targetAddress, spanContext);
        case ENABLE_COMMAND_ROUTING:
            return processEnableCommandRouting(requestMessage, targetAddress, spanContext);
        default:
            return processCustomOperationMessage(requestMessage, spanContext);
        }
    }

    /**
     * Processes a <em>set last known gateway</em> request message.
     *
     * @param request The request message.
     * @param targetAddress The address the message is sent to.
     * @param spanContext The span context representing the request to be processed.
     * @return The response to send to the client via the event bus.
     */
    protected Future<Message> processSetLastKnownGatewayRequest(final Message request,
            final ResourceIdentifier targetAddress, final SpanContext spanContext) {
        final String tenantId = targetAddress.getTenantId();

        final String deviceIdAppProperty = AmqpUtils.getDeviceId(request);
        final String gatewayIdAppProperty = AmqpUtils.getGatewayId(request);

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                spanContext,
                SPAN_NAME_SET_LAST_GATEWAY,
                getClass().getSimpleName()
        ).start();

        final Future<CommandRouterResult> resultFuture;
        if (tenantId == null) {
            TracingHelper.logError(span, "missing tenant");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "missing tenant"));
        } else {
            if (deviceIdAppProperty != null && gatewayIdAppProperty != null) {
                logger.debug("setting last known gateway for tenant [{}], device [{}] to {}", tenantId, deviceIdAppProperty, gatewayIdAppProperty);

                TracingHelper.TAG_TENANT_ID.set(span, tenantId);
                TracingHelper.TAG_DEVICE_ID.set(span, deviceIdAppProperty);
                TracingHelper.TAG_GATEWAY_ID.set(span, gatewayIdAppProperty);
                if (AmqpUtils.getPayloadSize(request) != 0) {
                    logger.debug("ignoring payload in last known gateway request containing device/gateway properties");
                }
                resultFuture = getService().setLastKnownGatewayForDevice(tenantId, deviceIdAppProperty, gatewayIdAppProperty, span);

            } else if (AmqpUtils.getPayloadSize(request) != 0) {
                TracingHelper.TAG_TENANT_ID.set(span, tenantId);

                final Buffer payload = AmqpUtils.getPayload(request);
                resultFuture = parseSetLastKnownGatewayJson(payload)
                        .compose(deviceToGatewayMap -> {
                            logger.debug("setting {} last known gateway entries for tenant [{}]", deviceToGatewayMap.size(), tenantId);
                            span.log(Map.of("no_of_entries", deviceToGatewayMap.size()));
                            return getService().setLastKnownGatewayForDevice(tenantId, deviceToGatewayMap, span);
                        });
            } else {
                final String error = "either device_id and gateway_id application properties or alternatively a JSON payload must be set";
                TracingHelper.logError(span, error);
                resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, error));
            }
        }
        return finishSpanOnFutureCompletion(span, resultFuture.map(res -> AbstractRequestResponseEndpoint.getAmqpReply(
                CommandRouterConstants.COMMAND_ROUTER_ENDPOINT,
                tenantId,
                request,
                res)));
    }

    private Future<Map<String, String>> parseSetLastKnownGatewayJson(final Buffer payload) {
        final Promise<Map<String, String>> result = Promise.promise();
        try {
            final Map<String, String> resultMap = new HashMap<>();
            final JsonObject jsonObject = payload.toJsonObject();
            jsonObject.forEach(entry -> {
                if (entry.getValue() instanceof String) {
                    // key is device id, value is gateway id
                    resultMap.put(entry.getKey(), (String) entry.getValue());
                }
            });
            result.complete(resultMap);
        } catch (final DecodeException e) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "payload must contain a JSON object if device_id and gateway_id application properties are not set"));
        }
        return result.future();
    }

    /**
     * Processes a <em>register command consumer</em> request message.
     *
     * @param request The request message.
     * @param targetAddress The address the message is sent to.
     * @param spanContext The span context representing the request to be processed.
     * @return The response to send to the client via the event bus.
     */
    protected Future<Message> processRegisterCommandConsumer(final Message request,
            final ResourceIdentifier targetAddress, final SpanContext spanContext) {
        final String tenantId = targetAddress.getTenantId();
        final String deviceId = AmqpUtils.getDeviceId(request);
        final String adapterInstanceId = AmqpUtils.getApplicationProperty(
                request,
                CommandConstants.MSG_PROPERTY_ADAPTER_INSTANCE_ID,
                String.class);
        final Integer lifespanSecondsOrNull = AmqpUtils.getApplicationProperty(
                request,
                MessageHelper.APP_PROPERTY_LIFESPAN,
                Integer.class);

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                spanContext,
                SPAN_NAME_REGISTER_COMMAND_CONSUMER,
                getClass().getSimpleName()
        ).start();

        final Boolean sendEvent = getSendEvent(request);

        final Future<Message> resultFuture;
        if (tenantId == null || deviceId == null || adapterInstanceId == null) {
            TracingHelper.logError(span, "missing tenant, device and/or adapter instance id");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            final Duration lifespan = lifespanSecondsOrNull != null ? Duration.ofSeconds(lifespanSecondsOrNull) : Duration.ofSeconds(-1);
            TracingHelper.setDeviceTags(span, tenantId, deviceId);
            TracingHelper.TAG_ADAPTER_INSTANCE_ID.set(span, adapterInstanceId);
            span.setTag(MessageHelper.APP_PROPERTY_LIFESPAN, lifespan.getSeconds());
            logger.debug("register command consumer [tenant-id: {}, device-id: {}, adapter-instance-id {}, lifespan: {}s]",
                    tenantId, deviceId, adapterInstanceId, lifespan.getSeconds());

            resultFuture = getService().registerCommandConsumer(tenantId, deviceId, sendEvent, adapterInstanceId, lifespan, span)
                    .map(res -> AbstractRequestResponseEndpoint.getAmqpReply(
                            CommandRouterConstants.COMMAND_ROUTER_ENDPOINT,
                            tenantId,
                            request,
                            res)
                    );
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    /**
     * Processes a <em>unregister command consumer</em> request message.
     *
     * @param request The request message.
     * @param targetAddress The address the message is sent to.
     * @param spanContext The span context representing the request to be processed.
     * @return The response to send to the client via the event bus.
     */
    protected Future<Message> processUnregisterCommandConsumer(final Message request,
            final ResourceIdentifier targetAddress, final SpanContext spanContext) {
        final String tenantId = targetAddress.getTenantId();
        final String deviceId = AmqpUtils.getDeviceId(request);
        final String adapterInstanceId = AmqpUtils.getApplicationProperty(
                request,
                CommandConstants.MSG_PROPERTY_ADAPTER_INSTANCE_ID,
                String.class);

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                spanContext,
                SPAN_NAME_UNREGISTER_COMMAND_CONSUMER,
                getClass().getSimpleName()
        ).start();

        final Boolean sendEvent =  getSendEvent(request);

        final Future<Message> resultFuture;
        if (tenantId == null || deviceId == null || adapterInstanceId == null) {
            TracingHelper.logError(span, "missing tenant, device and/or adapter instance id");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            TracingHelper.setDeviceTags(span, tenantId, deviceId);
            TracingHelper.TAG_ADAPTER_INSTANCE_ID.set(span, adapterInstanceId);
            logger.debug("unregister command consumer [tenant-id: {}, device-id: {}, adapter-instance-id {}]",
                    tenantId, deviceId, adapterInstanceId);

            resultFuture = getService().unregisterCommandConsumer(tenantId, deviceId, sendEvent, adapterInstanceId, span)
                    .map(res -> AbstractRequestResponseEndpoint.getAmqpReply(
                            CommandRouterConstants.COMMAND_ROUTER_ENDPOINT,
                            tenantId,
                            request,
                            res)
                    );
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    /**
     * Processes an <em>enable command request</em> request message.
     *
     * @param request The request message.
     * @param targetAddress The address the message is sent to.
     * @param spanContext The span context representing the request to be processed.
     * @return The response to send to the client via the event bus.
     */
    protected Future<Message> processEnableCommandRouting(
            final Message request,
            final ResourceIdentifier targetAddress,
            final SpanContext spanContext) {

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                spanContext,
                SPAN_NAME_ENABLE_COMMAND_ROUTING,
                getClass().getSimpleName()
        ).start();

        final Future<Message> response = parseTenantIdentifiers(request)
            .compose(tenantIds -> {
                span.log(Map.of("no_of_tenants", tenantIds.size()));
                return getService().enableCommandRouting(tenantIds, span);
            })
            .map(result -> AbstractRequestResponseEndpoint.getAmqpReply(targetAddress.getEndpoint(), null, request, result));

        return finishSpanOnFutureCompletion(span, response);
    }

    private Future<List<String>> parseTenantIdentifiers(final Message request) {
        final Buffer payload = AmqpUtils.getPayload(request);
        if (payload == null) {
            return Future.succeededFuture(List.of());
        }
        final Promise<List<String>> result = Promise.promise();
        try {
            final JsonArray array = payload.toJsonArray();
            final List<String> tenantIds = array.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .collect(Collectors.toList());
            result.complete(tenantIds);
        } catch (final DecodeException e) {
            result.fail(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "payload must contain JSON array of tenant identifiers"));
        }
        return result.future();
    }

    /**
     * Processes a request for a non-standard operation.
     * <p>
     * Subclasses should override this method in order to support additional, custom
     * operations that are not defined by Hono's Command Router API.
     * <p>
     * This default implementation simply returns a future that is failed with a
     * {@link ClientErrorException} with an error code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @param spanContext The span context representing the request to be processed.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<Message> processCustomOperationMessage(final Message request, final SpanContext spanContext) {
        logger.debug("invalid operation in request message [{}]", request.getSubject());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }


    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier linkTarget, final Message msg) {
        return GenericRequestMessageFilter.isValidRequestMessage(msg);
    }

    @Override
    public final String getName() {
        return CommandRouterConstants.COMMAND_ROUTER_ENDPOINT;
    }

    private static Boolean getSendEvent(final Message request) {
        return Optional.ofNullable(AmqpUtils.getApplicationProperty(
                request,
                MessageHelper.APP_PROPERTY_SEND_EVENT,
                Boolean.class)).orElse(false);
    }
}
