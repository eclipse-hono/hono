/**
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.stream.Stream;

import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.Span;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.impl.MqttTopicSubscriptionImpl;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * Verifies behavior of {@link ErrorSubscription}.
 *
 */
public class ErrorSubscriptionTest {

    private final DeviceUser device = new DeviceUser("tenant", "device");
    private final DeviceUser gw = new DeviceUser("tenant", "gw");

    static Stream<String> endpointNames() {
        return Stream.of(getErrorEndpoint(), getErrorEndpointShort());
    }

    static Stream<Arguments> endpointNamesWithQoS() {
        return Stream.of(
                Arguments.of(getErrorEndpoint(), MqttQoS.AT_MOST_ONCE),
                Arguments.of(getErrorEndpoint(), MqttQoS.AT_LEAST_ONCE),
                Arguments.of(getErrorEndpointShort(), MqttQoS.AT_MOST_ONCE),
                Arguments.of(getErrorEndpointShort(), MqttQoS.AT_LEAST_ONCE));
    }

    /**
     * Verifies that creating a subscription for a topic fails if the endpoint is neither
     * {@value ErrorSubscription#ERROR_ENDPOINT} nor {@value ErrorSubscription#ERROR_ENDPOINT_SHORT}.
     */
    @Test
    public void testSubscriptionRequiresCorrectEndpoint() {
        final ErrorSubscription subscription = ErrorSubscription.fromTopic("cx/tenant/device/#", MqttQoS.AT_MOST_ONCE, null);
        assertThat(subscription).isNull();
    }

    /**
     * Verifies that creating a subscription for a topic fails if the client is unauthenticated and neither
     * tenant nor device ID are specified explicitly.
     *
     * @param endpointName The endpoint name used in the topic.
     */
    @ParameterizedTest
    @MethodSource("endpointNames")
    public void testSubscriptionRequiresExplicitTenantAndDeviceIdForUnauthenticatedClient(final String endpointName) {

        String topic = String.format("%s///#", endpointName);
        assertThat(ErrorSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, null)).isNull();

