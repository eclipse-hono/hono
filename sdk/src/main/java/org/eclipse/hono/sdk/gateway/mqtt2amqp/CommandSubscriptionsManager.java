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

package org.eclipse.hono.sdk.gateway.mqtt2amqp;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.eclipse.hono.client.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * A class that tracks command subscriptions, unsubscriptions and handles PUBACKs.
 */
final class CommandSubscriptionsManager {

    private static final Logger LOG = LoggerFactory.getLogger(CommandSubscriptionsManager.class);
    /**
     * Map of the current subscriptions. Key is the topic name.
     */
    private final Map<String, CommandSubscription> subscriptions = new ConcurrentHashMap<>();
    /**
     * Map of the requests waiting for an acknowledgement. Key is the command message id.
     */
    private final Map<Integer, PendingCommandRequest> waitingForAcknowledgement = new ConcurrentHashMap<>();
    private final Vertx vertx;
    private final MqttProtocolGatewayConfig config;
    private Future<MessageConsumer> commandConsumer;

    /**
     * Creates a new CommandSubscriptionsManager instance.
     *
     * @param vertx The Vert.x instance to execute the client on.
     * @param config The configuration properties to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    CommandSubscriptionsManager(final Vertx vertx, final MqttProtocolGatewayConfig config) {
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
        LOG.trace("Acknowledgement received for command [Msg-id: {}] that has been sent to device.", msgId);
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
                new PendingCommandRequest(startTimer(msgId), onAckHandler, onAckTimeoutHandler));
    }

    /**
     * Removes the entry from the waitingForAcknowledgement map for the given msgId.
     *
     * @param msgId The id of the command (message) that has been published.
     * @return The PendingCommandRequest object containing timer-id and event handlers.
     */
    private PendingCommandRequest removeFromWaitingForAcknowledgement(final Integer msgId) {
        return waitingForAcknowledgement.remove(msgId);
    }

    /**
     * Stores the command subscription and creates a command consumer for it. Multiple subscription share the same
     * consumer.
     *
     * @param subscription The device's command subscription.
     * @param commandConsumerSupplier A function to create a client for consuming messages.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @return The QoS of the subscription or {@link MqttQoS#FAILURE} if the consumer could not be opened.
     */
    public Future<MqttQoS> addSubscription(final CommandSubscription subscription,
            final Supplier<Future<MessageConsumer>> commandConsumerSupplier) {

        Objects.requireNonNull(subscription);
        Objects.requireNonNull(commandConsumerSupplier);

        LOG.trace("Adding subscription for topic filter [{}]", subscription.getTopicFilter());

        if (commandConsumer == null) {
            commandConsumer = commandConsumerSupplier.get();
        }

        return commandConsumer
                .map(messageConsumer -> {
                    subscriptions.put(subscription.getTopicFilter(), subscription);
                    LOG.debug("Added subscription for topic filter [{}] with QoS {}", subscription.getTopicFilter(),
                            subscription.getQos());
                    return subscription.getQos();
                })
                .otherwise(MqttQoS.FAILURE);
    }

    /**
     * Removes the subscription entry for the given topic filter. If this was the only subscription, the command
     * consumer will be closed.
     *
     * @param topicFilter The topic filter string to unsubscribe.
     * @throws NullPointerException if the topic filter is {@code null}.
     */
    public void removeSubscription(final String topicFilter) {
        Objects.requireNonNull(topicFilter);

        final CommandSubscription value = subscriptions.remove(topicFilter);
        if (value != null) {
            LOG.debug("Remove subscription for topic filter [{}]", topicFilter);

            if (subscriptions.isEmpty() && commandConsumer != null && commandConsumer.succeeded()) {
                closeCommandConsumer(commandConsumer.result());
            }
        } else {
            LOG.debug("Cannot remove subscription; none registered for topic filter [{}].", topicFilter);
        }
    }

    /**
     * Removes all the subscription entries and closes the command consumer.
     *
     **/
    public void removeAllSubscriptions() {
        subscriptions.keySet().forEach(this::removeSubscription);
    }

    private void closeCommandConsumer(final MessageConsumer consumer) {
        consumer.close(cls -> {
            if (cls.succeeded()) {
                LOG.debug("Command consumer closed");
                commandConsumer = null;
            } else {
                LOG.error("Error closing command consumer", cls.cause());
            }
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
     * Returns all subscriptions of this device.
     *
     * @return An unmodifiable view of the subscriptions map with the topic filter as key and the subscription object as
     *         value.
     */
    public Map<String, CommandSubscription> getSubscriptions() {
        return Collections.unmodifiableMap(subscriptions);
    }

    /**
     * A class to facilitate storing of information in connection with the pending command requests. The pending command
     * requests are tracked using a map in the enclosing class {@link CommandSubscriptionsManager}.
     */
    private static class PendingCommandRequest {

        private final Long timerId;
        private final Handler<Integer> onAckHandler;
        private final Handler<Void> onAckTimeoutHandler;

        /**
         * Creates a new PendingCommandRequest instance.
         *
         * @param timerId The unique ID of the timer.
         * @param onAckHandler Handler to invoke when the device has acknowledged the command.
         * @param onAckTimeoutHandler Handler to invoke when there is a timeout waiting for the acknowledgement from the
         *            device.
         * @throws NullPointerException if any of the parameters is {@code null}.
         */
        private PendingCommandRequest(final Long timerId, final Handler<Integer> onAckHandler,
                final Handler<Void> onAckTimeoutHandler) {
            this.timerId = Objects.requireNonNull(timerId);
            this.onAckHandler = Objects.requireNonNull(onAckHandler);
            this.onAckTimeoutHandler = Objects.requireNonNull(onAckTimeoutHandler);
        }

    }
}
