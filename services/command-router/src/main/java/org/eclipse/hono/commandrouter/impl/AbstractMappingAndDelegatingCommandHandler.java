/*******************************************************************************
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.NoConsumerException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.InternalCommandSender;
import org.eclipse.hono.client.registry.DeviceDisabledOrNotRegisteredException;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.registry.TenantDisabledOrNotRegisteredException;
import org.eclipse.hono.commandrouter.CommandRouterMetrics;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.Futures;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Timer;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * A handler for mapping received commands to the corresponding target protocol adapter instance
 * and forwarding them using an {@link org.eclipse.hono.client.command.InternalCommandSender}.
 *
 * @param <T> The type of command context of the commands handled here.
 */
public abstract class AbstractMappingAndDelegatingCommandHandler<T extends CommandContext> implements Lifecycle {

    /**
     * Duration after which record processing is cancelled.
     * <p>
     * Usually, the timeout values configured for the service clients involved in command processing should define the
     * overall time limit. The timeout mechanism associated with this value here serves as a safeguard against
     * exceptional cases where the other timeouts didn't get applied. It thereby prevents command processing from
     * getting stuck, so that the command consumer doesn't get blocked.
     */
    private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(8);

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * A client for accessing Hono's <em>Tenant</em> service.
     */
    protected final TenantClient tenantClient;
    /**
     * A tracer to use for tracking the processing of commands.
     */
    protected final Tracer tracer;

    private final Vertx vertx;
    private final CommandTargetMapper commandTargetMapper;
    private final InternalCommandSender internalCommandSender;
    private final CommandRouterMetrics metrics;

    private final CommandProcessingQueue<T> commandQueue;

    /**
     * Creates a new MappingAndDelegatingCommandHandler instance.
     *
     * @param vertx The Vert.x instance to use.
     * @param tenantClient The Tenant service client.
     * @param commandQueue The command processing queue instance to use.
     * @param commandTargetMapper The mapper component to determine the command target.
     * @param internalCommandSender The command sender to publish commands to the internal command topic.
     * @param metrics The component to use for reporting metrics.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public AbstractMappingAndDelegatingCommandHandler(
            final Vertx vertx,
            final TenantClient tenantClient,
            final CommandProcessingQueue<T> commandQueue,
            final CommandTargetMapper commandTargetMapper,
            final InternalCommandSender internalCommandSender,
            final CommandRouterMetrics metrics,
            final Tracer tracer) {

        this.vertx = Objects.requireNonNull(vertx);
        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.commandQueue = Objects.requireNonNull(commandQueue);
        this.commandTargetMapper = Objects.requireNonNull(commandTargetMapper);
        this.internalCommandSender = Objects.requireNonNull(internalCommandSender);
        this.metrics = Objects.requireNonNull(metrics);
        this.tracer = Objects.requireNonNull(tracer);
    }

    /**
     * {@inheritDoc}
     *
     * @return The outcome of starting the internal command sender.
     */
    @Override
    public Future<Void> start() {
        return internalCommandSender.start();
    }

    /**
     * {@inheritDoc}
     *
     * @return The outcome of stopping the internal command sender.
     */
    @Override
    public Future<Void> stop() {
        commandQueue.clear();
        return internalCommandSender.stop();
    }

    /**
     * Gets the messaging type this handler uses.
     *
     * @return The used messaging type.
     */
    protected abstract MessagingType getMessagingType();

    /**
     * Gets the component to use for reporting metrics.
     *
     * @return The metrics component.
     */
    protected final CommandRouterMetrics getMetrics() {
        return metrics;
    }

