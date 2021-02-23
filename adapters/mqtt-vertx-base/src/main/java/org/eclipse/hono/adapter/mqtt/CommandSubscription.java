/**
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.mqtt;

import java.util.Objects;

import org.eclipse.hono.adapter.client.command.Command;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.MqttTopicSubscription;

/**
 * A device's MQTT subscription for command messages.
 * <p>
 * Supported topic filters: {@code command|c/[+|TENANT]/[+|DEVICE_ID]/req|q/#}
 * <p>
 * Examples:
 * <ol>
 * <li>{@code command/DEFAULT_TENANT/4711/req/#} unauthenticated device</li>
 * <li>{@code command///req/#} - authenticated device</li>
 * <li>{@code c///q/#} - authenticated device using short names</li>
 * </ol>
 */
public final class CommandSubscription extends AbstractSubscription {

    private static final Logger LOG = LoggerFactory.getLogger(CommandSubscription.class);

    private final String req;
    private final Key key;

    private CommandSubscription(
            final ResourceIdentifier topicResource,
            final Device authenticatedDevice,
            final MqttQoS qos) {
        super(topicResource, authenticatedDevice, qos);
        this.req = topicResource.elementAt(3);
        this.key = new Key(getTenant(), getDeviceId());
    }

    /**
     * Creates a command subscription object for the given topic.
     * <p>
     * If the authenticated device is given, it is used to either validate the tenant and device-id
     * given via the topic or, if the topic doesn't contain these values, the authenticated device
     * is used to provide tenant and device-id for the created command subscription object.
     *
     * @param topic The topic to subscribe for commands.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @return The CommandSubscription object or {@code null} if the topic does not match the rules.
     * @throws NullPointerException if topic is {@code null}.
     */
    public static CommandSubscription fromTopic(final String topic, final Device authenticatedDevice) {
        Objects.requireNonNull(topic);
        try {
            final ResourceIdentifier topicResource = validateTopic(topic);
            return new CommandSubscription(topicResource, authenticatedDevice, null);
        } catch (final IllegalArgumentException e) {
            LOG.debug(e.getMessage());
            return null;
        }
    }

    /**
     * Creates a command subscription object for the given topic. When the authenticated device is given
     * it is used to either check given tenant and device-id from topic or fill this
     * fields if not given.
     *
     * @param mqttTopicSub The MqttTopicSubscription request from device for command subscription.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @return The CommandSubscription object or {@code null} if the topic does not match the rules.
     * @throws NullPointerException if mqttTopicSub is {@code null}.
     */
    public static CommandSubscription fromTopic(
            final MqttTopicSubscription mqttTopicSub,
            final Device authenticatedDevice) {

        Objects.requireNonNull(mqttTopicSub);
        try {
            final ResourceIdentifier topicResource = validateTopic(mqttTopicSub.topicName());
            return new CommandSubscription(
                    topicResource,
                    authenticatedDevice,
                    mqttTopicSub.qualityOfService());
        } catch (final IllegalArgumentException e) {
            LOG.debug(e.getMessage());
            return null;
        }
    }

    private static ResourceIdentifier validateTopic(final String topic) {
        Objects.requireNonNull(topic);
        if (topic.isEmpty()) {
            throw new IllegalArgumentException("topic filter must not be empty");
        }
        final ResourceIdentifier resource = ResourceIdentifier.fromString(topic);
        if (resource.length() != 5 || !"#".equals(resource.elementAt(4))) {
            throw new IllegalArgumentException(
                    "topic filter does not match pattern: " + CommandConstants.COMMAND_ENDPOINT + "|"
                            + CommandConstants.COMMAND_ENDPOINT_SHORT + "/+/+/req|q/#");
        }
        if (!CommandConstants.isCommandEndpoint(resource.getEndpoint())) {
            throw new IllegalArgumentException(
                    "the endpoint needs to be '" + CommandConstants.COMMAND_ENDPOINT + "' or '"
                            + CommandConstants.COMMAND_ENDPOINT_SHORT + "'");
        }
        if (!CommandConstants.COMMAND_RESPONSE_REQUEST_PART.equals(resource.elementAt(3))
                && !CommandConstants.COMMAND_RESPONSE_REQUEST_PART_SHORT.equals(resource.elementAt(3))) {
            throw new IllegalArgumentException(
                    "the request part needs to be '" + CommandConstants.COMMAND_RESPONSE_REQUEST_PART + "' or '"
                            + CommandConstants.COMMAND_RESPONSE_REQUEST_PART_SHORT + "'");
        }
        return resource;
    }

    /**
     * Gets the key to identify a subscription in the list of subscriptions of an MQTT Endpoint.
     * An MQTT Endpoint can't have multiple subscriptions with the same key, meaning with
     * respect to command subscriptions that there can only be one command subscription
     * per device.
     *
     * @return The key.
     */
    @Override
    public Key getKey() {
        return key;
    }

    /**
     * Gets the request part of the subscription.
     *
     * @return The request part.
     */
    public String getRequestPart() {
        return req;
    }

    /**
     * Gets the name of the topic that a command should be published to.
     *
     * @param command The command to publish.
     * @return The topic name.
     * @throws NullPointerException if command is {@code null}.
     */
    public String getCommandPublishTopic(final Command command) {

        Objects.requireNonNull(command);

        final String topicTenantId = containsTenantId() ? getTenant() : "";
        final String topicDeviceId = command.isTargetedAtGateway() ? command.getDeviceId()
                : containsDeviceId() ? getDeviceId() : "";
        final String topicCommandRequestId = command.isOneWay() ? "" : command.getRequestId();

        return String.format(
                "%s/%s/%s/%s/%s/%s",
                getEndpoint(),
                topicTenantId,
                topicDeviceId,
                getRequestPart(),
                topicCommandRequestId,
                command.getName());
    }

    /**
     * The key to identify a command subscription of a particular device.
     */
    public static final class Key {

        private final String deviceId;
        private final String tenantId;

        /**
         * Creates a new Key.
         *
         * @param tenantId The tenant identifier.
         * @param deviceId The device identifier.
         * @throws NullPointerException If tenantId or deviceId is {@code null}.
         */
        public Key(final String tenantId, final String deviceId) {
            this.tenantId = Objects.requireNonNull(tenantId);
            this.deviceId = Objects.requireNonNull(deviceId);
        }

        public String getDeviceId() {
            return deviceId;
        }

        public String getTenantId() {
            return tenantId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Key that = (Key) o;
            return deviceId.equals(that.deviceId) &&
                    tenantId.equals(that.tenantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deviceId, tenantId);
        }
    }
}
