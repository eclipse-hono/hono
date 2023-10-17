/**
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.adapter.AbstractProtocolAdapterBase;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.service.metric.MetricsTags.TtdStatus;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Timer.Sample;
import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

/**
 * A base class for implementing CoAP resources providing access to Hono's south bound
 * Telemetry, Event and Command &amp; Control APIs.
 *
 */
public abstract class AbstractHonoResource extends TracingSupportingHonoResource {

    private static final String KEY_TIMER_ID = "timerId";
    private static final String KEY_MICROMETER_SAMPLE = "micrometer.sample";
    private static final Logger LOG = LoggerFactory.getLogger(AbstractHonoResource.class);

    private final Vertx vertx;

    /**
     * Creates a new resource.
     *
     * @param resourceName The name of this resource.
     * @param adapter The protocol adapter that this resource is part of.
     * @param tracer Open Tracing tracer to use for tracking the processing of requests.
     * @param vertx The vert.x instance to run on.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected AbstractHonoResource(
            final String resourceName,
            final CoapProtocolAdapter adapter,
            final Tracer tracer,
            final Vertx vertx) {
        super(adapter, tracer, resourceName);
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Gets an authenticated device's identity for a CoAP POST request.
     *
     * @param exchange The CoAP exchange with URI and/or peer's principal.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded if the device can be determined from the CoAP exchange,
     *         otherwise the future will be failed with a {@link ClientErrorException}.
     */
    protected Future<RequestDeviceAndAuth> getPostRequestDeviceAndAuth(final CoapExchange exchange) {

        return Optional.ofNullable(TracingSupportingHonoResource.getAuthenticatedDevice(exchange))
                .map(authenticatedDevice -> new RequestDeviceAndAuth(
                        authenticatedDevice,
                        TracingSupportingHonoResource.getAuthId(exchange),
                        authenticatedDevice))
                .map(Future::succeededFuture)
                .orElseGet(() -> Future.failedFuture(new ClientErrorException(
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        "DTLS session does not contain authenticated Device")));
    }

