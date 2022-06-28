/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import java.util.Optional;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TelemetryConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.MqttTopicSubscription;

/**
 * A device's MQTT subscription for error messages.
 * <p>
 * Supported topic filters: {@code error|e/[TENANT]/[DEVICE_ID]/#}
 * <br>(empty tenant/device segments can also be omitted)
 * <p>
 * Examples:
 * <ol>
 * <li>{@code error/DEFAULT_TENANT/4711/#} unauthenticated device</li>
 * <li>{@code error///#} - authenticated device</li>
 * <li>{@code e///#} - authenticated device using short name</li>
 * </ol>
 */
public final class ErrorSubscription extends AbstractSubscription {

    /**
     * The name of the error endpoint.
     */
    public static final String ERROR_ENDPOINT = "error";
    /**
     * The short name of the error endpoint.
     */
    public static final String ERROR_ENDPOINT_SHORT = "e";
    /**
     * Endpoint used in the topic of the published error message identifying the topic endpoint
     * in the message that lead to the error as unknown.
     */
    public static final String UNKNOWN_ENDPOINT = "unknown";
    /**
     * Endpoint used in the topic of the published error message identifying the message that
     * lead to the error as a command response message.
     */
    public static final String COMMAND_RESPONSE_ENDPOINT = "command-response";
    /**
     * Short variant of the endpoint used in the topic of the published error message identifying the message that
     * lead to the error as a command response message.
     */
    public static final String COMMAND_RESPONSE_ENDPOINT_SHORT = "c-s";

    private static final Logger LOG = LoggerFactory.getLogger(ErrorSubscription.class);

    private final Key key;

    private ErrorSubscription(
            final ResourceIdentifier topicResource,
            final Device authenticatedDevice,
            final MqttQoS qos) {
        super(topicResource, qos, authenticatedDevice);
        key = getKey(getTenant(), getDeviceId());
    }

    /**
     * Creates a error subscription object for the given topic.
     * <p>
     * If the authenticated device is given, it is used to either validate the tenant and device-id
     * given via the topic or, if the topic doesn't contain these values, the authenticated device
     * is used to provide tenant and device-id for the created error subscription object.
     *
     * @param mqttTopicSub The MqttTopicSubscription request from device for error subscription.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @return The ErrorSubscription object or {@code null} if the topic does not match the rules.
     * @throws NullPointerException if mqttTopicSub is {@code null}.
     */
    public static ErrorSubscription fromTopic(final MqttTopicSubscription mqttTopicSub, final Device authenticatedDevice) {
        Objects.requireNonNull(mqttTopicSub);
        return fromTopic(mqttTopicSub.topicName(), mqttTopicSub.qualityOfService(), authenticatedDevice);
    }