    /**
     * Delegates an incoming command to the protocol adapter instance that the target
     * device is connected to.
     * <p>
     * Determines the target gateway (if applicable) and protocol adapter instance for an incoming command
     * and delegates the command to the resulting protocol adapter instance.
     *
     * @param commandContext The context of the command to send.
     * @param timer The timer indicating the amount of time used for processing the command message.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected Future<Void> mapAndDelegateIncomingCommand(final T commandContext, final Timer.Sample timer) {
        Objects.requireNonNull(commandContext);
        Objects.requireNonNull(timer);

        commandQueue.add(commandContext);

        final Promise<Void> resultPromise = Promise.promise();
        final long timerId = vertx.setTimer(PROCESSING_TIMEOUT.toMillis(), tid -> {
            if (commandQueue.remove(commandContext) || !commandContext.isCompleted()) {
                log.info("command processing timed out after {}s [{}]", PROCESSING_TIMEOUT.toSeconds(), commandContext.getCommand());
                TracingHelper.logError(commandContext.getTracingSpan(),
                        String.format("command processing timed out after %ds", PROCESSING_TIMEOUT.toSeconds()));
                final ServerErrorException error = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                        "command processing timed out");
                commandContext.release(error);
                resultPromise.tryFail(error);
            }
        });
        mapAndDelegateIncomingCommandInternal(commandContext, timer)
                .onComplete(ar -> {
                    vertx.cancelTimer(timerId);
                    if (ar.failed()) {
                        commandQueue.remove(commandContext);
                    }
                    Futures.tryHandleResult(resultPromise, ar);
                });
        return resultPromise.future();
    }

    private Future<Void> mapAndDelegateIncomingCommandInternal(final T commandContext, final Timer.Sample timer) {
        final Command command = commandContext.getCommand();

        // determine last used gateway device id
        if (log.isTraceEnabled()) {
            log.trace("determine command target gateway/adapter for [{}]", command);
        }

        final Future<TenantObject> tenantObjectFuture = tenantClient.get(
                command.getTenant(),
                commandContext.getTracingContext());

        return tenantObjectFuture
                .compose(tenantObject -> {
                    TenantTraceSamplingHelper.applyTraceSamplingPriority(tenantObject, null,
                            commandContext.getTracingSpan());
                    commandContext.put(CommandContext.KEY_TENANT_CONFIG, tenantObject);

                    // check whether the handler messaging type is equal to the messaging type of the tenant (if set)
                    final MessagingType tenantMessagingType = Optional
                            .ofNullable(tenantObject.getProperty(TenantConstants.FIELD_EXT, JsonObject.class))
                            .map(ext -> ext.getString(TenantConstants.FIELD_EXT_MESSAGING_TYPE))
                            .map(MessagingType::valueOf).orElse(null);
                    if (tenantMessagingType != null && getMessagingType() != tenantMessagingType) {
                        log.info("command received via {} but tenant is configured to use {} [{}]", getMessagingType(),
                                tenantMessagingType, commandContext.getCommand());
                        commandContext.getTracingSpan().log(String.format(
                                "command received via %s but tenant is configured to use %s",
                                getMessagingType(), tenantMessagingType));
                    }
                    return commandTargetMapper.getTargetGatewayAndAdapterInstance(
                            command.getTenant(),
                            command.getDeviceId(),
                            commandContext.getTracingContext());
                })
                .recover(cause -> {
                    final Throwable error;
                    if (tenantObjectFuture.failed() && ServiceInvocationException
                            .extractStatusCode(cause) == HttpURLConnection.HTTP_NOT_FOUND) {
                        error = new TenantDisabledOrNotRegisteredException(command.getTenant(),
                                HttpURLConnection.HTTP_NOT_FOUND);
                    } else if (cause instanceof DeviceDisabledOrNotRegisteredException) {
                        error = cause;
                    } else if (ServiceInvocationException.extractStatusCode(cause) == HttpURLConnection.HTTP_NOT_FOUND) {
                        log.debug("no target adapter instance found for command with device id "
                                + command.getDeviceId(), cause);
                        error = new NoConsumerException("no target adapter instance found");
                    } else {
                        log.debug("error getting target gateway and adapter instance for command with device id "
                                + command.getDeviceId(), cause);
                        error = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                                "error getting target gateway and adapter instance", cause);
                    }
                    if (error instanceof ClientErrorException) {
                        commandContext.reject(error);
                    } else {
                        commandContext.release(error);
                    }
                    reportCommandProcessingError(command, tenantObjectFuture.result(), error, timer);
                    return Future.failedFuture(cause);
                })
                .compose(result -> {
                    final String targetAdapterInstanceId = result
                            .getString(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID);
                    final String targetDeviceId = result.getString(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID);
                    final String targetGatewayId = targetDeviceId.equals(command.getDeviceId()) ? null : targetDeviceId;

                    if (Objects.isNull(targetGatewayId)) {
                        log.trace("determined target adapter instance [{}] for [{}] (command not mapped to gateway)",
                                targetAdapterInstanceId, command);
                    } else {
                        command.setGatewayId(targetGatewayId);
                        log.trace("determined target gateway [{}] and adapter instance [{}] for [{}]",
                                targetGatewayId, targetAdapterInstanceId, command);
                        commandContext.getTracingSpan().log("determined target gateway [" + targetGatewayId + "]");
                    }
                    return commandQueue.applySendCommandAction(commandContext,
                            () -> sendCommandInternal(commandContext, targetAdapterInstanceId,
                                    tenantObjectFuture.result(), timer));
                });
    }

    /**
     * Sends the given command to the internal Command and Control API endpoint provided by protocol adapters,
     * adhering to the specification of {@link InternalCommandSender#sendCommand(CommandContext, String)}.
     *
     * @param commandContext Context of the command to send.
     * @param targetAdapterInstanceId The target protocol adapter instance id.
     * @param tenantObject The tenant of the command target device.
     * @param timer The timer indicating the amount of time used for processing the command message.
     * @return A future indicating the output of the operation.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    private Future<Void> sendCommandInternal(
            final T commandContext,
            final String targetAdapterInstanceId,
            final TenantObject tenantObject,
            final Timer.Sample timer) {
        return internalCommandSender.sendCommand(commandContext, targetAdapterInstanceId)
                .onFailure(thr -> reportCommandProcessingError(commandContext.getCommand(), tenantObject, thr, timer));
    }

    /**
     * Reports a command for which processing failed.
     *
     * @param command The command to report.
     * @param tenantObject The tenant of the command target device.
     * @param processingException The exception that occurred during processing of the command.
     * @param timer The timer indicating the amount of time used for processing the command message.
     */
    protected void reportCommandProcessingError(final Command command, final TenantObject tenantObject,
            final Throwable processingException, final Timer.Sample timer) {
        metrics.reportCommand(
                command.isOneWay() ? MetricsTags.Direction.ONE_WAY : MetricsTags.Direction.REQUEST,
                command.getTenant(),
                tenantObject,
                MetricsTags.ProcessingOutcome.from(processingException),
                command.getPayloadSize(),
                timer);
    }

