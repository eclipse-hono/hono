/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.hono.adapter.client.command.CommandResponse;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

/**
 * A CoAP resource for uploading command response messages.
 *
 */
public class CommandResponseResource extends AbstractHonoResource {

    private static final Logger LOG = LoggerFactory.getLogger(CommandResponseResource.class);

    /**
     * Creates a new resource.
     * <p>
     * Delegates to {@link #CommandResponseResource(String, CoapProtocolAdapter, Tracer, Vertx)} using
     * {@value CommandConstants#COMMAND_RESPONSE_ENDPOINT} as the resource name.
     *
     * @param adapter The protocol adapter that this resource is part of.
     * @param tracer Open Tracing tracer to use for tracking the processing of requests.
     * @param vertx The vert.x instance to run on.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public CommandResponseResource(
            final CoapProtocolAdapter adapter,
            final Tracer tracer,
            final Vertx vertx) {
        this(CommandConstants.COMMAND_RESPONSE_ENDPOINT, adapter, tracer, vertx);
    }

    /**
     * Creates a new resource.
     *
     * @param resourceName The name of this resource.
     * @param adapter The protocol adapter that this resource is part of.
     * @param tracer Open Tracing tracer to use for tracking the processing of requests.
     * @param vertx The vert.x instance to run on.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public CommandResponseResource(
            final String resourceName,
            final CoapProtocolAdapter adapter,
            final Tracer tracer,
            final Vertx vertx) {
        super(resourceName, adapter, tracer, vertx);
    }

    @Override
    public Future<?> handlePost(final CoapContext ctx) {
        return uploadCommandResponseMessage(ctx);
    }

    @Override
    public Future<?> handlePut(final CoapContext ctx) {
        return uploadCommandResponseMessage(ctx);
    }

    /**
     * Forwards a command response to a downstream application.
     *
     * @param context The context representing the command response to be forwarded.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded if the message has been forwarded successfully.
     *         In this case one of the context's <em>respond</em> methods will have been invoked to send a CoAP response
     *         back to the device.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if context is {@code null}.
     */
    public final Future<?> uploadCommandResponseMessage(final CoapContext context) {
        Objects.requireNonNull(context);

        final Device device = context.getOriginDevice();
        final Device authenticatedDevice = context.getAuthenticatedDevice();

        if (!context.isConfirmable()) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "command response endpoint supports confirmable request messages only"));
        }

        final Buffer payload = context.getPayload();
        final String contentType = context.getContentType();
        final String commandRequestId = context.getCommandRequestId();
        final Integer responseStatus = context.getCommandResponseStatus();
        LOG.debug("processing response to command [tenantId: {}, deviceId: {}, cmd-req-id: {}, status code: {}]",
                device.getTenantId(), device.getDeviceId(), commandRequestId, responseStatus);

        final Span currentSpan = TracingHelper
                .buildChildSpan(getTracer(), context.getTracingContext(), "upload Command response", getAdapter().getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_TENANT_ID, device.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, device.getDeviceId())
                .withTag(Constants.HEADER_COMMAND_RESPONSE_STATUS, responseStatus)
                .withTag(Constants.HEADER_COMMAND_REQUEST_ID, commandRequestId)
                .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), authenticatedDevice != null)
                .start();

        final Future<TenantObject> tenantTracker = getAdapter().getTenantClient().get(device.getTenantId(), currentSpan.context());
        final Optional<CommandResponse> cmdResponse = Optional.ofNullable(CommandResponse.fromRequestId(
                commandRequestId,
                device.getTenantId(),
                device.getDeviceId(),
                payload,
                contentType,
                responseStatus));
        final Future<CommandResponse> commandResponseTracker = cmdResponse
                .map(res -> Future.succeededFuture(res))
                .orElseGet(() -> Future.failedFuture(
                        new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                        String.format("command-request-id [%s] or status code [%s] is missing/invalid",
                                commandRequestId, responseStatus))));

        return CompositeFuture.all(tenantTracker, commandResponseTracker)
                .compose(ok -> {
                    final Future<RegistrationAssertion> deviceRegistrationTracker = getAdapter().getRegistrationAssertion(
                            device.getTenantId(),
                            device.getDeviceId(),
                            authenticatedDevice,
                            currentSpan.context());
                    final Future<Void> tenantValidationTracker = CompositeFuture.all(
                            getAdapter().isAdapterEnabled(tenantTracker.result()),
                            getAdapter().checkMessageLimit(tenantTracker.result(), payload.length(), currentSpan.context()))
                            .mapEmpty();

                    return CompositeFuture.all(tenantValidationTracker, deviceRegistrationTracker);
                })
                .compose(ok -> getAdapter().getCommandResponseSender(tenantTracker.result())
                        .sendCommandResponse(commandResponseTracker.result(), currentSpan.context()))
                .onSuccess(ok -> {
                    LOG.trace("forwarded command response [command-request-id: {}] to downstream application",
                            commandRequestId);
                    currentSpan.log("forwarded command response to application");
                    getAdapter().getMetrics().reportCommand(
                            Direction.RESPONSE,
                            device.getTenantId(),
                            tenantTracker.result(),
                            ProcessingOutcome.FORWARDED,
                            payload.length(),
                            context.getTimer());
                    context.respondWithCode(ResponseCode.CHANGED);
                })
                .onFailure(t -> {
                    LOG.debug("could not send command response [command-request-id: {}] to application",
                            commandRequestId, t);
                    TracingHelper.logError(currentSpan, t);
                    getAdapter().getMetrics().reportCommand(
                            Direction.RESPONSE,
                            device.getTenantId(),
                            tenantTracker.result(),
                            ProcessingOutcome.from(t),
                            payload.length(),
                            context.getTimer());
                })
                .onComplete(r -> currentSpan.finish());
    }

}