        topic = String.format("%s/+/#", endpointName);
        assertThat(ErrorSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, null)).isNull();

        topic = String.format("%s/+/+/#", endpointName);
        assertThat(ErrorSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, null)).isNull();
    }

    /**
     * Verifies that creating a subscription for a topic fails if the topic ends in something else than the <em>#</em>
     * character.
     *
     * @param endpointName The endpoint name used in the topic.
     */
    @ParameterizedTest
    @MethodSource("endpointNames")
    public void testSubscriptionRequiresWildcardSegmentAtEnd(final String endpointName) {

        String topic = String.format("%s/tenant/device/not#", endpointName);
        assertThat(ErrorSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, null)).isNull();

        topic = String.format("%s///not#", endpointName);
        assertThat(ErrorSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, device)).isNull();

        topic = String.format("%s/+/+/not#", endpointName);
        assertThat(ErrorSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, device)).isNull();
    }

    /**
     * Verifies that creating a subscription for a topic fails if the topic doesn't contain
     * 4 segments.
     *
     * @param endpointName The endpoint name used in the topic.
     */
    @ParameterizedTest
    @MethodSource("endpointNames")
    public void testSubscriptionRequiresTopicWithFourSegments(final String endpointName) {

        String topic = String.format("%s/tenant/device/additionalSegment/#", endpointName);
        assertThat(ErrorSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, null)).isNull();

        topic = String.format("%s/tenant/#", endpointName);
        assertThat(ErrorSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, null)).isNull();
    }


    /**
     * Verifies that an unauthenticated device can successfully subscribe for errors
     * using the default topic.
     *
     * @param endpointName The endpoint name used in the topic.
     * @param qos The requested QoS.
     */
    @ParameterizedTest
    @MethodSource("endpointNamesWithQoS")
    public void testSubscriptionSucceedsForUnauthenticatedDevice(final String endpointName, final MqttQoS qos) {

        final MqttTopicSubscription mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s/tenant1/deviceA/#", endpointName), qos);

        final ErrorSubscription subscription = ErrorSubscription.fromTopic(mqttTopicSubscription, null);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo("tenant1");
        assertThat(subscription.getDeviceId()).isEqualTo("deviceA");
        assertThat(subscription.getEndpoint()).isEqualTo(endpointName);
        assertThat(subscription.getQos()).isEqualTo(qos);
    }

    /**
     * Verifies that an authenticated device can successfully subscribe for errors
     * targeted at itself using all variants of topic names.
     *
     * @param endpointName The endpoint name used in the topic.
     * @param qos The requested QoS.
     */
    @ParameterizedTest
    @MethodSource("endpointNamesWithQoS")
    public void testSubscriptionSucceedsForAuthenticatedDevice(final String endpointName, final MqttQoS qos) {

        final Integer messageId = 123;
        final int errorCode = 503;
        final String errorCausingMsgEndpoint = TelemetryConstants.TELEMETRY_ENDPOINT;

        final MqttPublishMessage message = mock(MqttPublishMessage.class);
        when(message.messageId()).thenReturn(messageId);
        when(message.topicName()).thenReturn(errorCausingMsgEndpoint);
        when(message.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        final MqttContext context = MqttContext.fromPublishPacket(message, mock(MqttEndpoint.class), mock(Span.class), device);

        // WHEN subscribing to errors using explicit topic
        MqttTopicSubscription mqttTopicSubscription = new MqttTopicSubscriptionImpl(String.format("%s/tenant/device/#", endpointName), qos);

        ErrorSubscription subscription = ErrorSubscription.fromTopic(mqttTopicSubscription, device);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo("tenant");
        assertThat(subscription.getDeviceId()).isEqualTo("device");
        assertThat(subscription.getEndpoint()).isEqualTo(endpointName);
        assertThat(subscription.getQos()).isEqualTo(qos);
        // THEN the error topic does include both the tenant and device ID
        assertThat(subscription.getErrorPublishTopic(context, errorCode))
            .isEqualTo(String.format("%s/%s/%s/%s/%s/%s",
                    endpointName, device.getTenantId(), device.getDeviceId(), errorCausingMsgEndpoint, messageId, errorCode));

        // WHEN subscribing to errors including tenant only
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(String.format("%s/tenant//#", endpointName), qos);

        subscription = ErrorSubscription.fromTopic(mqttTopicSubscription, device);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo("tenant");
        assertThat(subscription.getDeviceId()).isEqualTo("device");
        assertThat(subscription.getEndpoint()).isEqualTo(endpointName);
        assertThat(subscription.getQos()).isEqualTo(qos);
        // THEN the error topic does include the tenant as well
        assertThat(subscription.getErrorPublishTopic(context, errorCode))
                .isEqualTo(String.format("%s/%s//%s/%s/%s",
                        endpointName, device.getTenantId(), errorCausingMsgEndpoint, messageId, errorCode));

        // WHEN subscribing to errors including device ID only
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(String.format("%s//device/#", endpointName), qos);

        subscription = ErrorSubscription.fromTopic(mqttTopicSubscription, device);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo("tenant");
        assertThat(subscription.getDeviceId()).isEqualTo("device");
        assertThat(subscription.getEndpoint()).isEqualTo(endpointName);
        assertThat(subscription.getQos()).isEqualTo(qos);
        // THEN the error topic does include the device ID as well
        assertThat(subscription.getErrorPublishTopic(context, errorCode))
                .isEqualTo(String.format("%s//%s/%s/%s/%s",
                        endpointName, device.getDeviceId(), errorCausingMsgEndpoint, messageId, errorCode));

        // using a tenant other than the tenant that the device belongs to should fail
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s/otherTenant/device/#", endpointName), qos);
        assertThat(ErrorSubscription.fromTopic(mqttTopicSubscription, device)).isNull();
    }

    /**
     * Verifies that an authenticated gateway can successfully subscribe for errors
     * targeted at one of devices that it is authorized to act on behalf of.
     *
     * @param endpointName The endpoint name used in the topic.
     * @param qos The requested QoS.
     */
    @ParameterizedTest
    @MethodSource("endpointNamesWithQoS")
    public void testSubscriptionSucceedsForAuthenticatedGateway(final String endpointName, final MqttQoS qos) {

        final String gatewayManagedDeviceId = "gatewayManagedDevice";
        final Integer messageId = 123;
        final int errorCode = 503;
        final String errorCausingMsgEndpoint = TelemetryConstants.TELEMETRY_ENDPOINT;

        final MqttPublishMessage message = mock(MqttPublishMessage.class);
        when(message.messageId()).thenReturn(messageId);
        when(message.topicName()).thenReturn(ResourceIdentifier.from(errorCausingMsgEndpoint, gw.getTenantId(), gatewayManagedDeviceId).toString());
        when(message.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        final MqttContext context = MqttContext.fromPublishPacket(message, mock(MqttEndpoint.class), mock(Span.class), gw);

        // WHEN subscribing to errors for a specific device omitting tenant
        MqttTopicSubscription mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s//%s/#", endpointName, gatewayManagedDeviceId), qos);

        ErrorSubscription subscription = ErrorSubscription.fromTopic(mqttTopicSubscription, gw);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(gw.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo(gatewayManagedDeviceId);
        assertThat(subscription.getAuthenticatedDeviceId()).isEqualTo(gw.getDeviceId());
        assertThat(subscription.getQos()).isEqualTo(qos);
        assertThat(subscription.isGatewaySubscriptionForSpecificDevice()).isEqualTo(true);
        // THEN the error topic does not include the tenant either
        assertThat(subscription.getErrorPublishTopic(context, errorCode))
                .isEqualTo(String.format("%s//%s/%s/%s/%s",
                        endpointName, gatewayManagedDeviceId, errorCausingMsgEndpoint, messageId, errorCode));

        // WHEN subscribing to errors for a specific device including the tenant
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s/%s/%s/#", endpointName, device.getTenantId(), gatewayManagedDeviceId), qos);

        subscription = ErrorSubscription.fromTopic(mqttTopicSubscription, gw);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(gw.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo(gatewayManagedDeviceId);
        assertThat(subscription.getAuthenticatedDeviceId()).isEqualTo(gw.getDeviceId());
        assertThat(subscription.getQos()).isEqualTo(qos);
        assertThat(subscription.isGatewaySubscriptionForSpecificDevice()).isEqualTo(true);
        // THEN the error topic does include the tenant as well
        assertThat(subscription.getErrorPublishTopic(context, errorCode))
                .isEqualTo(String.format("%s/%s/%s/%s/%s/%s",
                        endpointName, gw.getTenantId(), gatewayManagedDeviceId, errorCausingMsgEndpoint, messageId, errorCode));

        // WHEN subscribing to errors for all devices omitting tenant
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s//+/#", endpointName), qos);

        subscription = ErrorSubscription.fromTopic(mqttTopicSubscription, gw);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(gw.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo(gw.getDeviceId());
        assertThat(subscription.getAuthenticatedDeviceId()).isEqualTo(gw.getDeviceId());
        assertThat(subscription.getQos()).isEqualTo(qos);
        assertThat(subscription.isGatewaySubscriptionForSpecificDevice()).isEqualTo(false);
        // THEN the error topic does not include the tenant either
        assertThat(subscription.getErrorPublishTopic(context, errorCode))
                .isEqualTo(String.format("%s//%s/%s/%s/%s",
                        endpointName, gatewayManagedDeviceId, errorCausingMsgEndpoint, messageId, errorCode));

        // WHEN subscribing to errors for all devices including the tenant
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s/%s/+/#", endpointName, device.getTenantId()), qos);

        subscription = ErrorSubscription.fromTopic(mqttTopicSubscription, gw);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(gw.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo(gw.getDeviceId());
        assertThat(subscription.getAuthenticatedDeviceId()).isEqualTo(gw.getDeviceId());
        assertThat(subscription.getQos()).isEqualTo(qos);
        assertThat(subscription.isGatewaySubscriptionForSpecificDevice()).isEqualTo(false);
        // THEN the error topic does include the tenant as well
        assertThat(subscription.getErrorPublishTopic(context, errorCode))
                .isEqualTo(String.format("%s/%s/%s/%s/%s/%s",
                        endpointName, gw.getTenantId(), gatewayManagedDeviceId, errorCausingMsgEndpoint, messageId, errorCode));

        // using a tenant other than the tenant that the gateway belongs to should fail
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s/otherTenant/+/#", endpointName), qos);
        assertThat(ErrorSubscription.fromTopic(mqttTopicSubscription, gw)).isNull();
    }

    /**
     * Verifies that the topic for an error caused by a message sent to a topic with
     * an unknown/invalid endpoint is "unknown".
     */
    @Test
    public void testGetErrorPublishTopicForMessageWithUnknownEndpoint() {
        final MqttTopicSubscription mqttTopicSubscription = new MqttTopicSubscriptionImpl("error///#", MqttQoS.AT_MOST_ONCE);
        final ErrorSubscription subscription = ErrorSubscription.fromTopic(mqttTopicSubscription, device);

        assertThat(subscription).isNotNull();
        String errorPublishTopic = subscription.getErrorPublishTopic("something", null, null, 503);
        assertThat(errorPublishTopic.split("/")[3]).isEqualTo(ErrorSubscription.UNKNOWN_ENDPOINT);

        errorPublishTopic = subscription.getErrorPublishTopic("", null, null, 503);
        assertThat(errorPublishTopic.split("/")[3]).isEqualTo(ErrorSubscription.UNKNOWN_ENDPOINT);

        errorPublishTopic = subscription.getErrorPublishTopic(null, null, null, 503);
        assertThat(errorPublishTopic.split("/")[3]).isEqualTo(ErrorSubscription.UNKNOWN_ENDPOINT);
    }

    /**
     * Verifies that the topic for an error caused by a command response message is correct.
     */
    @Test
    public void testGetErrorPublishTopicForCommandResponseMessage() {
        final MqttTopicSubscription mqttTopicSubscription = new MqttTopicSubscriptionImpl("error///#", MqttQoS.AT_MOST_ONCE);
        final ErrorSubscription subscription = ErrorSubscription.fromTopic(mqttTopicSubscription, device);

        assertThat(subscription).isNotNull();
        String errorPublishTopic = subscription.getErrorPublishTopic(CommandConstants.COMMAND_ENDPOINT, null, null, 503);
        assertThat(errorPublishTopic.split("/")[3]).isEqualTo(ErrorSubscription.COMMAND_RESPONSE_ENDPOINT);

        errorPublishTopic = subscription.getErrorPublishTopic(CommandConstants.COMMAND_ENDPOINT_SHORT, null, null, 503);
        assertThat(errorPublishTopic.split("/")[3]).isEqualTo(ErrorSubscription.COMMAND_RESPONSE_ENDPOINT_SHORT);
    }

    private static String getErrorEndpoint() {
        return ErrorSubscription.ERROR_ENDPOINT;
    }

    private static String getErrorEndpointShort() {
        return ErrorSubscription.ERROR_ENDPOINT_SHORT;
    }

}
