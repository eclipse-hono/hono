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
package org.eclipse.hono.service.commandrouter;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.AbstractDelegatingRequestResponseEndpoint;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandRouterConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

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
                    .map(res -> CommandRouterConstants.getAmqpReply(
                            CommandRouterConstants.COMMAND_ROUTER_ENDPOINT,
                            tenantId,
                            request,
                            res)
                    );
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
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
        final String deviceId = MessageHelper.getDeviceId(request);
        final String adapterInstanceId = MessageHelper.getApplicationProperty(request.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, String.class);
        final Integer lifespanSecondsOrNull = MessageHelper.getApplicationProperty(request.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_LIFESPAN, Integer.class);

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                spanContext,
                SPAN_NAME_REGISTER_COMMAND_CONSUMER,
                getClass().getSimpleName()
        ).start();

        final Future<Message> resultFuture;
        if (tenantId == null || deviceId == null || adapterInstanceId == null) {
            TracingHelper.logError(span, "missing tenant, device and/or adapter instance id");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            final Duration lifespan = lifespanSecondsOrNull != null ? Duration.ofSeconds(lifespanSecondsOrNull) : Duration.ofSeconds(-1);
            TracingHelper.TAG_TENANT_ID.set(span, tenantId);
            TracingHelper.TAG_DEVICE_ID.set(span, deviceId);
            span.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
            span.setTag(MessageHelper.APP_PROPERTY_LIFESPAN, lifespan.getSeconds());
            log.debug("register command consumer [tenant-id: {}, device-id: {}, adapter-instance-id {}, lifespan: {}s]",
                    tenantId, deviceId, adapterInstanceId, lifespan.getSeconds());

            resultFuture = getService().registerCommandConsumer(tenantId, deviceId, adapterInstanceId, lifespan, span)
                    .map(res -> CommandRouterConstants.getAmqpReply(
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
        final String deviceId = MessageHelper.getDeviceId(request);
        final String adapterInstanceId = MessageHelper.getApplicationProperty(request.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, String.class);

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                spanContext,
                SPAN_NAME_UNREGISTER_COMMAND_CONSUMER,
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
            log.debug("unregister command consumer [tenant-id: {}, device-id: {}, adapter-instance-id {}]",
                    tenantId, deviceId, adapterInstanceId);

            resultFuture = getService().unregisterCommandConsumer(tenantId, deviceId, adapterInstanceId, span)
                    .map(res -> CommandRouterConstants.getAmqpReply(
                            CommandRouterConstants.COMMAND_ROUTER_ENDPOINT,
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
        log.debug("invalid operation in request message [{}]", request.getSubject());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }


    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier linkTarget, final Message msg) {
        return CommandRouterMessageFilter.verify(linkTarget, msg);
    }

    @Override
    public final String getName() {
        return CommandRouterConstants.COMMAND_ROUTER_ENDPOINT;
    }
}
