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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TriTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.log.Fields;
import io.vertx.core.Vertx;

/**
 * A class that tracks command subscriptions, unsubscriptions and handles PUBACKs.
 * 
 * @param <T> The type of configuration properties the mqtt adapter supports/requires.
 */
public final class CommandHandler<T extends MqttProtocolAdapterProperties> {
    private static final Logger LOG = LoggerFactory.getLogger(CommandHandler.class);
    private final Map<String, TriTuple<CommandSubscription, MessageConsumer, Object>> subscriptions = new ConcurrentHashMap<>();
    private final Map<Integer, PendingCommandRequest> waitingForAcknowledgement = new ConcurrentHashMap<>();
    private final Vertx vertx;
    private final T config;

    /**
     * Creates a new CommandHandler instance.
     *
     * @param vertx The Vert.x instance to execute the client on.
     * @param config The configuration properties to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public CommandHandler(final Vertx vertx, final T config) {
        this.vertx = Objects.requireNonNull(vertx);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Invoked when a device sends an MQTT <em>PUBACK</em> packet.
     *
     * @param msgId The msgId of the command published with QoS 1.
     * @param afterCommandPublished The action to be invoked if not {@code null} on arrival of PUBACK.
     * @throws NullPointerException if msgId is {@code null}.
     */
    public void handlePubAck(final Integer msgId, final Function<TenantObject, BiConsumer<CommandSubscription, CommandContext>> afterCommandPublished) {
        Objects.requireNonNull(msgId);
        LOG.trace("Acknowledgement received for command [Msg-id: {}] that has been sent to device.", msgId);
        Optional.ofNullable(removeFromWaitingForAcknowledgement(msgId)).ifPresent(value -> {
            cancelTimer(value.timerId);

            final CommandSubscription subscription = value.subscription;
            if (afterCommandPublished != null) {
                afterCommandPublished.apply(value.tenantObject).accept(subscription, value.commandContext);
            }
            LOG.debug(
                    "Acknowledged [Msg-id: {}] command to device [tenant-id: {}, device-id: {}, MQTT client-id: {}, QoS: {}]",
                    msgId, subscription.getTenant(), subscription.getDeviceId(), subscription.getClientId(),
                    subscription.getQos());
            final Map<String, String> items = new HashMap<>(3);
            items.put(Fields.EVENT, "Published command has been acknowledged");
            items.put(TracingHelper.TAG_CLIENT_ID.getKey(), subscription.getClientId());
            items.put(TracingHelper.TAG_QOS.getKey(), subscription.getQos().toString());
            value.commandContext.getCurrentSpan().log(items);
        });
    }

    /**
     * Stores the published message id along with command subscription and command context.
     *
     * @param msgId The id of the command (message) that has been published.
     * @param tenantObject The tenant configuration object.
     * @param subscription The device's command subscription.
     * @param commandContext The commandContext of the command sent.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public void addToWaitingForAcknowledgement(final Integer msgId,
            final TenantObject tenantObject,
            final CommandSubscription subscription,
            final CommandContext commandContext) {

        Objects.requireNonNull(msgId);
        Objects.requireNonNull(tenantObject);
        Objects.requireNonNull(subscription);
        Objects.requireNonNull(commandContext);

        waitingForAcknowledgement.put(msgId,
                PendingCommandRequest.from(startTimer(msgId), tenantObject, subscription, commandContext));
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
            final MessageConsumer commandConsumer) {
        Objects.requireNonNull(subscription);
        Objects.requireNonNull(commandConsumer);
        subscriptions.put(subscription.getTopic(), TriTuple.of(subscription, commandConsumer, null));
    }

    /**
     * Closes the command consumer and removes the subscription entry for the given topic.
     *
     * @param topic The topic string to unsubscribe.
     * @param consumer The consumer to be invoked if not {@code null} during removal of a subscription.
     * @throws NullPointerException if topic is {@code null}.
     **/
    public void removeSubscription(final String topic, final BiConsumer<String, String> consumer) {
        Objects.requireNonNull(topic);
        Optional.ofNullable(subscriptions.remove(topic)).ifPresent(value -> {
            final CommandSubscription subscription = value.one();
            if (consumer != null) {
                consumer.accept(subscription.getTenant(), subscription.getDeviceId());
            }
            closeCommandConsumer(subscription, value.two());
        });
    }

