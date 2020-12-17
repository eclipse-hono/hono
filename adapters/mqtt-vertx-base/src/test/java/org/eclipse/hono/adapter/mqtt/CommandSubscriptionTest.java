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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.eclipse.hono.adapter.client.command.Command;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.util.CommandConstants;
import org.junit.jupiter.api.Test;

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

    /**
     * Verifies subscription pattern without authenticated device and correct pattern.
     */
    @Test
    public void testSubscriptionUnauth() {

        final CommandSubscription subscription = CommandSubscription.fromTopic(getCommandEndpoint() + "/tenant1/deviceA/req/#", null);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo("tenant1");
        assertThat(subscription.getDeviceId()).isEqualTo("deviceA");
        assertThat(subscription.getEndpoint()).isEqualTo(getCommandEndpoint());
        assertThat(subscription.getRequestPart()).isEqualTo("req");
    }

    /**
     * Verifies subscription pattern without authenticated device and correct short pattern.
     */
    @Test
    public void testSubscriptionUnauthShort() {
        final CommandSubscription subscription = CommandSubscription.fromTopic(getCommandEndpointShort() + "/tenant1/deviceA/q/#", null);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo("tenant1");
        assertThat(subscription.getDeviceId()).isEqualTo("deviceA");
        assertThat(subscription.getEndpoint()).isEqualTo(getCommandEndpointShort());
        assertThat(subscription.getRequestPart()).isEqualTo(CommandConstants.COMMAND_RESPONSE_REQUEST_PART_SHORT);
    }

    /**
     * Verifies subscription pattern with authenticated device and correct pattern.
     */
    @Test
    public void testSubscriptionAuth() {
        final CommandSubscription subscription = CommandSubscription.fromTopic(getCommandEndpoint() + "/tenant/device/req/#", device);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo("tenant");
        assertThat(subscription.getDeviceId()).isEqualTo("device");
    }

    /**
     * Verifies subscription pattern with authenticated device, correct pattern and valid qos.
     */
    @Test
    public void testSubscriptionAuthWithQoS() {
        final MqttTopicSubscription mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                getCommandEndpoint() + "/+/+/req/#", MqttQoS.AT_LEAST_ONCE);
        final CommandSubscription subscription = CommandSubscription.fromTopic(mqttTopicSubscription, device,
                "testMqttClient");
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(device.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo(device.getDeviceId());
        assertThat(subscription.getTopic()).isEqualTo(getCommandEndpoint() + "/+/+/req/#");
        assertThat(subscription.getQos()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
        assertThat(subscription.getClientId()).isEqualTo("testMqttClient");
    }

    /**
     * Verifies subscription pattern with auth device differing device id in topic.
     * This represents a scenario where a gateway subscribes on behalf of a device.
     */
    @Test
    public void testSubscriptionAuthWithDifferentDeviceId() {
        final String gatewayManagedDeviceId = "gatewayManagedDevice";
        final MqttTopicSubscription mqttTopicSubscription = new MqttTopicSubscriptionImpl(
                getCommandEndpoint() + "//" + gatewayManagedDeviceId + "/req/#", MqttQoS.AT_LEAST_ONCE);
        final CommandSubscription subscription = CommandSubscription.fromTopic(mqttTopicSubscription, device,
                "testMqttClient");
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(device.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo(gatewayManagedDeviceId);
        assertThat(subscription.getAuthenticatedDeviceId()).isEqualTo(device.getDeviceId());
        assertThat(subscription.getTopic()).isEqualTo(getCommandEndpoint() + "//" + gatewayManagedDeviceId + "/req/#");
        assertThat(subscription.getQos()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
        assertThat(subscription.getClientId()).isEqualTo("testMqttClient");
        assertThat(subscription.isGatewaySubscriptionForSpecificDevice()).isEqualTo(true);
    }

    /**
     * Verifies subscription pattern with authenticated device and correct short pattern.
     */
    @Test
    public void testSubscriptionAuthShort() {
        final CommandSubscription subscription = CommandSubscription.fromTopic(getCommandEndpointShort() + "/tenant/device/q/#", device);
        assertThat(subscription).isNotNull();
    }

    /**
     * Verifies subscription pattern with authenticated device and correct pattern with different tenant/device as in
     * authentication is not allowed.
     */
    @Test
    public void testSubscriptionAuthDeviceDifferent() {
        final CommandSubscription subscription = CommandSubscription.fromTopic(getCommandEndpoint() + "/tenantA/deviceB/req/#", device);
        assertThat(subscription).isNull();
    }

    /**
     * Verifies subscription pattern with authenticated device and correct pattern without given tenant/device.
     */
    @Test
    public void testSubscriptionAuthWithPattern() {
        final CommandSubscription subscription = CommandSubscription.fromTopic(getCommandEndpoint() + "/+/+/req/#", device);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(device.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo(device.getDeviceId());
        assertThat(subscription.getEndpoint()).isEqualTo(getCommandEndpoint());
        assertThat(subscription.getRequestPart()).isEqualTo(CommandConstants.COMMAND_RESPONSE_REQUEST_PART);

        final Command command = mock(Command.class);
        when(command.getTenant()).thenReturn(device.getTenantId());
        when(command.getDeviceId()).thenReturn(device.getDeviceId());
        when(command.getRequestId()).thenReturn("requestId");
        when(command.getName()).thenReturn("doSomething");
        assertThat(subscription.getCommandPublishTopic(command))
            .isEqualTo("command///req/requestId/doSomething");
    }

    /**
     * Verifies subscription pattern with authenticated device subscribing to commands targeted at itself.
     */
    @Test
    public void testDeviceSubscriptionForItself() {
        final CommandSubscription subscription = CommandSubscription.fromTopic(getCommandEndpoint() + "///req/#", device);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(device.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo(device.getDeviceId());
        assertThat(subscription.getEndpoint()).isEqualTo(getCommandEndpoint());
        assertThat(subscription.getRequestPart()).isEqualTo(CommandConstants.COMMAND_RESPONSE_REQUEST_PART);

        final Command command = mock(Command.class);
        when(command.getTenant()).thenReturn(device.getTenantId());
        when(command.getDeviceId()).thenReturn(device.getDeviceId());
        when(command.getRequestId()).thenReturn("requestId");
        when(command.getName()).thenReturn("doSomething");
        assertThat(subscription.getCommandPublishTopic(command))
            .isEqualTo("command///req/requestId/doSomething");
    }

    /**
     * Verifies subscription pattern with authenticated gateway for a particular device.
     */
    @Test
    public void testGatewaySubscriptionForOneDevice() {
        final CommandSubscription subscription = CommandSubscription.fromTopic(getCommandEndpoint() + "//deviceA/req/#", gw);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(gw.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo("deviceA");
        assertThat(subscription.getEndpoint()).isEqualTo(getCommandEndpoint());
        assertThat(subscription.getRequestPart()).isEqualTo(CommandConstants.COMMAND_RESPONSE_REQUEST_PART);

        final Command command = mock(Command.class);
        when(command.isTargetedAtGateway()).thenReturn(true);
        when(command.getTenant()).thenReturn(gw.getTenantId());
        when(command.getDeviceId()).thenReturn(gw.getDeviceId());
        when(command.getOriginalDeviceId()).thenReturn("deviceA");
        when(command.getRequestId()).thenReturn("requestId");
        when(command.getName()).thenReturn("doSomething");
        assertThat(subscription.getCommandPublishTopic(command))
            .isEqualTo("command//deviceA/req/requestId/doSomething");
    }

    /**
     * Verifies subscription pattern with authenticated gateway for all its devices.
     */
    @Test
    public void testGatewaySubscriptionForAllDevices() {
        final CommandSubscription subscription = CommandSubscription.fromTopic(getCommandEndpoint() + "//+/req/#", gw);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(gw.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo(gw.getDeviceId());
        assertThat(subscription.getEndpoint()).isEqualTo(getCommandEndpoint());
        assertThat(subscription.getRequestPart()).isEqualTo(CommandConstants.COMMAND_RESPONSE_REQUEST_PART);

        final Command oneWayCommand = mock(Command.class);
        when(oneWayCommand.isTargetedAtGateway()).thenReturn(true);
        when(oneWayCommand.getTenant()).thenReturn(device.getTenantId());
        when(oneWayCommand.getDeviceId()).thenReturn(gw.getDeviceId());
        when(oneWayCommand.getOriginalDeviceId()).thenReturn(device.getDeviceId());
        when(oneWayCommand.getRequestId()).thenReturn("");
        when(oneWayCommand.getName()).thenReturn("doSomething");
        assertThat(subscription.getCommandPublishTopic(oneWayCommand))
            .isEqualTo(String.format("command//%s/req//doSomething", device.getDeviceId()));
    }

    /**
     * Verifies subscription pattern without authenticated device and not given tenant/device is not allowed.
     */
    @Test
    public void testSubscriptionUnauthWithPattern() {
        final CommandSubscription subscription = CommandSubscription.fromTopic(getCommandEndpoint() + "/+/+/req/#", null);
        assertThat(subscription).isNull();
    }

    /**
     * Verifies subscription pattern with other endpoint as c and control is not allowed.
     */
    @Test
    public void testSubscriptionEndpoint() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("cx/tenant/device/q/#", null);
        assertThat(subscription).isNull();
    }

    /**
     * Verifies subscription pattern with other req part as q and req is not allowed.
     */
    @Test
    public void testSubscriptionReq() {
        final CommandSubscription subscription = CommandSubscription.fromTopic(getCommandEndpointShort() + "/tenant/device/qx/#", null);
        assertThat(subscription).isNull();
    }

    /**
     * Verifies subscription pattern with other ending part as # is not allowed.
     */
    @Test
    public void testSubscriptionEnd() {
        final CommandSubscription subscription = CommandSubscription.fromTopic(getCommandEndpointShort() + "/tenant/device/q/a", null);
        assertThat(subscription).isNull();
    }

    /**
     * Verifies subscription pattern with other then 5 parts is not allowed.
     */
    @Test
    public void testSubscriptionParts() {
        final CommandSubscription subscription = CommandSubscription.fromTopic(getCommandEndpointShort() + "/tenant/device/q/#/x", null);
        assertThat(subscription).isNull();
        final CommandSubscription subscription2 = CommandSubscription.fromTopic(getCommandEndpointShort() + "/tenant/device/q", null);
        assertThat(subscription2).isNull();
    }

    private String getCommandEndpoint() {
        return CommandConstants.COMMAND_ENDPOINT;
    }

    private String getCommandEndpointShort() {
        return CommandConstants.COMMAND_ENDPOINT_SHORT;
    }

}
