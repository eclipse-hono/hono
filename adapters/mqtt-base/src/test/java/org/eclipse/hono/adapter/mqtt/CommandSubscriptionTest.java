/**
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.util.CommandConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.impl.MqttTopicSubscriptionImpl;

/**
 * Verifies behavior of {@link CommandSubscription}.
 *
 */
public class CommandSubscriptionTest {

    private final Device device = new Device("tenant", "device");
    private final Device gw = new Device("tenant", "gw");

    static Stream<String> endpointNames() {
        return Stream.of(getCommandEndpoint(), getCommandEndpointShort());
    }

    static Stream<Arguments> endpointAndReqNames() {
        return Stream.of(
                Arguments.of(getCommandEndpoint(), CommandConstants.COMMAND_RESPONSE_REQUEST_PART),
                Arguments.of(getCommandEndpointShort(), CommandConstants.COMMAND_RESPONSE_REQUEST_PART),
                Arguments.of(getCommandEndpoint(), CommandConstants.COMMAND_RESPONSE_REQUEST_PART_SHORT),
                Arguments.of(getCommandEndpointShort(), CommandConstants.COMMAND_RESPONSE_REQUEST_PART_SHORT));
    }

    static Stream<Arguments> endpointAndReqNamesWithQoS() {
        return Stream.of(
                Arguments.of(getCommandEndpoint(), CommandConstants.COMMAND_RESPONSE_REQUEST_PART, MqttQoS.AT_MOST_ONCE),
                Arguments.of(getCommandEndpoint(), CommandConstants.COMMAND_RESPONSE_REQUEST_PART, MqttQoS.AT_LEAST_ONCE),
                Arguments.of(getCommandEndpointShort(), CommandConstants.COMMAND_RESPONSE_REQUEST_PART, MqttQoS.AT_MOST_ONCE),
                Arguments.of(getCommandEndpointShort(), CommandConstants.COMMAND_RESPONSE_REQUEST_PART, MqttQoS.AT_LEAST_ONCE),
                Arguments.of(getCommandEndpoint(), CommandConstants.COMMAND_RESPONSE_REQUEST_PART_SHORT, MqttQoS.AT_MOST_ONCE),
                Arguments.of(getCommandEndpoint(), CommandConstants.COMMAND_RESPONSE_REQUEST_PART_SHORT, MqttQoS.AT_LEAST_ONCE),
                Arguments.of(getCommandEndpointShort(), CommandConstants.COMMAND_RESPONSE_REQUEST_PART_SHORT, MqttQoS.AT_MOST_ONCE),
                Arguments.of(getCommandEndpointShort(), CommandConstants.COMMAND_RESPONSE_REQUEST_PART_SHORT, MqttQoS.AT_LEAST_ONCE));
    }

