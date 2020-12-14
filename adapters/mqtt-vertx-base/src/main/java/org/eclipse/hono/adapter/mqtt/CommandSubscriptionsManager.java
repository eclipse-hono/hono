/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.hono.adapter.client.command.CommandConsumer;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * A class that tracks command subscriptions, unsubscriptions and handles PUBACKs.
 *
 * @param <T> The type of configuration properties the mqtt adapter supports/requires.
 */
public final class CommandSubscriptionsManager<T extends MqttProtocolAdapterProperties> {
    private static final Logger LOG = LoggerFactory.getLogger(CommandSubscriptionsManager.class);
    /**
     * Map of the current subscriptions. Key is the topic name.
     */
    private final Map<String, Pair<CommandSubscription, CommandConsumer>> subscriptions = new ConcurrentHashMap<>();
    /**
     * Map of the requests waiting for an acknowledgement. Key is the command message id.
     */
    private final Map<Integer, PendingCommandRequest> waitingForAcknowledgement = new ConcurrentHashMap<>();
    private final Vertx vertx;
    private final T config;

    /**
     * Creates a new CommandSubscriptionsManager instance.
     *
     * @param vertx The Vert.x instance to execute the client on.
     * @param config The configuration properties to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public CommandSubscriptionsManager(final Vertx vertx, final T config) {
        this.vertx = Objects.requireNonNull(vertx);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Invoked when a device sends an MQTT <em>PUBACK</em> packet.
     *
     * @param msgId The message/packet id of the command published with QoS 1.
     * @throws NullPointerException if msgId is {@code null}.
     */
    public void handlePubAck(final Integer msgId) {
        Objects.requireNonNull(msgId);
        LOG.trace("acknowledgement received for command sent to device [packet-id: {}]", msgId);
        Optional.ofNullable(removeFromWaitingForAcknowledgement(msgId))
                .ifPresentOrElse(pendingCommandRequest -> {
                    if (pendingCommandRequest.timerId != null) {
                        cancelTimer(pendingCommandRequest.timerId);
                    }
                    pendingCommandRequest.onAckHandler.handle(msgId);
                }, () -> LOG.debug("no active command request found for received acknowledgement [packet-id: {}]", msgId));
    }

    /**
     * Registers handlers to be invoked when the command message with the given id is either acknowledged or a timeout
     * occurs.
     *
     * @param msgId The id of the command (message) that has been published.
     * @param onAckHandler Handler to invoke when the device has acknowledged the command.
     * @param onAckTimeoutHandler Handler to invoke when there is a timeout waiting for the acknowledgement from the
     *            device.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public void addToWaitingForAcknowledgement(final Integer msgId, final Handler<Integer> onAckHandler,
            final Handler<Void> onAckTimeoutHandler) {

        Objects.requireNonNull(msgId);
        Objects.requireNonNull(onAckHandler);
        Objects.requireNonNull(onAckTimeoutHandler);

        waitingForAcknowledgement.put(msgId,
                PendingCommandRequest.from(startTimer(msgId), onAckHandler, onAckTimeoutHandler));
    }

    /**
     * Removes the entry from the waitingForAcknowledgement map for the given msgId.
     *
     * @param msgId The id of the command (message) that has been published.
     * @return The PendingCommandRequest object containing timer-id, tenantObject, commandSubscription and 
     *         commandContext for the given msgId.
     */
    private PendingCommandRequest removeFromWaitingForAcknowledgement(
            final Integer msgId) {
        return waitingForAcknowledgement.remove(msgId);
    }

