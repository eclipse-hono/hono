/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.adapter.MapBasedTelemetryExecutionContext;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;

import io.micrometer.core.instrument.Timer.Sample;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.Span;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * A dictionary of relevant information required during the
 * processing of an MQTT message published by a device.
 *
 */
public final class MqttContext extends MapBasedTelemetryExecutionContext {

    private static final String PARAM_ON_ERROR = "on-error";

    private final MqttPublishMessage message;
    private final MqttEndpoint deviceEndpoint;
    private final DeviceUser authenticatedDevice;

    // --- fields that are effectively final, may be null if topic is invalid ---
    private String contentType;
    private MetricsTags.EndpointType endpoint;
    private PropertyBag propertyBag;
    private Optional<Duration> timeToLive = Optional.empty();
    private ErrorHandlingMode errorHandlingMode = ErrorHandlingMode.DEFAULT;

    // --- fields that may get changed via setters; may be null ---
    private ResourceIdentifier topic;
    private Buffer mappedPayload;
    private Sample timer;

    /**
     * Modes defining how to handle errors raised when a device publishes a message.
     */
    public enum ErrorHandlingMode {
        /**
         * Disconnect on error.
         */
        DISCONNECT("disconnect"),
        /**
         * Ignore errors, send PUB_ACK if QoS is 1.
         */
        IGNORE("ignore"),
        /**
         * Ignore errors and don't send PUB_ACK if QoS is 1.
         */
        SKIP_ACK("skip-ack"),
        /**
         * Use the default error handling mode: {@link ErrorHandlingMode#IGNORE} if the device is subscribed to the
         * error topic or {@link ErrorHandlingMode#DISCONNECT} otherwise.
         */
        DEFAULT("default");

        private final String parameterValue;

        ErrorHandlingMode(final String parameterValue) {
            this.parameterValue = parameterValue;
        }

        /**
         * Construct an ErrorHandlingMode object from an MQTT topic property bag parameter value.
         *
         * @param paramValue The parameter value to parse.
         * @return The parsed ErrorHandlingMode with {@link ErrorHandlingMode#DEFAULT} as default.
         */
        public static ErrorHandlingMode from(final String paramValue) {
            if (paramValue != null) {
                for (final ErrorHandlingMode mode : values()) {
                    if (paramValue.equals(mode.parameterValue)) {
                        return mode;
                    }
                }
            }
            return DEFAULT;
        }
    }

    private MqttContext(
            final MqttPublishMessage message,
            final MqttEndpoint deviceEndpoint,
            final Span span,
            final DeviceUser authenticatedDevice) {
        super(span, authenticatedDevice);
        this.message = Objects.requireNonNull(message);
        this.deviceEndpoint = Objects.requireNonNull(deviceEndpoint);
        this.authenticatedDevice = authenticatedDevice;
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
            final DeviceUser authenticatedDevice) {

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
                result.errorHandlingMode = ErrorHandlingMode.from(bag.getProperty(PARAM_ON_ERROR));
            });
        return result;
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
     * Gets the QOS level of the message.
     *
     * @return The level.
     */
    public MqttQoS qosLevel() {
        return message.qosLevel();
    }

    /**
     * Checks if the message needs to be retained.
     *
     * @return {@code true} if the message needs to be retained.
     */
    public boolean isRetain() {
        return message.isRetain();
    }

    /**
     * Gets the message payload.
     * <p>
     * If a mapped message payload was set via {@link #applyMappedPayload(Buffer)},
     * it will be returned here. Otherwise the payload of the message from the device is used.
     *
     * @return The payload (not null).
     */
    public Buffer payload() {
        return Optional.ofNullable(mappedPayload)
                .or(() -> Optional.ofNullable(message.payload()))
                .orElseGet(Buffer::buffer);
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
    public DeviceUser authenticatedDevice() {
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
     * Gets the topic that the message is associated with.
     * <p>
     * Note that the returned resource might be different from the original topic
     * (as returned via {@link #getOrigAddress()}) in that the topic part representing
     * the device identifier may have been updated via {@link #applyMappedTargetDeviceId(String)}.
     * <p>
     * Any property bag parameters set in the original topic name will be excluded in the returned value.
     *
     * @return The topic or {@code null} if the topic could not be
     *         parsed into a resource identifier.
     */
    public ResourceIdentifier topic() {
        return topic;
    }

    /**
     * Applies a mapped target device identifier to this mqtt context.
     * <p>
     * The given device identifier overrides the one derived from the message topic.
     *
     * @param mappedTargetDeviceId The mapped target device identifier.
     * @throws NullPointerException if mappedTargetDeviceId is {@code null}.
     */
    public void applyMappedTargetDeviceId(final String mappedTargetDeviceId) {
        Objects.requireNonNull(mappedTargetDeviceId);

        if (topic != null && !mappedTargetDeviceId.equals(topic.getResourceId())) {
            topic = ResourceIdentifier.from(
                    topic,
                    topic.getTenantId(),
                    mappedTargetDeviceId);
        }
    }

    /**
     * Applies a mapped message payload which will override the original message payload
     * in the {@link #payload()} method.
     *
     * @param payload The payload.
     */
    public void applyMappedPayload(final Buffer payload) {
        this.mappedPayload = Objects.requireNonNull(payload);
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
     * Gets the identifier of the device that the message is associated with.
     * <p>
     * If a mapped target device identifier has been set, it will be returned here.
     * Otherwise the identifier is taken from the message topic or, if not set there,
     * from the authenticated device.
     * <p>
     * In a scenario of a gateway sending a message on behalf of an edge
     * device, the returned identifier is the edge device identifier.
     *
     * @return The device identifier or {@code null} if the topic of the message
     *         does not contain a device identifier and the device is not
     *         authenticated.
     */
    public String deviceId() {

        if (topic != null && !Strings.isNullOrEmpty(topic.getResourceId())) {
            return topic.getResourceId();
        } else if (authenticatedDevice != null) {
            return authenticatedDevice.getDeviceId();
        } else {
            return null;
        }
    }

    /**
     * Gets the correlation identifier taken either from a corresponding
     * property bag value or the message packet id.
     *
     * @return The correlation identifier or {@code null} in case of
     *         a QoS 0 message with no corresponding property bag value.
     */
    public String correlationId() {
        if (propertyBag != null) {
            final String propertyBagValue = propertyBag.getProperty(MessageHelper.SYS_PROPERTY_CORRELATION_ID);
            if (propertyBagValue != null) {
                return propertyBagValue;
            }
        }
        return isAtLeastOnce() ? Integer.toString(message.messageId()) : null;
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
     * Gets the mode defining how to handle errors raised when a device publishes a message.
     *
     * @param errorSubscriptionExists {@code true} if the device has an error topic subscription.
     * @return the effective mode.
     */
    public ErrorHandlingMode getErrorHandlingMode(final boolean errorSubscriptionExists) {
        if (errorHandlingMode == ErrorHandlingMode.DEFAULT) {
            return errorSubscriptionExists ? ErrorHandlingMode.IGNORE : ErrorHandlingMode.DISCONNECT;
        }
        return errorHandlingMode;
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
