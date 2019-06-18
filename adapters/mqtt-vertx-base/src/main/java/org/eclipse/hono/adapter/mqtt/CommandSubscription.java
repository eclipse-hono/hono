/**
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.MqttTopicSubscription;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.util.CommandConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MQTT subscription of devices, to get commands.
 *
 * <p>
 * Format of subscription need to be: {@code control|c/+|TENANT/+|DEVICE_ID/req|q/#} - e.g.:
 * </p>
 * <ol>
 * <li>{@code control/+/+/req/#} - authenticated device and verbose format</li>
 * <li>{@code c/+/+/q/#} - authenticated device with short format</li>
 * <li>{@code control/DEFAULT_TENANT/4711/req/#} unauthenticated device with verbose format</li>
 * </ol>
 */
public class CommandSubscription {

    private static final Logger LOG = LoggerFactory.getLogger(CommandSubscription.class);

    private String endpoint;
    private String req;
    private String tenant;
    private String deviceId;
    private MqttQoS qos;
    private String clientId;
    private String topic;
    private boolean isAuthenticated;

    private CommandSubscription(final String topic) {
        Objects.requireNonNull(topic);
        this.topic = topic;
        final String[] parts = topic.split("\\/");
        if (parts.length != 5 || !"#".equals(parts[4])) {
            throw new IllegalArgumentException(
                    "topic filter does not match pattern: " + CommandConstants.COMMAND_ENDPOINT + "|"
                            + CommandConstants.COMMAND_LEGACY_ENDPOINT + "|"
                            + CommandConstants.COMMAND_ENDPOINT_SHORT + "/+/+/req|q/#");
        }
        endpoint = parts[0];
        if (!CommandConstants.isCommandEndpoint(endpoint)) {
            throw new IllegalArgumentException(
                    "the endpoint needs to be '" + CommandConstants.COMMAND_ENDPOINT + "' or '"
                            + CommandConstants.COMMAND_LEGACY_ENDPOINT + "' or '"
                            + CommandConstants.COMMAND_ENDPOINT_SHORT + "'");
        }
        req = parts[3];
        if (!CommandConstants.COMMAND_RESPONSE_REQUEST_PART.equals(req)
                && !CommandConstants.COMMAND_RESPONSE_REQUEST_PART_SHORT.equals(req)) {
            throw new IllegalArgumentException(
                    "the request part needs to be '" + CommandConstants.COMMAND_RESPONSE_REQUEST_PART + "' or '"
                            + CommandConstants.COMMAND_RESPONSE_REQUEST_PART_SHORT + "'");
        }
        if (!"+".equals(parts[1])) {
            tenant = parts[1];
        }
        if (!"+".equals(parts[2])) {
            deviceId = parts[2];
        }
    }

    private CommandSubscription(final String topic, final Device authenticatedDevice) {
        this(topic);
        if (authenticatedDevice == null) {
            isAuthenticated = false;
            if (tenant == null || tenant.isEmpty()) {
                throw new IllegalArgumentException(
                        "for unauthenticated devices the tenant needs to be given in the subscription");
            }
            if (deviceId == null || deviceId.isEmpty()) {
                throw new IllegalArgumentException(
                        "for unauthenticated devices the device-id needs to be given in the subscription");
            }
        } else {
            isAuthenticated = true;
            if ((tenant != null && !authenticatedDevice.getTenantId().equals(tenant)) ||
                    (deviceId != null && !authenticatedDevice.getDeviceId().equals(deviceId))) {
                throw new IllegalArgumentException(
                        "for authenticated devices the given device-id and tenant need to match the authentication or be undefined ('+')");
            } else {
                tenant = authenticatedDevice.getTenantId();
                deviceId = authenticatedDevice.getDeviceId();
            }
        }
    }

    private CommandSubscription(final String topic, final Device authenticatedDevice, final MqttQoS qos, final String clientId) {
        this(topic, authenticatedDevice);
        this.qos = qos;
        this.clientId = clientId;
    }

    /**
     * Gets the tenant from topic or authentication .
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
        return isAuthenticated;
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
     * @param clientId The the client identifier as provided by the remote MQTT client.
     * @return The CommandSubscription object or {@code null} if the topic does not match the rules.
     * @throws NullPointerException if topic is {@code null}.
     */
    public static CommandSubscription fromTopic(final MqttTopicSubscription mqttTopicSub, final Device authenticatedDevice, final String clientId) {
        try {
            return new CommandSubscription(mqttTopicSub.topicName(), authenticatedDevice,
                    mqttTopicSub.qualityOfService(), clientId);
        } catch (final IllegalArgumentException e) {
            LOG.debug(e.getMessage());
            return null;
        }
    }
}