    /**
     * Gets a device identity for a CoAP PUT request which contains a tenant and device id in its URI.
     *
     * @param exchange The CoAP exchange with URI and/or peer's principal.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded if the device can be determined from the CoAP exchange,
     *         otherwise the future will be failed with a {@link ClientErrorException}.
     */
    protected Future<RequestDeviceAndAuth> getPutRequestDeviceAndAuth(final CoapExchange exchange) {

        final List<String> pathList = exchange.getRequestOptions().getUriPath();
        if (pathList.isEmpty()) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND,
                    "request URI must not be empty"));
        }

        final String[] path = pathList.toArray(new String[pathList.size()]);
        final var requestedResource = ResourceIdentifier.fromPath(path);

        final Promise<RequestDeviceAndAuth> result = Promise.promise();
        Optional.ofNullable(TracingSupportingHonoResource.getAuthenticatedDevice(exchange))
            .ifPresentOrElse(
                    authenticatedDevice -> {
                        final String tenantId = Optional.ofNullable(requestedResource.getTenantId())
                                .orElse(authenticatedDevice.getTenantId());
                        if (Strings.isNullOrEmpty(requestedResource.getResourceId())) {
                            result.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND,
                                    "request URI must contain device ID"));
                        } else if (authenticatedDevice.getTenantId().equals(tenantId)) {
                            result.complete(new RequestDeviceAndAuth(
                                    new DeviceUser(tenantId, requestedResource.getResourceId()),
                                    TracingSupportingHonoResource.getAuthId(exchange),
                                    authenticatedDevice));
                        } else {
                            result.fail(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                                    "tenant ID in request URI must match provided credentials"));
                        }
                    },
                    () -> {
                        // unauthenticated device request
                        // URI must contain tenant and device IDs and tenant must match authenticated peer's tenant
                        if (Strings.isNullOrEmpty(requestedResource.getTenantId())
                                || Strings.isNullOrEmpty(requestedResource.getResourceId())) {
                            result.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND,
                                    "request URI must contain tenant and device ID"));
                        } else {
                            result.complete(new RequestDeviceAndAuth(
                                    new DeviceUser(requestedResource.getTenantId(), requestedResource.getResourceId()),
                                    null,
                                    null));
                        }
                    });
        return result.future();
    }

    /**
     * Creates a new context for processing a request from an authenticated device.
     *
     * @param exchange The CoAP request to process.
     * @param deviceAndAuth The device that the request was received from.
     * @param span The Open Tracing span to use for tracking the processing of the request.
     * @return The context.
     */
    protected CoapContext newContext(final CoapExchange exchange, final RequestDeviceAndAuth deviceAndAuth, final Span span) {
        return CoapContext.fromRequest(
                exchange,
                deviceAndAuth.getOriginDevice(),
                deviceAndAuth.getAuthenticatedDevice(),
                deviceAndAuth.getAuthId(),
                span,
                getAdapter().getMetrics().startTimer());
    }

    @Override
    protected Future<CoapContext> createCoapContextForPost(final CoapExchange exchange, final Span span) {
        return getPostRequestDeviceAndAuth(exchange).map(deviceAndAuth -> newContext(exchange, deviceAndAuth, span));
    }

    @Override
    protected Future<CoapContext> createCoapContextForPut(final CoapExchange exchange, final Span span) {
        return getPutRequestDeviceAndAuth(exchange).map(deviceAndAuth -> newContext(exchange, deviceAndAuth, span));
    }

    /**
     * Adds a Micrometer sample to a command context.
     *
     * @param ctx The context to add the sample to.
     * @param sample The sample.
     * @throws NullPointerException if ctx is {@code null}.
     */
    protected static final void addMicrometerSample(final CommandContext ctx, final Sample sample) {
        Objects.requireNonNull(ctx);
        ctx.put(KEY_MICROMETER_SAMPLE, sample);
    }

    /**
     * Gets the timer used to track the processing of a command message.
     *
     * @param ctx The command context to extract the sample from.
     * @return The sample or {@code null} if the context does not
     *         contain a sample.
     * @throws NullPointerException if ctx is {@code null}.
     */
    protected static final Sample getMicrometerSample(final CommandContext ctx) {
        Objects.requireNonNull(ctx);
        return ctx.get(KEY_MICROMETER_SAMPLE);
    }

    /**
     * Invoked before the message is sent to the downstream peer.
     * <p>
     * Subclasses may override this method in order to customize the
     * properties used for sending the message, e.g. adding custom properties.
     *
     * @param messageProperties The properties that are being added to the downstream message.
     * @param ctx The routing context.
     */
    protected void customizeDownstreamMessageProperties(final Map<String, Object> messageProperties, final CoapContext ctx) {
        // this default implementation does nothing
    }

    /**
     * Forwards a message to the south bound Telemetry or Event API of the messaging infrastructure configured
     * for the tenant that the origin device belongs to.
     * <p>
     * Depending on the outcome of the attempt to upload the message, the CoAP response code is set as
     * described by the <a href="https://www.eclipse.org/hono/docs/user-guide/coap-adapter/">CoAP adapter user guide</a>
     *
     * @param context The request that contains the uploaded message.
     * @param endpoint The type of API endpoint to forward the message to.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded if the message has been forwarded successfully.
     *         In this case one of the context's <em>respond</em> methods will have been invoked to send a CoAP response
     *         back to the device.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected final Future<Void> doUploadMessage(
            final CoapContext context,
            final MetricsTags.EndpointType endpoint) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(endpoint);

        final String contentType = context.getContentType();
        final Buffer payload = context.getPayload();

        if (!AbstractProtocolAdapterBase.isPayloadOfIndicatedType(payload, contentType)) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "content type [%s] does not match payload".formatted(contentType)));
        } else {
            final String gatewayId = context.getGatewayId();
            final String tenantId = context.getOriginDevice().getTenantId();
            final String deviceId = context.getOriginDevice().getDeviceId();
            final MetricsTags.QoS qos = context.isConfirmable() ? MetricsTags.QoS.AT_LEAST_ONCE : MetricsTags.QoS.AT_MOST_ONCE;

            final Span currentSpan = TracingHelper
                    .buildChildSpan(getTracer(), context.getTracingContext(),
                            "upload " + endpoint.getCanonicalName(), getAdapter().getTypeName())
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                    .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                    .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                    .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), context.isDeviceAuthenticated())
                    .withTag(Constants.HEADER_QOS_LEVEL, qos.asTag().getValue())
                    .start();

            final Promise<Void> responseReady = Promise.promise();

            final Future<RegistrationAssertion> tokenTracker = getAdapter().getRegistrationAssertion(
                    tenantId,
                    deviceId,
                    context.getAuthenticatedDevice(),
                    currentSpan.context());
            final Future<TenantObject> tenantTracker = getAdapter().getTenantClient().get(tenantId, currentSpan.context());
            final Future<TenantObject> tenantValidationTracker = tenantTracker
                    .compose(tenantObject -> Future.all(
                            getAdapter().isAdapterEnabled(tenantObject),
                            getAdapter().checkMessageLimit(tenantObject, payload.length(), currentSpan.context()))
                            .map(tenantObject));

            // we only need to consider TTD if the device and tenant are enabled and the adapter
            // is enabled for the tenant
            final Future<Integer> ttdTracker = Future.all(tenantValidationTracker, tokenTracker)
                    .compose(ok -> {
                        final Integer ttdParam = context.getTimeUntilDisconnect();
                        return getAdapter().getTimeUntilDisconnect(tenantTracker.result(), ttdParam)
                                .onSuccess(effectiveTtd -> Optional.ofNullable(effectiveTtd)
                                        .ifPresent(v -> TracingHelper.TAG_DEVICE_TTD.set(currentSpan, v)));
                    });
            final Future<ProtocolAdapterCommandConsumer> commandConsumerTracker = ttdTracker
                    .compose(ttd -> createCommandConsumer(
                            ttd,
                            tenantTracker.result(),
                            deviceId,
                            gatewayId,
                            context,
                            responseReady,
                            currentSpan));

            return commandConsumerTracker
                .compose(commandConsumer -> {
                    final Map<String, Object> props = getAdapter().getDownstreamMessageProperties(context);
                    Optional.ofNullable(commandConsumer)
                            .map(c -> ttdTracker.result())
                            .ifPresent(ttd -> props.put(CommandConstants.MSG_PROPERTY_DEVICE_TTD, ttd));
                    customizeDownstreamMessageProperties(props, context);

                    if (context.isConfirmable()) {
                        context.startAcceptTimer(vertx, tenantTracker.result(), getAdapter().getConfig().getTimeoutToAck());
                    }
                    final Future<Void> sendResult;
                    if (endpoint == EndpointType.EVENT) {
                        sendResult = getAdapter().getEventSender(tenantValidationTracker.result()).sendEvent(
                                tenantTracker.result(),
                                tokenTracker.result(),
                                contentType,
                                payload,
                                props,
                                currentSpan.context());
                    } else {
                        sendResult = getAdapter().getTelemetrySender(tenantValidationTracker.result()).sendTelemetry(
                                tenantTracker.result(),
                                tokenTracker.result(),
                                context.getRequestedQos(),
                                contentType,
                                payload,
                                props,
                                currentSpan.context());
                    }
                    return Future.all(sendResult, responseReady.future()).mapEmpty();
                }).compose(proceed -> {

                    // downstream message sent and (if ttd was set) command was received or ttd has timed out
                    // we wait for the CommandConsumer having been closed before delivering the response to the
                    // device in order to prevent a race condition when the device immediately sends a new
                    // request and the CommandConsumer from the current request has not been closed yet
                    return Optional.ofNullable(commandConsumerTracker.result())
                            .map(consumer -> consumer.close(false, currentSpan.context())
                                    .otherwise(thr -> null))
                            .orElseGet(Future::succeededFuture);

                }).map(proceed -> {

                    final CommandContext commandContext = context.get(CommandContext.KEY_COMMAND_CONTEXT);
                    final Response response = new Response(ResponseCode.CHANGED);
                    if (commandContext != null) {
                        addCommandToResponse(context, response, commandContext, currentSpan);
                        commandContext.accept();
                        getAdapter().getMetrics().reportCommand(
                                commandContext.getCommand().isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                tenantId,
                                tenantTracker.result(),
                                ProcessingOutcome.FORWARDED,
                                commandContext.getCommand().getPayloadSize(),
                                getMicrometerSample(commandContext));
                    }

                    LOG.trace("successfully processed message for device [tenantId: {}, deviceId: {}, endpoint: {}]",
                            tenantId, deviceId, endpoint.getCanonicalName());
                    getAdapter().getMetrics().reportTelemetry(
                            endpoint,
                            tenantId,
                            tenantTracker.result(),
                            MetricsTags.ProcessingOutcome.FORWARDED,
                            qos,
                            payload.length(),
                            getTtdStatus(context),
                            context.getTimer());

                    context.respond(response);
                    currentSpan.finish();
                    return (Void) null;

                }).recover(t -> {

                    LOG.debug("cannot process message from device [tenantId: {}, deviceId: {}, endpoint: {}]",
                            tenantId, deviceId, endpoint.getCanonicalName(), t);
                    final Future<Void> commandConsumerClosedTracker = Optional.ofNullable(commandConsumerTracker.result())
                            .map(consumer -> consumer.close(false, currentSpan.context())
                                    .otherwise(thr -> null))
                            .orElseGet(Future::succeededFuture);
                    final CommandContext commandContext = context.get(CommandContext.KEY_COMMAND_CONTEXT);
                    if (commandContext != null) {
                        TracingHelper.logError(commandContext.getTracingSpan(),
                                "command won't be forwarded to device in CoAP response, CoAP request handling failed", t);
                        commandContext.release(t);
                        currentSpan.log("released command for device");
                    }
                    getAdapter().getMetrics().reportTelemetry(
                            endpoint,
                            tenantId,
                            tenantTracker.result(),
                            ClientErrorException.class.isInstance(t) ? MetricsTags.ProcessingOutcome.UNPROCESSABLE : MetricsTags.ProcessingOutcome.UNDELIVERABLE,
                            qos,
                            payload.length(),
                            getTtdStatus(context),
                            context.getTimer());
                    TracingHelper.logError(currentSpan, t);
                    commandConsumerClosedTracker.onComplete(res -> currentSpan.finish());
                    return Future.failedFuture(t);
                });
        }
    }

    /**
     * Adds a command to a CoAP response.
     * <p>
     * This default implementation adds the command name, content format and response URI to the
     * CoAP response options and puts the command's input data (if any) to the response body.
     *
     * @param requestContext The CoAP request context.
     * @param response The CoAP response.
     * @param commandContext The context containing the command to add.
     * @param currentSpan The Open Tracing span used for tracking the CoAP request.
     */
    protected void addCommandToResponse(
            final CoapContext requestContext,
            final Response response,
            final CommandContext commandContext,
            final Span currentSpan) {

        final Command command = commandContext.getCommand();
        final OptionSet options = response.getOptions();
        LOG.debug("adding command [name: {}, request-id: {}] to response for device [tenant-id: {}, device-id: {}]",
                command.getName(), command.getRequestId(), command.getTenant(), command.getGatewayOrDeviceId());
        commandContext.getTracingSpan().log("forwarding command to device in CoAP response");

        options.addLocationQuery(Constants.HEADER_COMMAND + "=" + command.getName());
        currentSpan.setTag(Constants.HEADER_COMMAND, command.getName());

        final boolean useShortEndpointName = requestContext.hasShortEndpointName();
        String endpointName = null;
        String tenantId = null;
        String deviceId = null;
        String requestId = null;

        if (command.isOneWay()) {
            endpointName = useShortEndpointName ?
                    CommandConstants.COMMAND_ENDPOINT_SHORT : CommandConstants.COMMAND_ENDPOINT;
            if (command.isTargetedAtGateway()) {
                tenantId = "";
                deviceId = command.getDeviceId();
            }
        } else {
            endpointName = useShortEndpointName ?
                    CommandConstants.COMMAND_RESPONSE_ENDPOINT_SHORT : CommandConstants.COMMAND_RESPONSE_ENDPOINT;
            requestId = command.getRequestId();

            if (requestContext.getAuthenticatedDevice() == null) {
                tenantId = command.getTenant();
                deviceId = command.getDeviceId();
            } else if (command.isTargetedAtGateway()) {
                tenantId = "";
                deviceId = command.getDeviceId();
            }
        }

        options.addLocationPath(endpointName);
        Optional.ofNullable(tenantId).ifPresent(options::addLocationPath);
        Optional.ofNullable(deviceId).ifPresent(id -> {
            options.addLocationPath(id);
            currentSpan.setTag(Constants.HEADER_COMMAND_TARGET_DEVICE, id);
        });
        Optional.ofNullable(requestId).ifPresent(id -> {
            options.addLocationPath(id);
            currentSpan.setTag(Constants.HEADER_COMMAND_REQUEST_ID, command.getRequestId());
        });

        final int formatCode = MediaTypeRegistry.parse(command.getContentType());
        if (formatCode != MediaTypeRegistry.UNDEFINED) {
            options.setContentFormat(formatCode);
        } else {
            currentSpan.log("ignoring unknown content type [" + command.getContentType() + "] of command");
        }
        Optional.ofNullable(command.getPayload())
            .map(Buffer::getBytes)
            .ifPresent(response::setPayload);
    }

    /**
     * Creates a consumer for command messages to be sent to a device.
     *
     * @param ttdSecs The number of seconds the device waits for a command.
     * @param tenantObject The tenant configuration object.
     * @param deviceId The identifier of the device.
     * @param gatewayId The identifier of the gateway that is acting on behalf of the device or {@code null} otherwise.
     * @param context The device's currently executing CoAP request context.
     * @param responseReady A future to complete once one of the following conditions are met:
     *            <ul>
     *            <li>the request did not include a <em>hono-ttd</em> query-parameter or</li>
     *            <li>a command has been received and the response ready future has not yet been completed or</li>
     *            <li>the ttd has expired</li>
     *            </ul>
     * @param uploadMessageSpan The OpenTracing Span used for tracking the processing of the request.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be completed with the created message consumer or {@code null}, if the response can be
     *         sent back to the device without waiting for a command.
     *         <p>
     *         The future will be failed with a {@code ServiceInvocationException} if the message consumer could not be
     *         created.
     * @throws NullPointerException if any of the parameters other than TTD or gatewayId is {@code null}.
     */
    protected final Future<ProtocolAdapterCommandConsumer> createCommandConsumer(
            final Integer ttdSecs,
            final TenantObject tenantObject,
            final String deviceId,
            final String gatewayId,
            final CoapContext context,
            final Handler<AsyncResult<Void>> responseReady,
            final Span uploadMessageSpan) {

        Objects.requireNonNull(tenantObject);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(context);
        Objects.requireNonNull(responseReady);
        Objects.requireNonNull(uploadMessageSpan);

        if (ttdSecs == null || ttdSecs <= 0) {
            // no need to wait for a command
            responseReady.handle(Future.succeededFuture());
            return Future.succeededFuture();
        }
        final AtomicBoolean requestProcessed = new AtomicBoolean(false);
        TracingHelper.TAG_DEVICE_TTD.set(uploadMessageSpan, ttdSecs);

        final Span waitForCommandSpan = TracingHelper
                .buildChildSpan(getTracer(), uploadMessageSpan.context(),
                        "create consumer and wait for command", getAdapter().getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .start();
        TracingHelper.setDeviceTags(waitForCommandSpan, tenantObject.getTenantId(), deviceId);

        final Function<CommandContext, Future<Void>> commandHandler = commandContext -> {

            final Span processCommandSpan = TracingHelper
                    .buildFollowsFromSpan(getTracer(), waitForCommandSpan.context(), "process received command")
                    .withTag(Tags.COMPONENT.getKey(), getAdapter().getTypeName())
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                    // add reference to the trace started in the command router when the command was first received
                    .addReference(References.FOLLOWS_FROM, commandContext.getTracingContext())
                    .start();
            TracingHelper.setDeviceTags(processCommandSpan, tenantObject.getTenantId(), deviceId);

            Tags.COMPONENT.set(commandContext.getTracingSpan(), getAdapter().getTypeName());
            commandContext.logCommandToSpan(processCommandSpan);
            final Command command = commandContext.getCommand();
            final Sample commandSample = getAdapter().getMetrics().startTimer();
            if (isCommandValid(command, processCommandSpan)) {
                final Promise<Void> commandHandlerDonePromise = Promise.promise();
                if (requestProcessed.compareAndSet(false, true)) {
                    waitForCommandSpan.finish();
                    getAdapter().checkMessageLimit(tenantObject, command.getPayloadSize(), processCommandSpan.context())
                            .onComplete(result -> {
                                if (result.succeeded()) {
                                    addMicrometerSample(commandContext, commandSample);
                                    // put command context to routing context and notify
                                    context.put(CommandContext.KEY_COMMAND_CONTEXT, commandContext);
                                    commandHandlerDonePromise.complete();
                                } else {
                                    commandContext.reject(result.cause());
                                    TracingHelper.logError(processCommandSpan, "rejected command for device", result.cause());
                                    getAdapter().getMetrics().reportCommand(
                                            command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                            tenantObject.getTenantId(),
                                            tenantObject,
                                            ProcessingOutcome.from(result.cause()),
                                            command.getPayloadSize(),
                                            commandSample);
                                    commandHandlerDonePromise.fail(result.cause());
                                }
                                cancelCommandReceptionTimer(context);
                                setTtdStatus(context, TtdStatus.COMMAND);
                                responseReady.handle(Future.succeededFuture());
                                processCommandSpan.finish();
                            });
                } else {
                    final String errorMsg = "waiting time for command has elapsed or another command has already been processed";
                    LOG.debug("{} [tenantId: {}, deviceId: {}]", errorMsg, tenantObject.getTenantId(), deviceId);
                    getAdapter().getMetrics().reportCommand(
                            command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                            tenantObject.getTenantId(),
                            tenantObject,
                            ProcessingOutcome.UNDELIVERABLE,
                            command.getPayloadSize(),
                            commandSample);
                    final var exception = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, errorMsg);
                    commandContext.release(exception);
                    TracingHelper.logError(processCommandSpan, errorMsg);
                    processCommandSpan.finish();
                    commandHandlerDonePromise.fail(exception);
                }
                return commandHandlerDonePromise.future();
            } else {
                getAdapter().getMetrics().reportCommand(
                        command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                        tenantObject.getTenantId(),
                        tenantObject,
                        ProcessingOutcome.UNPROCESSABLE,
                        command.getPayloadSize(),
                        commandSample);
                LOG.debug("command message is invalid: {}", command);
                commandContext.reject("malformed command message");
                TracingHelper.logError(processCommandSpan, "malformed command message");
                processCommandSpan.finish();
                return Future.failedFuture("malformed command message");
            }
        };

        final Future<ProtocolAdapterCommandConsumer> commandConsumerFuture;
        if (gatewayId != null) {
            // gateway scenario
            commandConsumerFuture = getAdapter().getCommandConsumerFactory().createCommandConsumer(
                    tenantObject.getTenantId(),
                    deviceId,
                    gatewayId,
                    false,
                    commandHandler,
                    Duration.ofSeconds(ttdSecs),
                    waitForCommandSpan.context());
        } else {
            commandConsumerFuture = getAdapter().getCommandConsumerFactory().createCommandConsumer(
                    tenantObject.getTenantId(),
                    deviceId,
                    false,
                    commandHandler,
                    Duration.ofSeconds(ttdSecs),
                    waitForCommandSpan.context());
        }
        return commandConsumerFuture
                .onFailure(thr -> {
                    TracingHelper.logError(waitForCommandSpan, thr);
                    waitForCommandSpan.finish();
                })
                .map(consumer -> {
                    if (!requestProcessed.get()) {
                        // if the request was not responded already, add a timer for triggering an empty response
                        addCommandReceptionTimer(context, requestProcessed, responseReady, ttdSecs, waitForCommandSpan);
                        context.startAcceptTimer(vertx, tenantObject, getAdapter().getConfig().getTimeoutToAck());
                    }
                    // wrap the consumer to be able to create a separate span on closing the consumer (errors should just be logged on that span, not the parent)
                    return new ProtocolAdapterCommandConsumer() {
                        @Override
                        public Future<Void> close(final boolean sendEvent, final SpanContext spanContext) {
                            final Span closeConsumerSpan = TracingHelper
                                    .buildChildSpan(getTracer(), spanContext, "close command consumer",
                                            getAdapter().getTypeName())
                                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                                    .start();
                            TracingHelper.setDeviceTags(closeConsumerSpan, tenantObject.getTenantId(), deviceId);
                            return consumer.close(sendEvent, closeConsumerSpan.context())
                                    .onFailure(thr -> TracingHelper.logError(closeConsumerSpan, thr))
                                    .onComplete(ar -> closeConsumerSpan.finish());
                        }
                    };
                });
    }

    /**
     * Validate if a command is valid and can be sent as response.
     * <p>
     * The default implementation will call {@link Command#isValid()}. Protocol adapters may override this, but should
     * consider calling the super method.
     *
     * @param command The command to validate, will never be {@code null}.
     * @param currentSpan The current tracing span.
     * @return {@code true} if the command is valid, {@code false} otherwise.
     */
    protected boolean isCommandValid(final Command command, final Span currentSpan) {
        return command.isValid();
    }

    /**
     * Sets a timer to trigger the sending of a (empty) response to a device if no command has been received from an
     * application within a given amount of time.
     * <p>
     * The created timer's ID is put to the routing context using key {@link #KEY_TIMER_ID}.
     *
     * @param context The device's currently executing HTTP request.
     * @param requestProcessed protect request from multiple responses
     * @param responseReady The future to complete when the time has expired.
     * @param delaySecs The number of seconds to wait for a command.
     * @param waitForCommandSpan The span tracking the command reception.
     */
    private void addCommandReceptionTimer(
            final CoapContext context,
            final AtomicBoolean requestProcessed,
            final Handler<AsyncResult<Void>> responseReady,
            final long delaySecs,
            final Span waitForCommandSpan) {

        final Long timerId = vertx.setTimer(delaySecs * 1000L, id -> {

            LOG.trace("time to wait [{}s] for command expired [timer id: {}]", delaySecs, id);

            if (requestProcessed.compareAndSet(false, true)) {
                // no command to be sent,
                // send empty response
                setTtdStatus(context, TtdStatus.EXPIRED);
                waitForCommandSpan.log(String.format("time to wait for command expired (%ds)", delaySecs));
                waitForCommandSpan.finish();
                responseReady.handle(Future.succeededFuture());
            } else {
                // a command has been sent to the device already
                LOG.trace("response already sent, nothing to do ...");
            }
        });

        LOG.trace("adding command reception timer [id: {}]", timerId);

        context.put(KEY_TIMER_ID, timerId);
    }

    private void cancelCommandReceptionTimer(final CoapContext context) {

        final Long timerId = context.get(KEY_TIMER_ID);
        if (timerId != null && timerId >= 0) {
            if (vertx.cancelTimer(timerId)) {
                LOG.trace("Cancelled timer id {}", timerId);
            } else {
                LOG.debug("Could not cancel timer id {}", timerId);
            }
        }
    }

    private void setTtdStatus(final CoapContext context, final TtdStatus status) {
        context.put(TtdStatus.class.getName(), status);
    }

    private TtdStatus getTtdStatus(final CoapContext context) {
        return Optional.ofNullable((TtdStatus) context.get(TtdStatus.class.getName()))
                .orElse(TtdStatus.NONE);
    }

}
