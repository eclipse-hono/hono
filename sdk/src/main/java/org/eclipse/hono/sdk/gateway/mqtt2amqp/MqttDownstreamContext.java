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

import java.util.Objects;

import org.eclipse.hono.auth.Device;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * A dictionary of relevant information required during the processing of an MQTT message published by a device.
 *
 */
public class MqttDownstreamContext {

    private final MqttPublishMessage message;
    private final MqttEndpoint deviceEndpoint;
    private final Device authenticatedDevice;
    private final String topic;
    private final MqttQoS qos;

    private MqttDownstreamContext(final Device authenticatedDevice, final MqttPublishMessage publishedMessage,
            final MqttEndpoint deviceEndpoint, final String topic) {
        this.authenticatedDevice = authenticatedDevice;
        this.message = publishedMessage;
        this.deviceEndpoint = deviceEndpoint;
        this.topic = topic;
        this.qos = publishedMessage.qosLevel();
    }

    /**
     * Creates a new context for a published message.
     * 
     * @param message The published MQTT message.
     * @param deviceEndpoint The endpoint representing the device that has published the message.
     * @param authenticatedDevice The authenticated device identity.
     * @return The context.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static MqttDownstreamContext fromPublishPacket(
            final MqttPublishMessage message,
            final MqttEndpoint deviceEndpoint,
            final Device authenticatedDevice) {

        Objects.requireNonNull(message);
        Objects.requireNonNull(deviceEndpoint);
        Objects.requireNonNull(authenticatedDevice);

        return new MqttDownstreamContext(authenticatedDevice, message, deviceEndpoint, message.topicName());
    }

    /**
     * Gets the MQTT message to process.
     *
     * @return The message.
     */
    public MqttPublishMessage message() {
        return message;
    }

    /**
     * Gets the MQTT endpoint over which the message has been received.
     * 
     * @return The endpoint.
     */
    MqttEndpoint deviceEndpoint() {
        return deviceEndpoint;
    }

    /**
     * Gets the identity of the authenticated device that has published the message.
     * 
     * @return The identity or {@code null} if the device has not been authenticated.
     */
    public Device authenticatedDevice() {
        return authenticatedDevice;
    }

    /**
     * Gets the topic that the message has been published to.
     * 
     * @return The topic.
     */
    public String topic() {
        return topic;
    }

    /**
     * Gets the QoS level of the published MQTT message.
     * 
     * @return The QoS.
     */
    public MqttQoS qosLevel() {
        return qos;
    }

    /**
     * Sends a PUBACK for the message to the device.
     */
    public void acknowledge() {
        if (message != null && deviceEndpoint != null) {
            deviceEndpoint.publishAcknowledge(message.messageId());
        }
    }
}