    /**
     * Creates a error subscription object for the given topic.
     * <p>
     * If the authenticated device is given, it is used to either validate the tenant and device-id
     * given via the topic or, if the topic doesn't contain these values, the authenticated device
     * is used to provide tenant and device-id for the created error subscription object.
     *
     * @param topic The topic to subscribe for errors.
     * @param qos The quality-of-service level for the subscription.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @return The ErrorSubscription object or {@code null} if the topic does not match the rules.
     * @throws NullPointerException if topic or qos is {@code null}.
     */
    public static ErrorSubscription fromTopic(final String topic, final MqttQoS qos, final Device authenticatedDevice) {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(qos);
        try {
            final ResourceIdentifier topicResource = validateTopic(topic);
            return new ErrorSubscription(topicResource, authenticatedDevice, qos);
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
        if (resource.length() != 4 || !"#".equals(resource.elementAt(resource.length() - 1))) {
            throw new IllegalArgumentException(
                    "invalid topic filter: must have 4 segments and end with '#'");
        }
        if (!isErrorEndpoint(resource.getEndpoint())) {
            throw new IllegalArgumentException(
                    "the endpoint needs to be '" + ERROR_ENDPOINT + "' or '" + ERROR_ENDPOINT_SHORT + "'");
        }
        return resource;
    }

    private static boolean isErrorEndpoint(final String endpoint) {
        return ERROR_ENDPOINT.equals(endpoint) || ERROR_ENDPOINT_SHORT.equals(endpoint);
    }

    /**
     * Checks whether the given topic name starts with the error endpoint identifier.
     *
     * @param topic The topic to check.
     * @return {@code true} if the topic has the error endpoint prefix.
     */
    public static boolean hasErrorEndpointPrefix(final String topic) {
        return topic != null && (topic.startsWith(ERROR_ENDPOINT + "/")
                || topic.startsWith(ERROR_ENDPOINT_SHORT + "/"));
    }

    /**
     * Gets the key to identify an error subscription in the list of error subscriptions of an MQTT Endpoint.
     * <p>
     * Only returns a non-null key if the topic is valid.
     *
     * @param topic The topic to subscribe for errors.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @return The key or {@code null} if the topic does not match the rules.
     * @throws NullPointerException if topic is {@code null}.
     */
    public static Key getKey(final String topic, final Device authenticatedDevice) {
        Objects.requireNonNull(topic);
        try {
            final ResourceIdentifier topicResource = validateTopic(topic);
            // using some non-null QoS value - will be ignored
            return new ErrorSubscription(topicResource, authenticatedDevice, MqttQoS.AT_MOST_ONCE).getKey();
        } catch (final IllegalArgumentException e) {
            LOG.debug(e.getMessage());
            return null;
        }
    }

    /**
     * Gets the key to identify a subscription in the list of subscriptions of an MQTT Endpoint.
     *
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @return The key.
     * @throws NullPointerException If tenantId or deviceId is {@code null}.
     */
    public static Key getKey(final String tenantId, final String deviceId) {
        return new DefaultKey(tenantId, deviceId, DefaultKey.Type.ERROR);
    }

    @Override
    public Key getKey() {
        return key;
    }

    /**
     * Gets the name of the topic that an error should be published to.
     *
     * @param context The context in which the error occurred.
     * @param errorCode The error code.
     * @return The topic name.
     * @throws NullPointerException if context is {@code null}.
     */
    public String getErrorPublishTopic(final MqttContext context, final int errorCode) {
        Objects.requireNonNull(context);

        final String contextDeviceId = context.deviceId();
        final String contextEndpoint = Optional.ofNullable(context.topic())
                .map(ResourceIdentifier::getEndpoint)
                .orElse("");
        return getErrorPublishTopic(contextEndpoint, contextDeviceId, context.correlationId(), errorCode);
    }

    /**
     * Gets the name of the topic that an error should be published to.
     *
     * @param messageEndpoint The endpoint of the message for which to send the error (e.g. "telemetry").
     * @param deviceId The identifier of the device for which to send the error (may be {@code null}). The value
     *                 will be used if it is not empty and differs from the (not empty) device
     *                 identifier of this error subscription.
     * @param correlationId The identifier of the message for which to send the error (may be {@code null}).
     * @param errorCode The error code.
     * @return The topic name.
     */
    public String getErrorPublishTopic(
            final String messageEndpoint,
            final String deviceId,
            final String correlationId,
            final int errorCode) {

        final String topicTenantId = containsTenantId() ? getTenant() : "";
        final String topicDeviceId;
        if (!Strings.isNullOrEmpty(deviceId) && !deviceId.equals(getDeviceId())) {
            // gateway case - use the deviceId parameter
            topicDeviceId = deviceId;
        } else {
            topicDeviceId = containsDeviceId() ? getDeviceId() : "";
        }

        return String.format(
                "%s/%s/%s/%s/%s/%s",
                getEndpoint(),
                topicTenantId,
                topicDeviceId,
                getSanitizedMessageEndpoint(messageEndpoint),
                Strings.isNullOrEmpty(correlationId) ? "-1" : correlationId,
                errorCode);
    }

    private String getSanitizedMessageEndpoint(final String messageEndpoint) {
        if (messageEndpoint != null) {
            switch (messageEndpoint) {
            case TelemetryConstants.TELEMETRY_ENDPOINT:
            case TelemetryConstants.TELEMETRY_ENDPOINT_SHORT:
            case EventConstants.EVENT_ENDPOINT:
            case EventConstants.EVENT_ENDPOINT_SHORT:
                return messageEndpoint;
            case CommandConstants.COMMAND_ENDPOINT:
                // command response messages have a topic of the form "command///res/${req-id}/${status}"
                return COMMAND_RESPONSE_ENDPOINT;
            case CommandConstants.COMMAND_ENDPOINT_SHORT:
                return COMMAND_RESPONSE_ENDPOINT_SHORT;
            default:
                return UNKNOWN_ENDPOINT;
            }
        }
        return UNKNOWN_ENDPOINT;
    }
}
