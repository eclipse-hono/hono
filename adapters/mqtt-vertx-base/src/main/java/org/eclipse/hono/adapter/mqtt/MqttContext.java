/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.adapter.mqtt;

import java.util.Objects;

import org.eclipse.hono.service.auth.device.Device;

import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * A dictionary of relevant information required during the
 * processing of an MQTT message published by a device.
 *
 */
public final class MqttContext {

    private final MqttPublishMessage message;
    private final MqttEndpoint deviceEndpoint;
    private final Device authenticatedDevice;

    private String contentType;

    /**
     * Creates a new context for a message and an endpoint.
     * 
     * @param publishedMessage The MQTT message to process.
     * @param deviceEndpoint The endpoint representing the device
     *                       that has published the message.
     * @throws NullPointerException if message or endpoint are {@code null}.
     */
    public MqttContext(
            final MqttPublishMessage publishedMessage,
            final MqttEndpoint deviceEndpoint) {

        this(publishedMessage, deviceEndpoint, null);
    }

    /**
     * Creates a new context for a message and an endpoint.
     * 
     * @param publishedMessage The published MQTT message.
     * @param deviceEndpoint The endpoint representing the device
     *                       that has published the message.
     * @param authenticatedDevice The authenticated device identity.
     * @throws NullPointerException if message or endpoint are {@code null}.
     */
    public MqttContext(
            final MqttPublishMessage publishedMessage,
            final MqttEndpoint deviceEndpoint,
            final Device authenticatedDevice) {

        this.message = Objects.requireNonNull(publishedMessage);
        this.deviceEndpoint = Objects.requireNonNull(deviceEndpoint);
        this.authenticatedDevice = authenticatedDevice;
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
     * Gets the MQTT endpoint over which the message has been
     * received.
     * 
     * @return The endpoint.
     */
    public MqttEndpoint deviceEndpoint() {
        return deviceEndpoint;
    }

    /**
     * Gets the identity of the authenticated device
     * that has published the message.
     * 
     * @return The identity or {@code null} if the device has not
     *         been authenticated.
     */
    public Device authenticatedDevice() {
        return authenticatedDevice;
    }

    /**
     * Gets the content type of the message payload.
     * 
     * @return The type or {@code null} if the content type is unknown.
     */
    public String contentType() {
        return contentType;
    }

    /**
     * Sets the content type of the message payload.
     * 
     * @param contentType The type or {@code null} if the content type is unknown.
     */
    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }
}
