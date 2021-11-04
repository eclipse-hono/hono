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

import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.kafka.common.TopicPartition;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.command.CommandToBeReprocessedException;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommandContext;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * Queue with the commands currently being processed, maintaining FIFO semantics.
 * That means the last step of command processing, sending the command on the internal command topic, is
 * invoked with respect to the order in which commands were originally received.
 */
public class KafkaCommandProcessingQueue {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaCommandProcessingQueue.class);

    /**
     * The queues that contain the commands currently being processed, with the commands stored according to the
     * TopicPartition they were received from and in the order they were received.
     */
    private final Map<TopicPartition, TopicPartitionCommandQueue> commandQueues = new HashMap<>();
    private final Context vertxContext;

    /**
     * Creates a new KafkaCommandProcessingQueue.
     * @param vertxContext The vert.x context to run tasks on asynchronously.
     * @throws NullPointerException if vertxContext is {@code null}.
     */
    public KafkaCommandProcessingQueue(final Context vertxContext) {
        this.vertxContext = Objects.requireNonNull(vertxContext);
    }

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
            LOG.info("command won't be sent - commands received from partition [{}] aren't handled by this consumer anymore [{}]",
                    topicPartition, commandContext.getCommand());
            TracingHelper.logError(commandContext.getTracingSpan(), String.format(
                    "command won't be sent - commands received from partition [%s] aren't handled by this consumer anymore",
                    topicPartition));
            final ServerErrorException error = new CommandToBeReprocessedException();
            commandContext.release(error);
            return Future.failedFuture(error);
        }
        return commandQueue.applySendCommandAction(commandContext, sendActionSupplier);
    }

    /**
     * Informs this queue about changes to the partitions that the command consumer is receiving records from.
     * @param partitions The newly assigned partitions.
     * @throws NullPointerException if partitions is {@code null}.
     */
    public void setCurrentlyHandledPartitions(final Collection<TopicPartition> partitions) {
        Objects.requireNonNull(partitions);
        removeCommandQueueEntries(commandQueueEntry -> !partitions.contains(commandQueueEntry.getKey()));
    }

    /**
     * Informs this queue about the partitions that the command consumer is not receiving records from any more.
     * @param partitions The revoked partitions.
     * @throws NullPointerException if partitions is {@code null}.
     */
    public void setRevokedPartitions(final Collection<TopicPartition> partitions) {
        Objects.requireNonNull(partitions);
        removeCommandQueueEntries(commandQueueEntry -> partitions.contains(commandQueueEntry.getKey()));
    }

    private void removeCommandQueueEntries(final Predicate<Map.Entry<TopicPartition, TopicPartitionCommandQueue>> filter) {
        final var commandQueuesIterator = commandQueues.entrySet().iterator();
        while (commandQueuesIterator.hasNext()) {
            final var commandQueueEntry = commandQueuesIterator.next();
            if (filter.test(commandQueueEntry)) {
                if (commandQueueEntry.getValue().isEmpty()) {
                    LOG.debug("partition [{}] isn't being handled anymore; command queue is empty", commandQueueEntry.getKey());
                } else {
                    LOG.info("partition [{}] isn't being handled anymore but its command queue isn't empty! [queue size: {}]",
                            commandQueueEntry.getKey(), commandQueueEntry.getValue().getSize());
                    commandQueueEntry.getValue().markAsUnusedAndClear();
                }
                commandQueuesIterator.remove();
            }
        }
    }

    /**
     * Keeps track of commands that are currently being processed and ensures that
     * the "SendCommandAction" provided via {@link #applySendCommandAction(KafkaBasedCommandContext, Supplier)}
     * is invoked on command objects in the same order that the commands got added
     * to the queue.
     */
    class TopicPartitionCommandQueue {

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
         * Checks whether this queue is empty.
         * @return {@code true} if this queue is empty.
         */
        public boolean isEmpty() {
            return queue.isEmpty();
        }

        /**
         * Gets the size of this queue.
         * @return The queue size.
         */
        public int getSize() {
            return queue.size();
        }

        /**
         * Releases any contained commands waiting to be sent and clears the queue.
         */
        public void markAsUnusedAndClear() {
            final LinkedList<KafkaBasedCommandContext> queueCopy = new LinkedList<>(queue);
            queue.clear();
            queueCopy.forEach(commandContext -> {
                final var actionAppliedPair = getSendActionSupplierAndResultPromise(commandContext);
                if (actionAppliedPair != null) {
                    // command is ready to be sent but waiting for the processing of an earlier entry
                    LOG.info("command won't be sent - its partition isn't being handled anymore [{}]", commandContext.getCommand());
                    TracingHelper.logError(commandContext.getTracingSpan(), "command won't be sent - its partition isn't being handled anymore");
                    final ServerErrorException error = new CommandToBeReprocessedException();
                    commandContext.release(error);
                    actionAppliedPair.two().fail(error);
                } // in the else case let the applySendCommandAction() method release the command eventually
            });
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
                sendGivenCommandAndNextInQueueIfPossible(queue.remove(), sendActionSupplier, resultPromise, true);
            } else if (!queue.contains(commandContext)) {
                // might happen if the invoking the sendAction takes place after the partition got unassigned and then reassigned again
                LOG.info("command won't be sent - not in queue [{}]", commandContext.getCommand());
                TracingHelper.logError(commandContext.getTracingSpan(), "command won't be sent - not in queue");
                final ServerErrorException error = new CommandToBeReprocessedException();
                commandContext.release(error);
                resultPromise.fail(error);
            } else {
                // given command is not next-in-line;
                // that means determining its target adapter instance has finished sooner (maybe because of fewer data-grid requests)
                // compared to a command that was received earlier
                LOG.debug("sending of command with offset {} gets delayed; waiting for processing of offset {} [queue size: {}; delayed {}]",
                        getRecordOffset(commandContext), getRecordOffset(queue.peek()), queue.size(), commandContext.getCommand());
                commandContext.getTracingSpan()
                        .log(String.format("waiting for an earlier command with offset %d to be processed first [queue size: %d]",
                                getRecordOffset(queue.peek()), queue.size()));
                commandContext.getTracingSpan().setTag("processing_delayed", true);
                commandContext.put(KEY_COMMAND_SEND_ACTION_SUPPLIER_AND_RESULT_PROMISE, Pair
                        .of(sendActionSupplier, resultPromise));
            }
            return resultPromise.future();
        }

        private void sendGivenCommandAndNextInQueueIfPossible(
                final KafkaBasedCommandContext commandContext,
                final Supplier<Future<Void>> sendActionSupplier,
                final Promise<Void> sendActionCompletedPromise,
                final boolean completedPromiseJustCreated) {

            LOG.trace("apply send action on [{}]", commandContext.getCommand());
            final Future<Void> sendActionFuture = sendActionSupplier.get();
            sendActionFuture.onComplete(sendActionCompletedPromise);

            if (!queue.isEmpty()) {
                if (sendActionFuture.isComplete() && completedPromiseJustCreated) {
                    // send action directly finished the result future (without any vert.x decoupling) and the completedPromise can't have any handlers yet;
                    // trigger next-command handling asynchronously, so that a sendActionFuture handler can first be set and run, before handling next commands
                    vertxContext.runOnContext(v -> sendNextCommandInQueueIfPossible());
                } else {
                    sendNextCommandInQueueIfPossible();
                }
            }
        }

        private void sendNextCommandInQueueIfPossible() {
            Optional.ofNullable(queue.peek())
                    // sendActionSupplierAndResultPromise not null means command is ready to be sent
                    .map(this::getSendActionSupplierAndResultPromise)
                    .ifPresent(pair -> sendGivenCommandAndNextInQueueIfPossible(queue.remove(), pair.one(), pair.two(), false));
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
