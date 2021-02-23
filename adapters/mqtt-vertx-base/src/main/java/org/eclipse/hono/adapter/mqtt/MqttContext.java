/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MapBasedTelemetryExecutionContext;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;

import io.micrometer.core.instrument.Timer.Sample;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.Span;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * A dictionary of relevant information required during the
 * processing of an MQTT message published by a device.
 *
 */
public final class MqttContext extends MapBasedTelemetryExecutionContext {

    private final MqttPublishMessage message;
    private final MqttEndpoint deviceEndpoint;
    private final Device authenticatedDevice;
    private ResourceIdentifier topic;
    private String contentType;
    private Sample timer;
    private MetricsTags.EndpointType endpoint;
    private PropertyBag propertyBag;
    private Optional<Duration> timeToLive = Optional.empty();

    private MqttContext(
            final MqttPublishMessage message,
            final MqttEndpoint deviceEndpoint,
            final Span span,
            final Device authenticatedDevice) {
        super(span, authenticatedDevice);
        this.message = Objects.requireNonNull(message);
        this.deviceEndpoint = Objects.requireNonNull(deviceEndpoint);
        this.authenticatedDevice = authenticatedDevice;
    }

    private static Optional<Duration> determineTimeToLive(final PropertyBag properties) {
        try {
            final Duration timeToLive = Optional.ofNullable(properties)
                    .map(propBag -> propBag.getProperty(Constants.HEADER_TIME_TO_LIVE))
                    .map(Long::parseLong)
                    .map(ttl -> ttl < 0 ? null : Duration.ofSeconds(ttl))
                    .orElse(null);
            return Optional.ofNullable(timeToLive);
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Creates a new context for a published message.
     *
     * @param publishedMessage The MQTT message to process.
     * @param deviceEndpoint The endpoint representing the device
     *                       that has published the message.
     * @param span The <em>OpenTracing</em> root span that is used to track the processing of this context.
     * @return The context.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static MqttContext fromPublishPacket(
            final MqttPublishMessage publishedMessage,
            final MqttEndpoint deviceEndpoint,
            final Span span) {

        return fromPublishPacket(publishedMessage, deviceEndpoint, span, null);
    }

    /**
     * Creates a new context for a published message.
     *
     * @param publishedMessage The published MQTT message.
     * @param deviceEndpoint The endpoint representing the device
     *                       that has published the message.
     * @param span The <em>OpenTracing</em> root span that is used to track the processing of this context.
     * @param authenticatedDevice The authenticated device identity (may be {@code null}).
     * @return The context.
     * @throws NullPointerException if any of the parameters except authenticatedDevice is {@code null}.
     */
    public static MqttContext fromPublishPacket(
            final MqttPublishMessage publishedMessage,
            final MqttEndpoint deviceEndpoint,
            final Span span,
            final Device authenticatedDevice) {

        Objects.requireNonNull(publishedMessage);
        Objects.requireNonNull(deviceEndpoint);
        Objects.requireNonNull(span);

        final MqttContext result = new MqttContext(publishedMessage, deviceEndpoint, span, authenticatedDevice);
        Optional.ofNullable(publishedMessage.topicName())
            .map(PropertyBag::fromTopic)
            .ifPresent(bag -> {
                result.propertyBag = bag;
                result.topic = bag.topicWithoutPropertyBag();
                result.endpoint = MetricsTags.EndpointType.fromString(result.topic.getEndpoint());
                // set the content-type using the corresponding value from the property bag
                result.contentType = bag.getProperty(MessageHelper.SYS_PROPERTY_CONTENT_TYPE);
                if (result.endpoint == EndpointType.EVENT) {
                    result.timeToLive = determineTimeToLive(bag);
                }
            });
        return result;
    }

    @Override
    public QoS getRequestedQos() {

        switch (message.qosLevel()) {
            case AT_LEAST_ONCE:
                return QoS.AT_LEAST_ONCE;
            case AT_MOST_ONCE:
                return QoS.AT_MOST_ONCE;
            default:
                return null;
        }
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
     * <p>
     * The type has either been set via {@link #setContentType(String)}
     * or it is the type determined from the message topic's property
     * bag, if it contains a content type.
     *
     * @return The type of the message payload or {@code null} if the type is unknown.
     */
    public String contentType() {
        return contentType;
    }

    /**
     * Sets the content type of the message payload.
     * <p>
     * This overwrites the content type determined from the message topic's
     * property bag (if any).
     *
     * @param contentType The type.
     * @throws NullPointerException if contentType is {@code null}.
     */
    public void setContentType(final String contentType) {
        this.contentType = Objects.requireNonNull(contentType);
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
        } else if (topic != null && !Strings.isNullOrEmpty(topic.getTenantId())) {
            return topic.getTenantId();
        } else {
            return null;
        }
    }

    /**
     * Gets the property bag object from the <em>property-bag</em>
     * set in the message's topic.
     *
     * @return The property bag (which may be empty) or
     *         {@code null} if the topic is {@code null} or empty.
     */
    public PropertyBag propertyBag() {
        return propertyBag;
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
        if (message != null && deviceEndpoint != null && isAtLeastOnce()) {
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

    /**
     * {@inheritDoc}
     *
     * @return The message topic.
     */
    @Override
    public String getOrigAddress() {
        return message.topicName();
    }

    /**
     * {@inheritDoc}
     *
     * @return An optional containing the <em>time-to-live</em> duration or an empty optional if
     * <ul>
     *     <li>the topic has no property bag</li>
     *     <li>the property bag does not contain a {@link org.eclipse.hono.util.Constants#HEADER_TIME_TO_LIVE} property</li>
     *     <li>the contained value cannot be parsed as a positive long</li>
     * </ul>
     */
    @Override
    public Optional<Duration> getTimeToLive() {
        return timeToLive;
    }
}
