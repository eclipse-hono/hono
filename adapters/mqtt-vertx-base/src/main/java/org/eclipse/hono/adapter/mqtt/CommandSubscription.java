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

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.command.Command;
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
        super(topicResource, qos, authenticatedDevice);
        this.req = topicResource.elementAt(3);
        this.key = new DefaultKey(getTenant(), getDeviceId(), DefaultKey.Type.COMMAND);
    }

    /**
     * Creates a command subscription object for the given topic.
     * <p>
     * If the authenticated device is given, it is used to either validate the tenant and device-id
     * given via the topic or, if the topic doesn't contain these values, the authenticated device
     * is used to provide tenant and device-id for the created command subscription object.
     *
     * @param mqttTopicSub The MqttTopicSubscription request from device for command subscription.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @return The CommandSubscription object or {@code null} if the topic does not match the rules.
     * @throws NullPointerException if mqttTopicSub is {@code null}.
     */
    public static CommandSubscription fromTopic(final MqttTopicSubscription mqttTopicSub, final Device authenticatedDevice) {
        Objects.requireNonNull(mqttTopicSub);
        return fromTopic(mqttTopicSub.topicName(), mqttTopicSub.qualityOfService(), authenticatedDevice);
    }

    /**
     * Creates a command subscription object for the given topic.
     * <p>
     * If the authenticated device is given, it is used to either validate the tenant and device-id
     * given via the topic or, if the topic doesn't contain these values, the authenticated device
     * is used to provide tenant and device-id for the created command subscription object.
     *
     * @param topic The topic to subscribe for commands.
     * @param qos The quality-of-service level for the subscription.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @return The CommandSubscription object or {@code null} if the topic does not match the rules.
     * @throws NullPointerException if topic or qos is {@code null}.
     */
    public static CommandSubscription fromTopic(final String topic, final MqttQoS qos, final Device authenticatedDevice) {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(qos);
        try {
            final ResourceIdentifier topicResource = validateTopic(topic);
            return new CommandSubscription(topicResource, authenticatedDevice, qos);
        } catch (final IllegalArgumentException e) {
            LOG.debug(e.getMessage());
            return null;
        }
    }

    private static ResourceIdentifier validateTopic(final String topic) {
        Objects.requireNonNull(topic);
        if (!ResourceIdentifier.isValid(topic)) {
            throw new IllegalArgumentException("topic filter or its first segment must not be empty");
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
     * Checks whether the given topic name starts with the command endpoint identifier.
     *
     * @param topic The topic to check.
     * @return {@code true} if the topic has the command endpoint prefix.
     */
    public static boolean hasCommandEndpointPrefix(final String topic) {
        return topic != null && (topic.startsWith(CommandConstants.COMMAND_ENDPOINT + "/")
                || topic.startsWith(CommandConstants.COMMAND_ENDPOINT_SHORT + "/"));
    }

    /**
     * Get the key to identify a command subscription in the list of command subscriptions of an MQTT Endpoint.
     * <p>
     * Only returns a non-null key if the topic is valid.
     *
     * @param topic The topic of the subscription.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @return The key or {@code null} if the topic does not match the rules.
     * @throws NullPointerException if topic is {@code null}.
     */
    public static Key getKey(final String topic, final Device authenticatedDevice) {
        Objects.requireNonNull(topic);
        try {
            final ResourceIdentifier topicResource = validateTopic(topic);
            // using some non-null QoS value - will be ignored
            return new CommandSubscription(topicResource, authenticatedDevice, MqttQoS.AT_MOST_ONCE).getKey();
        } catch (final IllegalArgumentException e) {
            LOG.debug(e.getMessage());
            return null;
        }
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
        final String topicDeviceId;
        if (command.isTargetedAtGateway()) {
            topicDeviceId = command.getDeviceId();
        } else {
            topicDeviceId = containsDeviceId() ? getDeviceId() : "";
        }
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
}
