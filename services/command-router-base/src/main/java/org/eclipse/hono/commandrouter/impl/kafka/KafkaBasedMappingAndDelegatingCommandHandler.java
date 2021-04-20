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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Supplier;

import org.apache.kafka.common.TopicPartition;
import org.eclipse.hono.adapter.client.command.CommandContext;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedCommand;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedCommandContext;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedInternalCommandSender;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.client.impl.CommandConsumer;
import org.eclipse.hono.client.kafka.tracing.KafkaTracingHelper;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.commandrouter.impl.AbstractMappingAndDelegatingCommandHandler;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Pair;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * Handler for commands received at the tenant-specific topic.
 */
public class KafkaBasedMappingAndDelegatingCommandHandler extends AbstractMappingAndDelegatingCommandHandler {

    private static final String KEY_COMMAND_SEND_ACTION_SUPPLIER_AND_RESULT_PROMISE = "commandSendActionSupplierAndResultPromise";
    /**
     * Queue in which the received commands per TopicPartition are kept so that they can
     * be delegated in the same order they were received.
     */
    private final Map<TopicPartition, CommandQueue> commandQueues = new HashMap<>();
    private final Tracer tracer;

    /**
     * Creates a new KafkaBasedMappingAndDelegatingCommandHandler instance.
     *
     * @param tenantClient The Tenant service client.
     * @param commandTargetMapper The mapper component to determine the command target.
     * @param internalCommandSender The command sender to publish commands to the internal command topic.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public KafkaBasedMappingAndDelegatingCommandHandler(
            final TenantClient tenantClient,
            final CommandTargetMapper commandTargetMapper,
            final KafkaBasedInternalCommandSender internalCommandSender,
            final Tracer tracer) {
        super(tenantClient, commandTargetMapper, internalCommandSender);
        this.tracer = Objects.requireNonNull(tracer);
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
        final KafkaBasedCommandContext commandContext = new KafkaBasedCommandContext(command, currentSpan);

        command.logToSpan(currentSpan);
        if (!command.isValid()) {
            log.debug("received invalid command record [{}]", command);
            commandContext.reject("malformed command message");
            return Future.failedFuture("command is invalid");
        }
        log.trace("received valid command record [{}]", command);
        final TopicPartition topicPartition = new TopicPartition(consumerRecord.topic(), consumerRecord.partition());
        final CommandQueue commandQueue = commandQueues
                .computeIfAbsent(topicPartition, k -> new CommandQueue());
        commandQueue.add(commandContext);

        return mapAndDelegateIncomingCommand(commandContext)
                .onFailure(thr -> commandQueue.remove(commandContext));
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
     * @return A future indicating the output of the operation.
     */
    @Override
    protected Future<Void> sendCommand(final CommandContext commandContext, final String targetAdapterInstanceId) {
        final KafkaBasedCommandContext cmdContext = (KafkaBasedCommandContext) commandContext;
        final TopicPartition topicPartition = new TopicPartition(
                cmdContext.getCommand().getRecord().topic(), cmdContext.getCommand().getRecord().partition());
        final CommandQueue commandQueue = commandQueues.get(topicPartition);

        return commandQueue.applySendCommandAction(cmdContext,
                () -> super.sendCommand(commandContext, targetAdapterInstanceId));
    }

    /**
     * Keeps track of commands that are currently being processed and ensures that
     * the "SendCommandAction" provided via {@link #applySendCommandAction(KafkaBasedCommandContext, Supplier)}
     * is invoked on command objects in the same order that the commands got added
     * to the queue.
     */
    class CommandQueue {
        private final Queue<KafkaBasedCommandContext> queue = new LinkedList<>();

        /**
         * Adds the given command to the queue.
         *
         * @param commandContext The context containing the command to add.
         * @throws NullPointerException if commandContext is {@code null}.
         */
        public void add(final KafkaBasedCommandContext commandContext) {
            Objects.requireNonNull(commandContext);
            queue.add(commandContext);
        }

