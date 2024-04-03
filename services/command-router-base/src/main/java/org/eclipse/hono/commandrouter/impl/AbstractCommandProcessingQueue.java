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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.command.CommandAlreadyProcessedException;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandToBeReprocessedException;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Queue with the commands currently being processed.
 * <p>
 * The final step of processing a command, forwarding it to its target, is invoked here maintaining the
 * order of the incoming commands.
 * <p>
 * Command order is maintained here across commands targeted at the same tenant/device. This is done by means
 * of keeping different (sub-)queues, referenced by a queue key derived from the command. The queue key must be the
 * same for all commands directed at a specific device of a tenant.
 * <p>
 * Subclasses may choose a queue key that combines multiple devices, keeping the overall number of queues lower than
 * the number of devices.
 *
 * @param <T> The type of command context.
 * @param <K> The type of queue key.
 */
public abstract class AbstractCommandProcessingQueue<T extends CommandContext, K> implements CommandProcessingQueue<T> {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    /**
     * The queues that contain the commands currently being processed, with the commands stored according to the
     * queue key derived from each command and in the order they were received.
     */
    private final Map<K, CommandQueue> commandQueues = new HashMap<>();
    private final Vertx vertx;

    /**
     * Creates a new CommandProcessingQueue.
     *
     * @param vertx The vert.x instance to use.
     * @throws NullPointerException if vertx is {@code null}.
     */
    public AbstractCommandProcessingQueue(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    @Override
    public final void add(final T commandContext) {
        Objects.requireNonNull(commandContext);
        final var key = getQueueKey(commandContext);
        final CommandQueue commandQueue = commandQueues
                .computeIfAbsent(key, k -> new CommandQueue(key));
        commandQueue.add(commandContext);
    }

    /**
     * Gets the queue key derived from the given command context.
     * <p>
     * The queue key must be the same for all commands directed at a specific tenant and device.
     *
     * @param commandContext The command context to derive the key from.
     * @return The queue key.
     */
    protected abstract K getQueueKey(T commandContext);

    /**
     * Gets a description of the command source (i.e. the Kafka topic partition
     * or the AMQP address) based on the queue key assigned to the command.
     * <p>
     * The returned name has to fit being put into such a log statement:
     * <pre>commands from ${CommandSourceForLog} aren't handled by this consumer anymore</pre>.
     *
     * @param queueKey The queue key.
     * @return The name.
     */
    protected abstract String getCommandSourceForLog(K queueKey);

    @Override
    public final boolean remove(final T commandContext) {
        Objects.requireNonNull(commandContext);
        final var key = getQueueKey(commandContext);
        return Optional.ofNullable(commandQueues.get(key))
                .map(commandQueue -> commandQueue.remove(commandContext))
                .orElse(false);
    }

    @Override
    public final Future<Void> applySendCommandAction(final T commandContext,
            final Supplier<Future<Void>> sendActionSupplier) {
        final var key = getQueueKey(commandContext);
        final CommandQueue commandQueue = commandQueues.get(key);
        if (commandQueue == null) {
            final String commandSourceForLog = getCommandSourceForLog(key);
            LOG.info("command won't be sent - commands from {} aren't handled by this consumer anymore [{}]",
                    commandSourceForLog, commandContext.getCommand());
            TracingHelper.logError(commandContext.getTracingSpan(),
                    "command won't be sent - commands from %s aren't handled by this consumer anymore"
                            .formatted(commandSourceForLog));
            final ServerErrorException error = new CommandToBeReprocessedException();
            commandContext.release(error);
            return Future.failedFuture(error);
        }
        return commandQueue.applySendCommandAction(commandContext, sendActionSupplier);
    }

    @Override
    public final void clear() {
        removeCommandQueueEntries(commandQueueEntry -> true);
    }

    /**
     * Removes all queue entries matching the given queue key.
     * <p>
     * All matching commands will be released.
     *
     * @param filter The filter for matching the queue entries based on the queue key.
     * @throws NullPointerException if filter is {@code null}.
     */
    protected final void removeCommandQueueEntries(final Predicate<K> filter) {
        Objects.requireNonNull(filter);
        final var commandQueuesIterator = commandQueues.entrySet().iterator();
        while (commandQueuesIterator.hasNext()) {
            final var commandQueueEntry = commandQueuesIterator.next();
            if (filter.test(commandQueueEntry.getKey())) {
                if (commandQueueEntry.getValue().isEmpty()) {
                    LOG.debug("commands from {} aren't handled here anymore; command queue is empty",
                            getCommandSourceForLog(commandQueueEntry.getKey()));
                } else {
                    LOG.info("commands from {} aren't handled here anymore but the corresponding command queue isn't empty! [queue size: {}]",
                            getCommandSourceForLog(commandQueueEntry.getKey()), commandQueueEntry.getValue().getSize());
                    commandQueueEntry.getValue().markAsUnusedAndClear();
                }
                commandQueuesIterator.remove();
            }
        }
    }

    /**
     * Keeps track of commands that are currently being processed and ensures that
     * the {@linkplain #applySendCommandAction(CommandContext, Supplier) SendCommandAction}
     * is invoked on command objects in the same order that the commands got added
     * to the queue.
     */
    public final class CommandQueue {

        private static final String KEY_COMMAND_SEND_ACTION_SUPPLIER_AND_RESULT_PROMISE = "commandSendActionSupplierAndResultPromise";

        private final Deque<T> queue = new ArrayDeque<>();
        private final K queueKey;

        /**
         * Creates a new CommandQueue.
         * @param queueKey The queue key.
         */
        CommandQueue(final K queueKey) {
            this.queueKey = queueKey;
        }

        /**
         * Adds the given command to the queue.
         *
         * @param commandContext The context containing the command to add.
         * @throws NullPointerException if commandContext is {@code null}.
         */
        public void add(final T commandContext) {
            Objects.requireNonNull(commandContext);
            queue.addLast(commandContext);
        }

        /**
         * Removes the given command from the queue.
         * <p>
         * To be used for commands for which processing resulted in an error
         * and {@link #applySendCommandAction(CommandContext, Supplier)}
         * will not be invoked.
         *
         * @param commandContext The context containing the command to add.
         * @return {@code true} if the command was removed.
         * @throws NullPointerException if commandContext is {@code null}.
         */
        public boolean remove(final T commandContext) {
            Objects.requireNonNull(commandContext);
            if (queue.remove(commandContext)) {
                sendNextCommandInQueueIfPossible();
                return true;
            }
            return false;
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
            final List<T> queueCopy = new ArrayList<>(queue);
            queue.clear();
            queueCopy.forEach(commandContext -> {
                final var actionAppliedPair = getSendActionSupplierAndResultPromise(commandContext);
                if (actionAppliedPair != null) {
                    // command is ready to be sent but waiting for the processing of an earlier entry
                    final String commandSourceForLog = getCommandSourceForLog(queueKey);
                    LOG.info("command won't be sent - commands from {} aren't handled by this consumer anymore [{}]",
                            commandSourceForLog, commandContext.getCommand());
                    TracingHelper.logError(
                            commandContext.getTracingSpan(),
                            "command won't be sent - commands from %s aren't handled by this consumer anymore"
                                    .formatted(commandSourceForLog));
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
        public Future<Void> applySendCommandAction(final T commandContext,
                final Supplier<Future<Void>> sendActionSupplier) {
            Objects.requireNonNull(commandContext);
            Objects.requireNonNull(sendActionSupplier);

            final Promise<Void> resultPromise = Promise.promise();
            if (commandContext.equals(queue.peek())) {
                // the usual case - first command added to the queue is the first command to send to the protocol adapter
                sendGivenCommandAndNextInQueueIfPossible(queue.remove(), sendActionSupplier, resultPromise, true);
            } else if (!queue.contains(commandContext)) {
                // might happen if the invoking the sendAction takes place after the Kafka partition associated
                // with this queue got unassigned and then reassigned again
                // or if processing the command has timed out and it was removed from the queue
                final ServerErrorException error;
                if (commandContext.isCompleted()) {
                    LOG.debug("command won't be sent - already processed and not in queue anymore [{}]", commandContext.getCommand());
                    error = new CommandAlreadyProcessedException();
                } else {
                    LOG.info("command won't be sent - not in queue [{}]", commandContext.getCommand());
                    TracingHelper.logError(commandContext.getTracingSpan(), "command won't be sent - not in queue");
                    error = new CommandToBeReprocessedException();
                    commandContext.release(error);
                }
                resultPromise.fail(error);
            } else {
                // given command is not next-in-line;
                // that means determining its target adapter instance has finished sooner (maybe because of fewer data-grid requests)
                // compared to a command that was received earlier
                final T next = queue.peek();
                LOG.debug("sending of command gets delayed; waiting for processing of previous command [queue size: {}; delayed {}; waiting for {}]",
                        queue.size(), commandContext.getCommand(), next.getCommand());
                commandContext.getTracingSpan()
                        .log("waiting for an earlier command to be processed first [queue size: %d; waiting for %s]"
                                .formatted(queue.size(), next.getCommand()));
                commandContext.getTracingSpan().setTag("processing_delayed", true);
                commandContext.put(KEY_COMMAND_SEND_ACTION_SUPPLIER_AND_RESULT_PROMISE, Pair
                        .of(sendActionSupplier, resultPromise));
            }
            return resultPromise.future();
        }

        private void sendGivenCommandAndNextInQueueIfPossible(
                final T commandContext,
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
                    vertx.getOrCreateContext().runOnContext(v -> sendNextCommandInQueueIfPossible());
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
                final T context) {
            return context.get(KEY_COMMAND_SEND_ACTION_SUPPLIER_AND_RESULT_PROMISE);
        }
    }
}
