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
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * A class that tracks command subscriptions, unsubscriptions and handles PUBACKs of a device.
 */
final class CommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CommandHandler.class);
    private final Map<String, CommandSubscription> subscriptions = new ConcurrentHashMap<>();
    private final Map<Integer, PendingCommandRequest> waitingForAcknowledgement = new ConcurrentHashMap<>();
    private final Vertx vertx;
    private final int commandAckTimeout;
    private Future<MessageConsumer> commandConsumer;
    private final Device authenticatedDevice;

    /**
     * Creates a new CommandHandler instance.
     *
     * @param vertx The Vert.x instance to execute the client on.
     * @param commandAckTimeout The number of milliseconds to wait for a acknowledgement.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @throws NullPointerException if vertx is {@code null}.
     */
    CommandHandler(final Vertx vertx, final int commandAckTimeout, final Device authenticatedDevice) {
        this.vertx = Objects.requireNonNull(vertx);
        this.authenticatedDevice = Objects.requireNonNull(authenticatedDevice);
        this.commandAckTimeout = commandAckTimeout;
    }

    public Device getAuthenticatedDevice() {
        return authenticatedDevice;
    }

    /**
     * Invoked when a device sends an MQTT <em>PUBACK</em> packet.
     *
     * @param msgId The msgId of the command published with QoS 1.
     * @param afterCommandPublished The action to be invoked if not {@code null} on arrival of PUBACK.
     * @throws NullPointerException if msgId is {@code null}.
     */
    public void handlePubAck(final Integer msgId,
            final BiConsumer<Message, CommandSubscription> afterCommandPublished) {
        Objects.requireNonNull(msgId);
        LOG.trace("Acknowledgement received for command [Msg-id: {}] that has been sent to device.", msgId);
        Optional.ofNullable(removeFromWaitingForAcknowledgement(msgId)).ifPresent(value -> {
            cancelTimer(value.timerId);

            final CommandSubscription subscription = value.subscription;
            if (afterCommandPublished != null) {
                afterCommandPublished.accept(value.message, subscription);
            }
            LOG.debug("Acknowledged [Msg-id: {}] command to {} [MQTT client-id: {}, QoS: {}]", msgId,
                    authenticatedDevice.toString(), subscription.getClientId(), subscription.getQos());
        });
    }

    /**
     * Stores the published message id along with message subscription and message context.
     *
     * @param msgId The id of the message (message) that has been published.
     * @param subscription The device's message subscription.
     * @param message The message sent.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public void addToWaitingForAcknowledgement(final Integer msgId, final CommandSubscription subscription,
            final Message message) {

        Objects.requireNonNull(msgId);
        Objects.requireNonNull(subscription);
        Objects.requireNonNull(message);

        waitingForAcknowledgement.put(msgId, new PendingCommandRequest(startTimer(msgId), subscription, message));
    }

    /**
     * Removes the entry from the waitingForAcknowledgement map for the given msgId.
     *
     * @param msgId The id of the command (message) that has been published.
     * @return The PendingCommandRequest object containing timer-id, subscription and message for the given msgId.
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
            LOG.debug("Remove subscription for topicFilter [{}]", topicFilter);

            if (commandConsumer != null && subscriptions.isEmpty()) {
                if (commandConsumer.succeeded()) {
                    closeCommandConsumer(commandConsumer.result());
                }
            }
        }
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

    /**
     * Removes all the subscription entries and closes the command consumer.
     *
     **/
    public void removeAllSubscriptions() {
        subscriptions.keySet().forEach(this::removeSubscription);
    }

    private long startTimer(final Integer msgId) {

        return vertx.setTimer(commandAckTimeout, timerId -> {

            Optional.ofNullable(removeFromWaitingForAcknowledgement(msgId)).ifPresent(value -> {
                final CommandSubscription subscription = value.subscription;
                LOG.debug("Timed out waiting for acknowledgment for command sent to {} [MQTT client-id: {}, QoS: {}]",
                        authenticatedDevice.toString(), subscription.getClientId(), subscription.getQos());
            });
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
     * requests are tracked using a map in the enclosing class {@link CommandHandler} and is used to handle PUBACKs from
     * devices.
     */
    private static class PendingCommandRequest {

        private final Long timerId;
        private final CommandSubscription subscription;
        private final Message message;

        /**
         * Creates a new PendingCommandRequest instance.
         *
         * @param timerId The unique ID of the timer.
         * @param subscription The device's msg subscription.
         * @param msg The msg sent.
         */
        private PendingCommandRequest(final Long timerId, final CommandSubscription subscription, final Message msg) {

            this.timerId = timerId;
            this.subscription = subscription;
            this.message = msg;
        }

    }
}