    /**
     * Closes the command consumers and removes all the subscription entries.
     * 
     * @param consumer The consumer to be invoked if not {@code null} during removal of a subscription.
     **/
    public void removeAllSubscriptions(final BiConsumer<String, String> consumer) {
        subscriptions.keySet().forEach(topic -> removeSubscription(topic, consumer));
    }

    /**
     * Stores the command subscription along with the command consumer.
     *
     * @param subscription The device's command subscription.
     * @param commandConsumer A client for consuming messages.
     */
    private void closeCommandConsumer(final CommandSubscription subscription,
            final MessageConsumer commandConsumer) {
        commandConsumer.close(cls -> {
            if (cls.succeeded()) {
                LOG.trace("Command consumer closed [tenant-it: {}, device-id :{}]", subscription.getTenant(),
                        subscription.getDeviceId());
            } else {
                LOG.debug("Error closing command consumer [tenant-it: {}, device-id :{}]", subscription.getTenant(),
                        subscription.getDeviceId(), cls.cause());
            }
        });
    }

    private long startTimer(final Integer msgId) {

        return vertx.setTimer(config.getCommandAckTimeout(), timerId -> {

            Optional.ofNullable(removeFromWaitingForAcknowledgement(msgId)).ifPresent(value -> {
                final CommandSubscription subscription = value.subscription;
                LOG.debug(
                        "Timed out waiting for acknowledgment for command sent to device [tenant-id: {}, device-id: {}, MQTT client-id: {}, QoS: {}]",
                        subscription.getTenant(), subscription.getDeviceId(), subscription.getClientId(),
                        subscription.getQos());
                final Map<String, String> items = new HashMap<>(3);
                items.put(Fields.EVENT, "Timed out waiting for acknowledgment for command sent to device");
                items.put(TracingHelper.TAG_CLIENT_ID.getKey(), subscription.getClientId());
                items.put(TracingHelper.TAG_QOS.getKey(), subscription.getQos().toString());
                value.commandContext.getCurrentSpan().log(items);
                value.commandContext.release(1);
            });
        });
    }

    private void cancelTimer(final Long timerId) {
        vertx.cancelTimer(timerId);
        LOG.trace("Canceled Timer [timer-id: {}}", timerId);
    }

    /**
     * A class to facilitate storing of information in connection with the pending command requests.
     * The pending command requests are tracked using a map in the enclosing class {@link CommandHandler}
     * and is used to handle PUBACKs from devices.
     */
    private static class PendingCommandRequest {

        private final Long timerId;
        private final TenantObject tenantObject;
        private final CommandSubscription subscription;
        private final CommandContext commandContext;

        private PendingCommandRequest(final Long timerId, final TenantObject tenantObject,
                final CommandSubscription subscription, final CommandContext commandContext) {

            Objects.requireNonNull(timerId);
            Objects.requireNonNull(tenantObject);
            Objects.requireNonNull(subscription);
            Objects.requireNonNull(commandContext);

            this.timerId = timerId;
            this.tenantObject = tenantObject;
            this.subscription = subscription;
            this.commandContext = commandContext;
        }

        /**
         * Creates a new PendingCommandRequest instance.
         *
         * @param timerId The unique ID of the timer.
         * @param tenantObject The tenant configuration object.
         * @param subscription The device's command subscription.
         * @param commandContext The commandContext of the command sent.
         * @throws NullPointerException if any of the parameters are {@code null}.
         */        
        private static PendingCommandRequest from(final Long timerId, final TenantObject tenantObject,
                final CommandSubscription subscription, final CommandContext commandContext){
            Objects.requireNonNull(timerId);
            Objects.requireNonNull(tenantObject);
            Objects.requireNonNull(subscription);
            Objects.requireNonNull(commandContext);

            return new PendingCommandRequest(timerId, tenantObject, subscription, commandContext);
        }
    }
}