    /**
     * Reports an invalid command.
     *
     * @param commandContext The context of the command to report.
     * @param timer The timer indicating the amount of time used for processing the command message.
     */
    protected void reportInvalidCommand(final CommandContext commandContext, final Timer.Sample timer) {
        final Command command = commandContext.getCommand();
        final Future<TenantObject> tenantObjectFuture = tenantClient
                .get(command.getTenant(), commandContext.getTracingContext());
        tenantObjectFuture
                .recover(thr -> Future.succeededFuture(null)) // ignore error here
                .onSuccess(tenantObjectOrNull -> {
                    metrics.reportCommand(
                            command.isOneWay() ? MetricsTags.Direction.ONE_WAY : MetricsTags.Direction.REQUEST,
                            command.getTenant(),
                            tenantObjectOrNull,
                            MetricsTags.ProcessingOutcome.UNPROCESSABLE,
                            command.getPayloadSize(),
                            timer);
                });
    }

    /**
     * Creates and starts an <em>OpenTracing</em> span for a mapping/delegation operation.
     *
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @param spanContext Existing span context.
     * @return The created and started span.
     */
    protected final Span createSpan(final String tenantId, final String deviceId, final SpanContext spanContext) {
        final String operationName = "map and delegate command";
        final Tracer.SpanBuilder spanBuilder = TracingHelper
                .buildChildSpan(tracer, spanContext, operationName, getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId);
        return spanBuilder.start();
    }
}