        /**
         * Removes the given command from the queue.
         * <p>
         * To be used for commands for which processing resulted in an error
         * and {@link #applySendCommandAction(KafkaBasedCommandContext, Supplier)}
         * will not be invoked.
         *
         * @param commandContext The context containing the command to add.
         * @throws NullPointerException if commandContext is {@code null}.
         */
        public void remove(final KafkaBasedCommandContext commandContext) {
            Objects.requireNonNull(commandContext);
            if (queue.remove(commandContext)) {
                sendNextCommandInQueueIfPossible();
            }
        }

        /**
         * Either invokes the given sendAction directly if it is next-in-line or makes sure the action is
         * invoked at a later point in time according to the order in which commands were originally received.
         *
         * @param commandContext The context of the command to apply the given sendAction for.
         * @param sendActionSupplier The Supplier for the action to send the given command to the internal Command and
         *                           Control API endpoint provided by protocol adapters.
         * @return A future indicating the outcome of sending the command message. If the given command is
         *         not in the queue, a failed future will be returned and the command context is released.
         * @throws NullPointerException if any of the parameters is {@code null}.
         */
        public Future<Void> applySendCommandAction(final KafkaBasedCommandContext commandContext,
                final Supplier<Future<Void>> sendActionSupplier) {
            Objects.requireNonNull(commandContext);
            Objects.requireNonNull(sendActionSupplier);

            final Promise<Void> resultPromise = Promise.promise();
            final Pair<Supplier<Future<Void>>, Promise<Void>> sendActionSupplierAndResultPromise = Pair
                    .of(sendActionSupplier, resultPromise);
            if (commandContext.equals(queue.peek())) {
                sendGivenCommandAndNextInQueueIfPossible(sendActionSupplierAndResultPromise);
            } else if (!queue.contains(commandContext)) {
                log.warn("command can't be sent - not in queue [{}]", commandContext.getCommand());
                TracingHelper.logError(commandContext.getTracingSpan(), "command can't be sent - not in queue");
                commandContext.release();
                resultPromise.fail(new IllegalStateException("command can't be sent - not in queue"));
            } else {
                // given command is not next-in-line;
                // that means determining its target adapter instance has finished sooner (maybe because of fewer data-grid requests)
                // compared to a command that was received earlier
                log.debug("sending of command with offset {} delayed waiting for processing of offset {} [delayed {}]",
                        getRecordOffset(commandContext), getRecordOffset(queue.peek()), commandContext.getCommand());
                commandContext.getTracingSpan().log(String.format("waiting for an earlier command with offset %d to be processed first",
                        getRecordOffset(queue.peek())));
                commandContext.put(KEY_COMMAND_SEND_ACTION_SUPPLIER_AND_RESULT_PROMISE, sendActionSupplierAndResultPromise);
            }
            return resultPromise.future();
        }

        private void sendGivenCommandAndNextInQueueIfPossible(
                final Pair<Supplier<Future<Void>>, Promise<Void>> sendActionSupplierAndResultPromise) {
            final KafkaBasedCommandContext commandContext = queue.remove();
            log.trace("sending [{}]", commandContext.getCommand());
            // TODO use the supplier result future to keep track of the latest fully processed command record with regard to its partition offset
            sendActionSupplierAndResultPromise.one().get()
                    .onComplete(sendActionSupplierAndResultPromise.two());
            sendNextCommandInQueueIfPossible();
        }

        private void sendNextCommandInQueueIfPossible() {
            Optional.ofNullable(queue.peek())
                    .map(this::getSendActionSupplierAndResultPromise)
                    .ifPresent(this::sendGivenCommandAndNextInQueueIfPossible);
        }

        private Pair<Supplier<Future<Void>>, Promise<Void>> getSendActionSupplierAndResultPromise(
                final KafkaBasedCommandContext context) {
            return context.get(KEY_COMMAND_SEND_ACTION_SUPPLIER_AND_RESULT_PROMISE);
        }

        private long getRecordOffset(final KafkaBasedCommandContext context) {
            if (context == null) {
                return -1;
            }
            return context.getCommand().getRecord().offset();
        }

    }
}
