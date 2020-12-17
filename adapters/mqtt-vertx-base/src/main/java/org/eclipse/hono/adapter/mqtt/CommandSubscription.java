/**
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.MqttTopicSubscription;

/**
 * A device's MQTT subscription for command messages.
 * <p>
 * Supported topic names: {@code command|c/[+|TENANT]/[+|DEVICE_ID]/req|q/#}
 * <p>
 * Examples:
 * <ol>
 * <li>{@code command/DEFAULT_TENANT/4711/req/#} unauthenticated device</li>
 * <li>{@code command///req/#} - authenticated device</li>
 * <li>{@code c///q/#} - authenticated device using short names</li>
 * </ol>
 */
public final class CommandSubscription {

    private static final Logger LOG = LoggerFactory.getLogger(CommandSubscription.class);

    private final String endpoint;
    private final String req;
    private final String tenant;
    private final String deviceId;
    private final String authenticatedDeviceId;
    private final String topic;
    private final boolean authenticated;
    private final boolean containsTenantId;
    private final boolean containsDeviceId;

    private MqttQoS qos;
    private String clientId;

    private CommandSubscription(final String topic, final Device authenticatedDevice) {
        this.topic = Objects.requireNonNull(topic);
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
        this.endpoint = resource.getEndpoint();
        final String resourceTenant = "+".equals(resource.getTenantId()) ? null : resource.getTenantId();
        this.containsTenantId = !Strings.isNullOrEmpty(resourceTenant);
        final String resourceDeviceId = "+".equals(resource.getResourceId()) ? null : resource.getResourceId();
        this.containsDeviceId = !Strings.isNullOrEmpty(resourceDeviceId);
        this.req = resource.elementAt(3);

        this.authenticated = authenticatedDevice != null;

        if (isAuthenticated()) {
            if (resourceTenant != null && !authenticatedDevice.getTenantId().equals(resourceTenant)) {
                throw new IllegalArgumentException(
                        "tenant in topic filter does not match authenticated device");
            }
            this.tenant = authenticatedDevice.getTenantId();
            this.authenticatedDeviceId = authenticatedDevice.getDeviceId();
            this.deviceId = Strings.isNullOrEmpty(resourceDeviceId) ? authenticatedDevice.getDeviceId() : resourceDeviceId;
        } else {
            if (Strings.isNullOrEmpty(resourceTenant)) {
                throw new IllegalArgumentException(
                        "for unauthenticated devices the tenant needs to be given in the subscription");
            }
            if (Strings.isNullOrEmpty(resourceDeviceId)) {
                throw new IllegalArgumentException(
                        "for unauthenticated devices the device-id needs to be given in the subscription");
            }
            this.tenant = resourceTenant;
            this.authenticatedDeviceId = null;
            this.deviceId = resourceDeviceId;
        }
    }

    private CommandSubscription(final String topic, final Device authenticatedDevice, final MqttQoS qos, final String clientId) {
        this(topic, authenticatedDevice);
        this.qos = qos;
        this.clientId = clientId;
    }

    /**
     * Creates a command subscription object for the given topic. When the authenticated device is given
     * it is used to either check given tenant and device-id from topic or fill this
     * fields if not given.
     *
     * @param topic The topic to subscribe for commands.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @return The CommandSubscription object or {@code null} if the topic does not match the rules.
     * @throws NullPointerException if topic is {@code null}.
     */
    public static CommandSubscription fromTopic(final String topic, final Device authenticatedDevice) {
        try {
            return new CommandSubscription(topic, authenticatedDevice);
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
     * @param clientId The client identifier as provided by the remote MQTT client.
     * @return The CommandSubscription object or {@code null} if the topic does not match the rules.
     * @throws NullPointerException if topic is {@code null}.
     */
    public static CommandSubscription fromTopic(
            final MqttTopicSubscription mqttTopicSub,
            final Device authenticatedDevice,
            final String clientId) {

        try {
            return new CommandSubscription(
                    mqttTopicSub.topicName(),
                    authenticatedDevice,
                    mqttTopicSub.qualityOfService(),
                    clientId);
        } catch (final IllegalArgumentException e) {
            LOG.debug(e.getMessage());
            return null;
        }
    }

    /**
     * Gets the tenant from topic or authentication.
     *
     * @return The tenant.
     */
    public String getTenant() {
        return tenant;
    }

    /**
     * Gets the device id from topic or authentication.
     *
     * @return The device id.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Gets the device id from authentication.
     *
     * @return The device id or {@code null}.
     */
    public String getAuthenticatedDeviceId() {
        return authenticatedDeviceId;
    }

    /**
     * Gets the endpoint of the subscription.
     *
     * @return The endpoint.
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Gets the QoS of the subscription.
     *
     * @return The QoS value.
     */
    public MqttQoS getQos() {
        return qos;
    }

    /**
     * Gets the clientId of the Mqtt subscription.
     *
     * @return The clientId.
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Gets the subscription topic.
     *
     * @return The topic.
     */
    public String getTopic() {
        return topic;
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
     * Gets the authentication status, which indicates the need to publish on tenant/device-id for unauthenticated
     * devices.
     *
     * @return {@code true} if created with an authenticated device.
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Checks whether this subscription represents the case of a gateway subscribing for commands
     * of a specific device that it acts on behalf of.
     *
     * @return {@code true} if a gateway is subscribing for commands of a specific device.
     */
    public boolean isGatewaySubscriptionForSpecificDevice() {
        return authenticatedDeviceId != null && !authenticatedDeviceId.equals(deviceId);
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

        final String topicTenantId = containsTenantId ? getTenant() : "";
        final String topicDeviceId = command.isTargetedAtGateway() ? command.getOriginalDeviceId()
                : containsDeviceId ? getDeviceId() : "";
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