    /**
     * Verifies that creating a subscription for a topic fails if the endpoint is neither
     * {@value CommandConstants#COMMAND_ENDPOINT} nor {@value CommandConstants#COMMAND_ENDPOINT_SHORT}.
     */
    @Test
    public void testSubscriptionRequiresCorrectEndpoint() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("cx/tenant/device/req/#", MqttQoS.AT_MOST_ONCE, null);
        assertThat(subscription).isNull();
    }

    /**
     * Verifies that creating a subscription for a topic fails if the client is unauthenticated and neither
     * tenant nor device ID are specified explicitly.
     *
     * @param endpointName The endpoint name used in the topic.
     * @param reqPartName The request part name used in the topic.
     */
    @ParameterizedTest
    @MethodSource("endpointAndReqNames")
    public void testSubscriptionRequiresExplicitTenantAndDeviceIdForUnauthenticatedClient(
            final String endpointName,
            final String reqPartName) {

        String topic = String.format("%s///%s/#", endpointName, reqPartName);
        assertThat(CommandSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, null)).isNull();

        topic = String.format("%s//+/%s/#", endpointName, reqPartName);
        assertThat(CommandSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, null)).isNull();
    }

    /**
     * Verifies that creating a subscription for a topic fails if the request part in the topic is neither
     * {@value CommandConstants#COMMAND_RESPONSE_REQUEST_PART} nor {@value CommandConstants#COMMAND_RESPONSE_REQUEST_PART_SHORT}.
     *
     * @param endpointName The endpoint name used in the topic.
     */
    @ParameterizedTest
    @MethodSource("endpointNames")
    public void testSubscriptionRequiresValidReqPart(final String endpointName) {

        String topic = String.format("%s/tenant/device/notReqNorQ/#", endpointName);
        assertThat(CommandSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, null)).isNull();

        topic = String.format("%s///notReqNorQ/#", endpointName);
        assertThat(CommandSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, device)).isNull();

        topic = String.format("%s//+/notReqNorQ/#", endpointName);
        assertThat(CommandSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, device)).isNull();
    }

    /**
     * Verifies that creating a subscription for a topic fails if the topic ends in something else than the <em>#</em>
     * character.
     *
     * @param endpointName The endpoint name used in the topic.
     * @param reqPartName The request part name used in the topic.
     */
    @ParameterizedTest
    @MethodSource("endpointAndReqNames")
    public void testSubscriptionRequiresWildcardSegmentAtEnd(
            final String endpointName,
            final String reqPartName) {

        String topic = String.format("%s/tenant/device/%s/not#", endpointName, reqPartName);
        assertThat(CommandSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, null)).isNull();

        topic = String.format("%s///%s/not#", endpointName, reqPartName);
        assertThat(CommandSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, device)).isNull();

        topic = String.format("%s//+/%s/not#", endpointName, reqPartName);
        assertThat(CommandSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, device)).isNull();
    }

    /**
     * Verifies that creating a subscription for a topic fails if the topic contains
     * fewer or more than 5 segments.
     *
     * @param endpointName The endpoint name used in the topic.
     * @param reqPartName The request part name used in the topic.
     */
    @ParameterizedTest
    @MethodSource("endpointAndReqNames")
    public void testSubscriptionRequiresTopicWithFiveSegments(
            final String endpointName,
            final String reqPartName) {

        String topic = String.format("%s/tenant/device/%s/#/additionalSegment", endpointName, reqPartName);
        assertThat(CommandSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, null)).isNull();

        topic = String.format("%s/tenant/device/%s", endpointName, reqPartName);
        assertThat(CommandSubscription.fromTopic(topic, MqttQoS.AT_MOST_ONCE, null)).isNull();
    }

    /**
     * Verifies that topic filters containing the {@code +} wild card character for the tenant ID are not supported.
     *
     * @param topicFilter The topic filter to test.
     */
    @ParameterizedTest
    @ValueSource(strings = { "command/+/+/req/#", "command/+//req/#", "command/+/device-id/req/#" })
    public void testSubscriptionFailsForWildcardTenantId(final String topicFilter) {

        final var topicSubscription = new MqttTopicSubscriptionImpl(topicFilter, MqttQoS.AT_LEAST_ONCE);
        assertThat(CommandSubscription.fromTopic(topicSubscription, null)).isNull();
    }

    /**
     * Verifies that an unauthenticated device can successfully subscribe for commands
     * using the default topic.
     *
     * @param endpointName The endpoint name used in the topic.
     * @param reqPartName The request part name used in the topic.
     * @param qos The requested QoS.
     */
    @ParameterizedTest
    @MethodSource("endpointAndReqNamesWithQoS")
    public void testSubscriptionSucceedsForUnauthenticatedDevice(
            final String endpointName,
            final String reqPartName,
            final MqttQoS qos) {

        final MqttTopicSubscription mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s/tenant1/deviceA/%s/#", endpointName, reqPartName),
                qos);

        final CommandSubscription subscription = CommandSubscription.fromTopic(mqttTopicSubscription, null);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo("tenant1");
        assertThat(subscription.getDeviceId()).isEqualTo("deviceA");
        assertThat(subscription.getEndpoint()).isEqualTo(endpointName);
        assertThat(subscription.getRequestPart()).isEqualTo(reqPartName);
        assertThat(subscription.getQos()).isEqualTo(qos);
    }

    /**
     * Verifies that an authenticated device can successfully subscribe for commands
     * targeted at itself using all variants of topic names.
     *
     * @param endpointName The endpoint name used in the topic.
     * @param reqPartName The request part name used in the topic.
     * @param qos The requested QoS.
     */
    @ParameterizedTest
    @MethodSource("endpointAndReqNamesWithQoS")
    public void testSubscriptionSucceedsForAuthenticatedDevice(
            final String endpointName,
            final String reqPartName,
            final MqttQoS qos) {

        final Command command = mock(Command.class);
        when(command.isTargetedAtGateway()).thenReturn(false);
        when(command.getTenant()).thenReturn(device.getTenantId());
        when(command.getGatewayOrDeviceId()).thenReturn(device.getDeviceId());
        when(command.getRequestId()).thenReturn("requestId");
        when(command.getName()).thenReturn("doSomething");

        // WHEN subscribing to commands using explicit topic
        MqttTopicSubscription mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s/tenant/device/%s/#", endpointName, reqPartName),
                qos);

        CommandSubscription subscription = CommandSubscription.fromTopic(mqttTopicSubscription, device);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo("tenant");
        assertThat(subscription.getDeviceId()).isEqualTo("device");
        assertThat(subscription.getEndpoint()).isEqualTo(endpointName);
        assertThat(subscription.getRequestPart()).isEqualTo(reqPartName);
        assertThat(subscription.getQos()).isEqualTo(qos);
        // THEN the command topic does include both the tenant and device ID
        assertThat(subscription.getCommandPublishTopic(command))
            .isEqualTo(String.format("%s/%s/%s/%s/requestId/doSomething",
                    endpointName, device.getTenantId(), device.getDeviceId(), reqPartName));

        // WHEN subscribing to commands including tenant only
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s/tenant//%s/#", endpointName, reqPartName),
                qos);

        subscription = CommandSubscription.fromTopic(mqttTopicSubscription, device);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo("tenant");
        assertThat(subscription.getDeviceId()).isEqualTo("device");
        assertThat(subscription.getEndpoint()).isEqualTo(endpointName);
        assertThat(subscription.getRequestPart()).isEqualTo(reqPartName);
        assertThat(subscription.getQos()).isEqualTo(qos);
        // THEN the command topic does include the tenant as well
        assertThat(subscription.getCommandPublishTopic(command))
            .isEqualTo(String.format("%s/%s//%s/requestId/doSomething",
                    endpointName, device.getTenantId(), reqPartName));

        // WHEN subscribing to commands including device ID only
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s//device/%s/#", endpointName, reqPartName),
                qos);

        subscription = CommandSubscription.fromTopic(mqttTopicSubscription, device);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo("tenant");
        assertThat(subscription.getDeviceId()).isEqualTo("device");
        assertThat(subscription.getEndpoint()).isEqualTo(endpointName);
        assertThat(subscription.getRequestPart()).isEqualTo(reqPartName);
        assertThat(subscription.getQos()).isEqualTo(qos);
        // THEN the command topic does include the device ID as well
        assertThat(subscription.getCommandPublishTopic(command))
            .isEqualTo(String.format("%s//%s/%s/requestId/doSomething",
                    endpointName, device.getDeviceId(), reqPartName));

        // WHEN subscribing to commands using implicit topic
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s///%s/#", endpointName, reqPartName),
                qos);

        subscription = CommandSubscription.fromTopic(mqttTopicSubscription, device);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo("tenant");
        assertThat(subscription.getDeviceId()).isEqualTo("device");
        assertThat(subscription.getEndpoint()).isEqualTo(endpointName);
        assertThat(subscription.getRequestPart()).isEqualTo(reqPartName);
        assertThat(subscription.getQos()).isEqualTo(qos);
        // THEN the command topic does not include tenant nor device ID
        assertThat(subscription.getCommandPublishTopic(command))
            .isEqualTo(String.format("%s///%s/requestId/doSomething",
                    endpointName, reqPartName));

        // using a tenant other than the tenant that the device belongs to should fail
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s/otherTenant/device/%s/#", endpointName, reqPartName),
                qos);
        assertThat(CommandSubscription.fromTopic(mqttTopicSubscription, device)).isNull();
    }

    /**
     * Verifies that an authenticated gateway can successfully subscribe for commands
     * targeted at one of devices that it is authorized to act on behalf of.
     *
     * @param endpointName The endpoint name used in the topic.
     * @param reqPartName The request part name used in the topic.
     * @param qos The requested QoS.
     */
    @ParameterizedTest
    @MethodSource("endpointAndReqNamesWithQoS")
    public void testSubscriptionSucceedsForAuthenticatedGateway(
            final String endpointName,
            final String reqPartName,
            final MqttQoS qos) {

        final String gatewayManagedDeviceId = "gatewayManagedDevice";

        final Command command = mock(Command.class);
        when(command.isTargetedAtGateway()).thenReturn(true);
        when(command.getTenant()).thenReturn(gw.getTenantId());
        when(command.getGatewayId()).thenReturn(gw.getDeviceId());
        when(command.getGatewayOrDeviceId()).thenReturn(gw.getDeviceId());
        when(command.getDeviceId()).thenReturn(gatewayManagedDeviceId);
        when(command.getRequestId()).thenReturn("requestId");
        when(command.getName()).thenReturn("doSomething");

        // command directed at gateway-device itself, i.e. no gateway-mapping happens here...
        final Command commandDirectedAtGatewayItself = mock(Command.class);
        // ... meaning the command target itself has no association with the Hono 'gateway' term
        when(commandDirectedAtGatewayItself.isTargetedAtGateway()).thenReturn(false);
        when(commandDirectedAtGatewayItself.getTenant()).thenReturn(gw.getTenantId());
        when(commandDirectedAtGatewayItself.getGatewayId()).thenReturn(null);
        when(commandDirectedAtGatewayItself.getGatewayOrDeviceId()).thenReturn(gw.getDeviceId());
        when(commandDirectedAtGatewayItself.getDeviceId()).thenReturn(gw.getDeviceId());
        when(commandDirectedAtGatewayItself.getRequestId()).thenReturn("requestId");
        when(commandDirectedAtGatewayItself.getName()).thenReturn("doSomething");

        // WHEN subscribing to commands for a specific device omitting tenant
        MqttTopicSubscription mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s//%s/%s/#", endpointName, gatewayManagedDeviceId, reqPartName),
                qos);

        CommandSubscription subscription = CommandSubscription.fromTopic(mqttTopicSubscription, gw);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(gw.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo(gatewayManagedDeviceId);
        assertThat(subscription.getAuthenticatedDeviceId()).isEqualTo(gw.getDeviceId());
        assertThat(subscription.getQos()).isEqualTo(qos);
        assertThat(subscription.isGatewaySubscriptionForSpecificDevice()).isEqualTo(true);
        assertThat(subscription.isGatewaySubscriptionForAllDevices()).isEqualTo(false);
        // THEN the command topic does not include the tenant either
        assertThat(subscription.getCommandPublishTopic(command))
            .isEqualTo(String.format("%s//%s/%s/requestId/doSomething",
                    endpointName, gatewayManagedDeviceId, reqPartName));

        // WHEN subscribing to commands for a specific device including the tenant
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s/%s/%s/%s/#", endpointName, device.getTenantId(), gatewayManagedDeviceId, reqPartName),
                qos);

        subscription = CommandSubscription.fromTopic(mqttTopicSubscription, gw);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(gw.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo(gatewayManagedDeviceId);
        assertThat(subscription.getAuthenticatedDeviceId()).isEqualTo(gw.getDeviceId());
        assertThat(subscription.getQos()).isEqualTo(qos);
        assertThat(subscription.isGatewaySubscriptionForSpecificDevice()).isEqualTo(true);
        assertThat(subscription.isGatewaySubscriptionForAllDevices()).isEqualTo(false);
        // THEN the command topic does include the tenant as well
        assertThat(subscription.getCommandPublishTopic(command))
            .isEqualTo(String.format("%s/%s/%s/%s/requestId/doSomething",
                    endpointName, gw.getTenantId(), gatewayManagedDeviceId, reqPartName));

        // WHEN subscribing to commands for all devices omitting tenant
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s//+/%s/#", endpointName, reqPartName),
                qos);

        subscription = CommandSubscription.fromTopic(mqttTopicSubscription, gw);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(gw.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo(gw.getDeviceId());
        assertThat(subscription.getAuthenticatedDeviceId()).isEqualTo(gw.getDeviceId());
        assertThat(subscription.getQos()).isEqualTo(qos);
        assertThat(subscription.isGatewaySubscriptionForSpecificDevice()).isEqualTo(false);
        assertThat(subscription.isGatewaySubscriptionForAllDevices()).isEqualTo(true);
        // THEN the command topic does not include the tenant either
        assertThat(subscription.getCommandPublishTopic(command))
            .isEqualTo(String.format("%s//%s/%s/requestId/doSomething",
                    endpointName, gatewayManagedDeviceId, reqPartName));

        // WHEN subscribing to commands for all devices including the tenant
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s/%s/+/%s/#", endpointName, device.getTenantId(), reqPartName),
                qos);

        subscription = CommandSubscription.fromTopic(mqttTopicSubscription, gw);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(gw.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo(gw.getDeviceId());
        assertThat(subscription.getAuthenticatedDeviceId()).isEqualTo(gw.getDeviceId());
        assertThat(subscription.getQos()).isEqualTo(qos);
        assertThat(subscription.isGatewaySubscriptionForSpecificDevice()).isEqualTo(false);
        assertThat(subscription.isGatewaySubscriptionForAllDevices()).isEqualTo(true);
        // THEN the command topic does include the tenant as well
        assertThat(subscription.getCommandPublishTopic(command))
            .isEqualTo(String.format("%s/%s/%s/%s/requestId/doSomething",
                    endpointName, gw.getTenantId(), gatewayManagedDeviceId, reqPartName));
        // AND THEN handling a command directed at the gateway itself
        // means the command topic includes an empty string in place of the target device id
        assertThat(subscription.getCommandPublishTopic(commandDirectedAtGatewayItself))
                .isEqualTo(String.format("%s/%s/%s/%s/requestId/doSomething",
                        endpointName, gw.getTenantId(), "", reqPartName));

        // WHEN using a tenant other than the tenant that the gateway belongs
        mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                String.format("%s/otherTenant/+/%s/#", endpointName, reqPartName),
                qos);
        // THEN subscription creation should fail
        assertThat(CommandSubscription.fromTopic(mqttTopicSubscription, gw)).isNull();
    }

    private static String getCommandEndpoint() {
        return CommandConstants.COMMAND_ENDPOINT;
    }

    private static String getCommandEndpointShort() {
        return CommandConstants.COMMAND_ENDPOINT_SHORT;
    }

}
