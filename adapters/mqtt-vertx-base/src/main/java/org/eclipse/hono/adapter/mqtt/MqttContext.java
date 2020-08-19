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

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.device.DeviceCredentials;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.MapBasedTelemetryExecutionContext;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObject;

import io.micrometer.core.instrument.Timer.Sample;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
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
    private final ResourceIdentifier topic;
    private final TenantObject tenantObject;
    private final String authId;
    private final PropertyBag propertyBag;
    private final MetricsTags.EndpointType endpoint;

    private String contentType;
    private Sample timer;

    private MqttContext(
            final MqttPublishMessage message,
            final MqttEndpoint deviceEndpoint,
            final ResourceIdentifier topic,
            final TenantObject tenantObject,
            final String authId,
            final Device authenticatedDevice,
            final PropertyBag propertyBag) {
        super(authenticatedDevice);
        this.message = Objects.requireNonNull(message);
        this.deviceEndpoint = Objects.requireNonNull(deviceEndpoint);
        this.topic = Objects.requireNonNull(topic);
        this.tenantObject = Objects.requireNonNull(tenantObject);
        this.authId = authId;
        this.propertyBag = propertyBag;
        this.endpoint = MetricsTags.EndpointType.fromString(topic.getEndpoint());
    }

    /**
     * Creates a new context for a published message.
     *
     * @param publishedMessage The published MQTT message.
     * @param deviceEndpoint The endpoint representing the device that has published the message.
     * @param requestTenantDetailsProvider The provider to determine tenant and authentication identifier from the
     *            authentication data used during connection establishment or to determine the tenant from the topic of
     *            the PUBLISH message in case of an unauthenticated connection.
     * @param authenticatedDevice The authenticated identity of the device that published the message or {@code null} if
     *            the device has not been authenticated.
     * @param spanContext The OpenTracing context to use for tracking the operation (may be {@code null}).
     * @return A future containing the created context. The future will be failed if there was an error getting the
     *         tenant and authentication identifier via the requestTenantDetailsProvider. In particular, the future will be
     *         failed with a {@link ClientErrorException} if there was no corresponding data given in the connection
     *         attempt or in the message topic to determine the tenant.
     * @throws NullPointerException if message, endpoint or requestTenantDetailsProvider is {@code null}.
     */
    public static Future<MqttContext> fromPublishPacket(
            final MqttPublishMessage publishedMessage,
            final MqttEndpoint deviceEndpoint,
            final MqttRequestTenantDetailsProvider requestTenantDetailsProvider,
            final Device authenticatedDevice,
            final SpanContext spanContext) {

        Objects.requireNonNull(publishedMessage);
        Objects.requireNonNull(deviceEndpoint);
        Objects.requireNonNull(requestTenantDetailsProvider);

        if (publishedMessage.topicName() == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed topic name"));
        }
        final PropertyBag propertyBag = PropertyBag.fromTopic(publishedMessage.topicName());
        final ResourceIdentifier topic = ResourceIdentifier.fromString(propertyBag != null
                ? propertyBag.topicWithoutPropertyBag()
                : publishedMessage.topicName());

        return requestTenantDetailsProvider.getFromMqttEndpointOrPublishTopic(deviceEndpoint, topic, spanContext)
                .map(tenantObjectWithAuthId -> new MqttContext(
                        publishedMessage,
                        deviceEndpoint,
                        topic,
                        tenantObjectWithAuthId.getTenantObject(),
                        (tenantObjectWithAuthId instanceof DeviceCredentials)
                                ? ((DeviceCredentials) tenantObjectWithAuthId).getAuthId()
                                : null,
                        authenticatedDevice,
                        propertyBag));
    }

    /**
     * Creates a new context for a published message.
     *
     * @param publishedMessage The published MQTT message.
     * @param deviceEndpoint The endpoint representing the device that has published the message.
     * @param tenantObject The tenant that the device belongs to that published the message.
     * @param authId The authentication identifier used for the MQTT connection or {@code null} if the connection is
     *            unauthenticated.
     * @param authenticatedDevice The authenticated identity of the device that published the message or {@code null} if
     *            the device has not been authenticated.
     * @return The context.
     * @throws NullPointerException if any of the parameters except authenticatedDevice is {@code null}.
     * @throws IllegalArgumentException if the topic of the given message is {@code null}.
     */
    public static MqttContext fromPublishPacket(
            final MqttPublishMessage publishedMessage,
            final MqttEndpoint deviceEndpoint,
            final TenantObject tenantObject,
            final String authId,
            final Device authenticatedDevice) {

        Objects.requireNonNull(publishedMessage);
        Objects.requireNonNull(deviceEndpoint);
        Objects.requireNonNull(tenantObject);

        if (publishedMessage.topicName() == null) {
            throw new IllegalArgumentException("message topic must be set");
        }
        final PropertyBag propertyBag = PropertyBag.fromTopic(publishedMessage.topicName());
        final ResourceIdentifier topic = ResourceIdentifier.fromString(propertyBag != null
                ? propertyBag.topicWithoutPropertyBag()
                : publishedMessage.topicName());

        return new MqttContext(
                publishedMessage,
                deviceEndpoint,
                topic,
                tenantObject,
                authId,
                authenticatedDevice,
                propertyBag);
    }

    @Override
    public QoS getRequestedQos() {

        switch (message.qosLevel()) {
        case AT_LEAST_ONCE:
            return QoS.AT_LEAST_ONCE;
        case AT_MOST_ONCE:
            return QoS.AT_MOST_ONCE;
        default:
            return QoS.UNKNOWN;
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
        return getAuthenticatedDevice();
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
     * Gets the tenant that the device belongs to that published the message.
     *
     * @return The tenant object.
     */
    public TenantObject tenantObject() {
        return tenantObject;
    }

    /**
     * Gets the authentication identifier used in the MQTT connection.
     *
     * @return The authentication identifier or {@code null} if the device has not been authenticated.
     */
    public String authId() {
        return authId;
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
