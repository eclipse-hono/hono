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
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.util.TriTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
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
    private final Map<String, TriTuple<CommandSubscription, ProtocolAdapterCommandConsumer, Object>> subscriptions = new ConcurrentHashMap<>();
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
     * @param msgId The msgId of the command published with QoS 1.
     * @throws NullPointerException if msgId is {@code null}.
     */
    public void handlePubAck(final Integer msgId) {
        Objects.requireNonNull(msgId);
        LOG.trace("Acknowledgement received for command [Msg-id: {}] that has been sent to device", msgId);
        Optional.ofNullable(removeFromWaitingForAcknowledgement(msgId)).ifPresent(value -> {
            cancelTimer(value.timerId);
            value.onAckHandler.handle(msgId);
        });
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
            final ProtocolAdapterCommandConsumer commandConsumer) {
        Objects.requireNonNull(subscription);
        Objects.requireNonNull(commandConsumer);
        subscriptions.put(subscription.getTopic(), TriTuple.of(subscription, commandConsumer, null));
    }

    /**
     * Closes the command consumer and removes the subscription entry for the given topic.
     *
     * @param topic The topic string to unsubscribe.
     * @param onConsumerRemovedFunction The function to be invoked if not {@code null} during removal of a subscription.
     *                                  The first parameter is the tenant id, the second parameter the device id.
     *                                  To be returned is a future indicating the outcome of the function.
     * @param spanContext The span context (may be {@code null}).
     * @throws NullPointerException if topic is {@code null}.
     * @return A future indicating the outcome of the operation.
     **/
    public Future<Void> removeSubscription(final String topic,
            final BiFunction<String, String, Future<Void>> onConsumerRemovedFunction, final SpanContext spanContext) {
        Objects.requireNonNull(topic);

        final TriTuple<CommandSubscription, ProtocolAdapterCommandConsumer, Object> removed = subscriptions.remove(topic);
        if (removed != null) {
            final CommandSubscription subscription = removed.one();
            final Future<Void> functionFuture = onConsumerRemovedFunction != null
                    ? onConsumerRemovedFunction.apply(subscription.getTenant(), subscription.getDeviceId())
                    : Future.succeededFuture();
            final ProtocolAdapterCommandConsumer commandConsumer = removed.two();
            return CompositeFuture
                    .join(functionFuture, closeCommandConsumer(subscription, commandConsumer, spanContext)).mapEmpty();
        } else {
            LOG.debug("Cannot remove subscription; none registered for topic [{}]", topic);
            return Future.failedFuture(String.format("Cannot remove subscription; none registered for topic [%s]", topic));
        }
    }

    /**
     * Closes the command consumers and removes all the subscription entries.
     *
     * @param onConsumerRemovedFunction The function to be invoked if not {@code null} during removal of a subscription.
     *                                  The first parameter is the tenant id, the second parameter the device id.
     *                                  To be returned is a future indicating the outcome of the function.
     * @param spanContext The span context (may be {@code null}).
     * @return A future indicating the outcome of the operation.            
     **/
    public CompositeFuture removeAllSubscriptions(
            final BiFunction<String, String, Future<Void>> onConsumerRemovedFunction, final SpanContext spanContext) {
        @SuppressWarnings("rawtypes")
        final List<Future> removalFutures = subscriptions.keySet().stream()
                .map(topic -> removeSubscription(topic, onConsumerRemovedFunction, spanContext)).collect(Collectors.toList());
        return CompositeFuture.join(removalFutures);
    }

    /**
     * Stores the command subscription along with the command consumer.
     *
     * @param subscription The device's command subscription.
     * @param commandConsumer A client for consuming messages.
     * @param spanContext The span context (may be {@code null}).
     * @return A future indicating the outcome of the operation.
     */
    private Future<Void> closeCommandConsumer(final CommandSubscription subscription,
            final ProtocolAdapterCommandConsumer commandConsumer, final SpanContext spanContext) {
        return commandConsumer.close(spanContext)
                .map(v -> {
                    LOG.trace("Command consumer closed [tenant-it: {}, device-id :{}]", subscription.getTenant(),
                            subscription.getDeviceId());
                    return v;
                }).recover(thr -> {
                    LOG.debug("Error closing command consumer [tenant-it: {}, device-id :{}]",
                            subscription.getTenant(), subscription.getDeviceId(), thr);
                    return Future.failedFuture(thr);
                });
    }

    private long startTimer(final Integer msgId) {

        return vertx.setTimer(config.getCommandAckTimeout(), timerId -> {
            Optional.ofNullable(removeFromWaitingForAcknowledgement(msgId))
                    .ifPresent(value -> value.onAckTimeoutHandler.handle(null));
        });
    }

    private void cancelTimer(final Long timerId) {
        vertx.cancelTimer(timerId);
        LOG.trace("Canceled Timer [timer-id: {}}", timerId);
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
            this.timerId = Objects.requireNonNull(timerId);
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
