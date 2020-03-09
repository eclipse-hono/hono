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

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.MapBasedExecutionContext;
import org.eclipse.hono.util.ResourceIdentifier;

import io.micrometer.core.instrument.Timer.Sample;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * A dictionary of relevant information required during the
 * processing of an MQTT message published by a device.
 *
 */
public final class MqttContext extends MapBasedExecutionContext {

    private MqttPublishMessage message;
    private MqttEndpoint deviceEndpoint;
    private Device authenticatedDevice;
    private ResourceIdentifier topic;
    private String contentType;
    private Sample timer;
    private MetricsTags.EndpointType endpoint;
    private PropertyBag propertyBag;

    private MqttContext() {
    }

    /**
     * Creates a new context for a published message.
     * 
     * @param publishedMessage The MQTT message to process.
     * @param deviceEndpoint The endpoint representing the device
     *                       that has published the message.
     * @return The context.
     * @throws NullPointerException if message or endpoint are {@code null}.
     */
    public static MqttContext fromPublishPacket(
            final MqttPublishMessage publishedMessage,
            final MqttEndpoint deviceEndpoint) {

        return fromPublishPacket(publishedMessage, deviceEndpoint, null);
    }

    /**
     * Creates a new context for a published message.
     * 
     * @param publishedMessage The published MQTT message.
     * @param deviceEndpoint The endpoint representing the device
     *                       that has published the message.
     * @param authenticatedDevice The authenticated device identity.
     * @return The context.
     * @throws NullPointerException if message or endpoint are {@code null}.
     */
    public static MqttContext fromPublishPacket(
            final MqttPublishMessage publishedMessage,
            final MqttEndpoint deviceEndpoint,
            final Device authenticatedDevice) {

        Objects.requireNonNull(publishedMessage);
        Objects.requireNonNull(deviceEndpoint);

        final MqttContext result = new MqttContext();
        result.message = publishedMessage;
        result.deviceEndpoint = deviceEndpoint;
        result.authenticatedDevice = authenticatedDevice;
        if (publishedMessage.topicName() != null) {
            try {
                Optional.ofNullable(PropertyBag.fromTopic(publishedMessage.topicName()))
                        .ifPresentOrElse(propertyBag -> {
                            result.topic = ResourceIdentifier.fromString(propertyBag.topicWithoutPropertyBag());
                            result.propertyBag = propertyBag;
                        }, () -> result.topic = ResourceIdentifier.fromString(publishedMessage.topicName()));
                result.endpoint = MetricsTags.EndpointType.fromString(result.topic.getEndpoint());
            } catch (final IllegalArgumentException e) {
                // malformed topic
            }
        }
        return result;
    }

    /**
     * Creates a new context for a connection attempt.
     * 
     * @param endpoint The endpoint representing the client's connection attempt.
     * @return The context.
     * @throws NullPointerException if endpoint is {@code null}.
     */
    public static MqttContext fromConnectPacket(final MqttEndpoint endpoint) {
        final MqttContext result = new MqttContext();
        result.deviceEndpoint = endpoint;
        return result;
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

    /**
     * Gets the topic that the message has been published to.
     * 
     * @return The topic or {@code null} if the topic could not be
     *         parsed into a resource identifier.
     */
    public ResourceIdentifier topic() {
        return topic;
    }

    /**
     * Gets the tenant that the device belongs to that published
     * the message.
     * 
     * @return The tenant identifier or {@code null} if the device is
     *         not authenticated and the message's topic does not contain
     *         a tenant identifier.
     */
    public String tenant() {

        if (authenticatedDevice != null) {
            return authenticatedDevice.getTenantId();
        } else if (topic != null) {
            return topic.getTenantId();
        } else {
            return null;
        }
    }

    /**
     * Gets the property bag object from the <em>property-bag</em>
     * set in the message's topic.
     * 
     * @return The property bag object or {@code null} if
     *         there is no property bag set in the topic.
     */
    public PropertyBag propertyBag() {
        return this.propertyBag;
    }

    /**
     * Gets the endpoint that the message has been published to.
     * <p>
     * The name is determined from the endpoint path segment of the
     * topic that the message has been published to.
     * 
     * @return The endpoint or {@code null} if the message does not
     *         contain a topic.
     */
    public MetricsTags.EndpointType endpoint() {
        return endpoint;
    }

    /**
     * Checks if the message has been published using QoS 1.
     * 
     * @return {@code true} if the message has been published using QoS 1.
     */
    public boolean isAtLeastOnce() {

        if (message == null) {
            return false;
        } else {
            return MqttQoS.AT_LEAST_ONCE == message.qosLevel();
        }
    }

    /**
     * Sends a PUBACK for the message to the device.
     */
    public void acknowledge() {
        if (message != null && deviceEndpoint != null) {
            deviceEndpoint.publishAcknowledge(message.messageId());
        }
    }

    /**
     * Sets the object to use for measuring the time it takes to
     * process this request.
     * 
     * @param timer The timer.
     */
    public void setTimer(final Sample timer) {
        this.timer = timer;
    }

    /**
     * Gets the object used for measuring the time it takes to
     * process this request.
     * 
     * @return The timer or {@code null} if not set.
     */
    public Sample getTimer() {
        return timer;
    }
}