    /**
     * Stores the command subscription along with the command consumer.
     *
     * @param subscription The device's command subscription.
     * @param commandConsumer A client for consuming messages.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public void addSubscription(final CommandSubscription subscription,
            final CommandConsumer commandConsumer) {
        Objects.requireNonNull(subscription);
        Objects.requireNonNull(commandConsumer);
        subscriptions.put(subscription.getTopic(), Pair.of(subscription, commandConsumer));
    }

    /**
     * Removes the subscription entry for the given topic.
     *
     * @param topic The topic string to unsubscribe.
     * @param span The span to log to if no subscription entry is found.
     * @return A succeeded future with the removed subscription and its associated command consumer or a failed future
     *         if no subscription entry was found for the given topic.
     * @throws NullPointerException if topic or span is {@code null}.
     **/
    public Future<Pair<CommandSubscription, CommandConsumer>> removeSubscription(final String topic,
            final Span span) {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(span);

        final Pair<CommandSubscription, CommandConsumer> removed = subscriptions.remove(topic);
        if (removed != null) {
            return Future.succeededFuture(removed);
        } else {
            final String message = String.format("cannot remove subscription; none registered for topic [%s]", topic);
            LOG.debug(message);
            TracingHelper.logError(span, message);
            return Future.failedFuture(message);
        }
    }

    /**
     * Removes all the subscription entries.
     *
     * @param onSubscriptionRemovedFunction The function to be invoked on each removed subscription and its associated
     *            command consumer.
     * @param span The span to track the operation.
     * @return A composite future containing a future for each subscription entry. Each contained future tracks the
     *         application of the given function on the removed subscription entry.
     * @throws NullPointerException if onSubscriptionRemovedFunction or span is {@code null}.
     **/
    public CompositeFuture removeAllSubscriptions(
            final Function<Pair<CommandSubscription, CommandConsumer>, Future<Void>> onSubscriptionRemovedFunction,
            final Span span) {

        Objects.requireNonNull(onSubscriptionRemovedFunction);
        Objects.requireNonNull(span);

        @SuppressWarnings("rawtypes")
        final List<Future> removalFutures = subscriptions.keySet().stream()
                .map(topic -> removeSubscription(topic, span).compose(onSubscriptionRemovedFunction))
                .collect(Collectors.toList());
        return CompositeFuture.join(removalFutures);
    }

    private Long startTimer(final Integer msgId) {
        if (config.getEffectiveSendMessageToDeviceTimeout() < 1) {
            return null;
        }

        return vertx.setTimer(config.getEffectiveSendMessageToDeviceTimeout(), timerId -> {
            Optional.ofNullable(removeFromWaitingForAcknowledgement(msgId))
                    .ifPresent(value -> value.onAckTimeoutHandler.handle(null));
        });
    }

    private void cancelTimer(final Long timerId) {
        vertx.cancelTimer(timerId);
        LOG.trace("canceled Timer [timer-id: {}}", timerId);
    }

    /**
     * A class to facilitate storing of information in connection with the pending command requests.
     * The pending command requests are tracked using a map in the enclosing class {@link CommandSubscriptionsManager}.
     */
    private static class PendingCommandRequest {

        private final Long timerId;
        private final Handler<Integer> onAckHandler;
        private final Handler<Void> onAckTimeoutHandler;

        private PendingCommandRequest(final Long timerId, final Handler<Integer> onAckHandler,
                final Handler<Void> onAckTimeoutHandler) {
            this.timerId = timerId;
            this.onAckHandler = Objects.requireNonNull(onAckHandler);
            this.onAckTimeoutHandler = Objects.requireNonNull(onAckTimeoutHandler);
        }

        /**
         * Creates a new PendingCommandRequest instance.
         *
         * @param timerId The unique ID of the timer.
         * @param onAckHandler Handler to invoke when the device has acknowledged the command.
         * @param onAckTimeoutHandler Handler to invoke when there is a timeout waiting for the acknowledgement from the
         *            device.
         * @throws NullPointerException if any of the parameters is {@code null}.
         */        
        private static PendingCommandRequest from(final Long timerId, final Handler<Integer> onAckHandler,
                final Handler<Void> onAckTimeoutHandler) {
            return new PendingCommandRequest(timerId, onAckHandler, onAckTimeoutHandler);
        }
    }
}
