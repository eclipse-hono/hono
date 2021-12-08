/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.commandrouter.impl.kafka;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommand;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommandContext;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommandResponseSender;
import org.eclipse.hono.client.command.kafka.KafkaBasedInternalCommandSender;
import org.eclipse.hono.client.impl.CommandConsumer;
import org.eclipse.hono.client.kafka.tracing.KafkaTracingHelper;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.commandrouter.CommandRouterMetrics;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.commandrouter.impl.AbstractMappingAndDelegatingCommandHandler;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Futures;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.TenantObject;

import io.micrometer.core.instrument.Timer;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * Handler for commands received at the tenant-specific topic.
 */
public class KafkaBasedMappingAndDelegatingCommandHandler extends AbstractMappingAndDelegatingCommandHandler {

    /**
     * Duration after which record processing is cancelled.
     * <p>
     * Usually, the timeout values configured for the service clients involved in command processing should define the
     * overall time limit. The timeout mechanism associated with this value here serves as a safeguard against
     * exceptional cases where the other timeouts didn't get applied. It thereby prevents command processing from
     * getting stuck, so that the command consumer doesn't get blocked.
     */
    private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(8);

    private final Vertx vertx;
    private final KafkaBasedCommandResponseSender kafkaBasedCommandResponseSender;
    private final Tracer tracer;
    private final KafkaCommandProcessingQueue commandQueue;

    /**
     * Creates a new KafkaBasedMappingAndDelegatingCommandHandler instance.
     *
     * @param vertx The Vert.x instance to use.
     * @param tenantClient The Tenant service client.
     * @param commandQueue The command queue to use for keeping track of the command processing.
     * @param commandTargetMapper The mapper component to determine the command target.
     * @param internalCommandSender The command sender to publish commands to the internal command topic.
     * @param kafkaBasedCommandResponseSender The sender used to send command responses.
     * @param metrics The component to use for reporting metrics.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public KafkaBasedMappingAndDelegatingCommandHandler(
            final Vertx vertx,
            final TenantClient tenantClient,
            final KafkaCommandProcessingQueue commandQueue,
            final CommandTargetMapper commandTargetMapper,
            final KafkaBasedInternalCommandSender internalCommandSender,
            final KafkaBasedCommandResponseSender kafkaBasedCommandResponseSender,
            final CommandRouterMetrics metrics,
            final Tracer tracer) {
        super(tenantClient, commandTargetMapper, internalCommandSender, metrics);
        this.vertx = Objects.requireNonNull(vertx);
        this.commandQueue = Objects.requireNonNull(commandQueue);
        this.kafkaBasedCommandResponseSender = Objects.requireNonNull(kafkaBasedCommandResponseSender);
        this.tracer = Objects.requireNonNull(tracer);
    }

    @Override
    protected final MessagingType getMessagingType() {
        return MessagingType.kafka;
    }

    @Override
    public Future<Void> stop() {
        commandQueue.setCurrentlyHandledPartitions(List.of());
        return super.stop();
    }

    /**
     * Delegates an incoming command to the protocol adapter instance that the target
     * device is connected to.
     * <p>
     * Determines the target gateway (if applicable) and protocol adapter instance for an incoming command
     * and delegates the command to the resulting protocol adapter instance.
     *
     * @param consumerRecord The consumer record corresponding to the command.
     * @return A future indicating the output of the operation.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public Future<Void> mapAndDelegateIncomingCommandMessage(final KafkaConsumerRecord<String, Buffer> consumerRecord) {
        Objects.requireNonNull(consumerRecord);

        final Timer.Sample timer = getMetrics().startTimer();
        final KafkaBasedCommand command;
        try {
            command = KafkaBasedCommand.from(consumerRecord);
        } catch (final IllegalArgumentException exception) {
            log.debug("command record is invalid", exception);
            return Future.failedFuture("command record is invalid");
        }

        final SpanContext spanContext = KafkaTracingHelper.extractSpanContext(tracer, consumerRecord);
        final Span currentSpan = CommandConsumer.createSpan("map and delegate command", command.getTenant(),
                command.getDeviceId(), null, tracer, spanContext);
        KafkaTracingHelper.setRecordTags(currentSpan, consumerRecord);

        final KafkaBasedCommandContext commandContext = new KafkaBasedCommandContext(
                command,
                kafkaBasedCommandResponseSender,
                currentSpan);

        command.logToSpan(currentSpan);
        if (!command.isValid()) {
            log.debug("received invalid command record [{}]", command);
            commandContext.reject("malformed command message");
            reportInvalidCommand(commandContext, timer);
            return Future.failedFuture("command is invalid");
        }
        log.trace("received valid command record [{}]", command);
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
        mapAndDelegateIncomingCommand(commandContext, timer)
                .onComplete(ar -> {
                    vertx.cancelTimer(timerId);
                    if (ar.failed()) {
                        commandQueue.remove(commandContext);
                    }
                    Futures.tryHandleResult(resultPromise, ar);
                });
        return resultPromise.future();
    }

    /**
     * Sends the given command to the internal Command and Control API endpoint provided by protocol adapters.
     * <p>
     * Overridden to make sure that the order in which commands are sent via this method is the same in which
     * commands have been received (with respect to the topic partition of the command record).
     * This means that the actual sending of the command may get delayed here.
     *
     * @param commandContext Context of the command to send.
     * @param targetAdapterInstanceId The target protocol adapter instance id.
     * @param tenantObject The tenant of the command target device.
     * @param timer The timer indicating the amount of time used for processing the command message.
     * @return A future indicating the output of the operation.
     */
    @Override
    protected Future<Void> sendCommand(final CommandContext commandContext, final String targetAdapterInstanceId,
            final TenantObject tenantObject, final Timer.Sample timer) {
        final KafkaBasedCommandContext cmdContext = (KafkaBasedCommandContext) commandContext;
        return commandQueue.applySendCommandAction(cmdContext,
                () -> super.sendCommand(commandContext, targetAdapterInstanceId, tenantObject, timer));
    }

}
