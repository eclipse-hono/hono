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

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.kafka.common.TopicPartition;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedCommandContext;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * Queue with the commands currently being processed, maintaining FIFO semantics and keeping track of partition offsets
 * of the last fully processed commands per partition.
 */
public class KafkaCommandProcessingQueue {

    /**
     * The queues that contain the commands currently being processed, with the commands stored according to the
     * TopicPartition they were received from and in the order they were received.
     */
    private final Map<TopicPartition, TopicPartitionCommandQueue> commandQueues = new HashMap<>();

    /**
     * Adds the command represented by the given command context.
     *
     * @param commandContext The context containing the command to add.
     * @throws NullPointerException if commandContext is {@code null}.
     */
    public void add(final KafkaBasedCommandContext commandContext) {
        Objects.requireNonNull(commandContext);
        final KafkaConsumerRecord<String, Buffer> record = commandContext.getCommand().getRecord();
        final TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
        final TopicPartitionCommandQueue commandQueue = commandQueues
                .computeIfAbsent(topicPartition, k -> new TopicPartitionCommandQueue());
        commandQueue.add(commandContext);
    }

    /**
     * Removes the command represented by the given command context.
     * <p>
     * To be used for commands for which processing resulted in an error
     * and {@link #applySendCommandAction(KafkaBasedCommandContext, Supplier)}
     * will not be invoked.
     *
     * @param commandContext The context containing the command to remove.
     */
    public void remove(final KafkaBasedCommandContext commandContext) {
        Objects.requireNonNull(commandContext);
        final KafkaConsumerRecord<String, Buffer> record = commandContext.getCommand().getRecord();
        final TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
        Optional.ofNullable(commandQueues.get(topicPartition))
                .ifPresent(commandQueue -> commandQueue.remove(commandContext));
    }

    /**
     * Either invokes the given sendAction directly if it is next-in-line or makes sure the action is
     * invoked at a later point in time according to the order in which commands were originally received.
     *
     * @param commandContext The context of the command to apply the given sendAction for.
     * @param sendActionSupplier The Supplier for the action to send the given command to the internal Command and
     *                           Control API endpoint provided by protocol adapters.
     * @return A future indicating the outcome of sending the command message. If the given command is
     *         not in the corresponding queue, a failed future will be returned and the command context is released.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public Future<Void> applySendCommandAction(final KafkaBasedCommandContext commandContext,
            final Supplier<Future<Void>> sendActionSupplier) {
        final TopicPartition topicPartition = new TopicPartition(
                commandContext.getCommand().getRecord().topic(), commandContext.getCommand().getRecord().partition());
        final TopicPartitionCommandQueue commandQueue = commandQueues.get(topicPartition);
        if (commandQueue == null) {
            return Future.failedFuture(new IllegalStateException("command can't be sent - corresponding queue does not exist"));
        }
        return commandQueue.applySendCommandAction(commandContext, sendActionSupplier);
    }

    /**
     * Keeps track of commands that are currently being processed and ensures that
     * the "SendCommandAction" provided via {@link #applySendCommandAction(KafkaBasedCommandContext, Supplier)}
     * is invoked on command objects in the same order that the commands got added
     * to the queue.
     */
    static class TopicPartitionCommandQueue {

        private static final Logger LOG = LoggerFactory.getLogger(TopicPartitionCommandQueue.class);

        private static final String KEY_COMMAND_SEND_ACTION_SUPPLIER_AND_RESULT_PROMISE = "commandSendActionSupplierAndResultPromise";

        private final Deque<KafkaBasedCommandContext> queue = new LinkedList<>();

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
            if (commandContext.equals(queue.peek())) {
                // the usual case - first command added to the queue is the first command to send to the protocol adapter
                sendGivenCommandAndNextInQueueIfPossible(queue.remove(), sendActionSupplier, resultPromise);
            } else if (!queue.contains(commandContext)) {
                LOG.warn("command can't be sent - not in queue [{}]", commandContext.getCommand());
                TracingHelper.logError(commandContext.getTracingSpan(), "command can't be sent - not in queue");
                commandContext.release();
                resultPromise.fail(new IllegalStateException("command can't be sent - not in queue"));
            } else {
                // given command is not next-in-line;
                // that means determining its target adapter instance has finished sooner (maybe because of fewer data-grid requests)
                // compared to a command that was received earlier
                LOG.debug("sending of command with offset {} delayed waiting for processing of offset {} [delayed {}]",
                        getRecordOffset(commandContext), getRecordOffset(queue.peek()), commandContext.getCommand());
                commandContext.getTracingSpan()
                        .log(String.format("waiting for an earlier command with offset %d to be processed first",
                                getRecordOffset(queue.peek())));
                commandContext.put(KEY_COMMAND_SEND_ACTION_SUPPLIER_AND_RESULT_PROMISE, Pair
                        .of(sendActionSupplier, resultPromise));
            }
            return resultPromise.future();
        }

        private void sendGivenCommandAndNextInQueueIfPossible(final KafkaBasedCommandContext commandContext,
                final Supplier<Future<Void>> sendActionSupplier, final Promise<Void> sendActionCompletedPromise) {
            LOG.trace("sending [{}]", commandContext.getCommand());
            // TODO use the supplier result future to keep track of the latest fully processed command record with regard to its partition offset
            sendActionSupplier.get()
                    .onComplete(sendActionCompletedPromise);
            sendNextCommandInQueueIfPossible();
        }

        private void sendNextCommandInQueueIfPossible() {
            Optional.ofNullable(queue.peek())
                    // sendActionSupplierAndResultPromise not null means command is ready to be sent
                    .map(this::getSendActionSupplierAndResultPromise)
                    .ifPresent(pair -> sendGivenCommandAndNextInQueueIfPossible(queue.remove(), pair.one(), pair.two()));
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
