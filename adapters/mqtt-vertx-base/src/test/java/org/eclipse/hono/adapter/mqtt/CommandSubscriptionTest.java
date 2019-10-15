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
        final CommandSubscription subscription = CommandSubscription.fromTopic("c/tenant1/deviceA/q/#", null);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo("tenant1");
        assertThat(subscription.getDeviceId()).isEqualTo("deviceA");
        assertThat(subscription.getEndpoint()).isEqualTo(CommandConstants.COMMAND_ENDPOINT_SHORT);
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
        final CommandSubscription subscription = CommandSubscription.fromTopic("c/tenant/device/q/#", device);
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
    }

    /**
     * Verifies subscription pattern with authenticated device omitting tenant and device IDs.
     */
    @Test
    public void testSubscriptionAuthWithoutTenantDevice() {
        final CommandSubscription subscription = CommandSubscription.fromTopic(getCommandEndpoint() + "///req/#", device);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant()).isEqualTo(device.getTenantId());
        assertThat(subscription.getDeviceId()).isEqualTo(device.getDeviceId());
        assertThat(subscription.getEndpoint()).isEqualTo(getCommandEndpoint());
        assertThat(subscription.getRequestPart()).isEqualTo(CommandConstants.COMMAND_RESPONSE_REQUEST_PART);
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
        final CommandSubscription subscription = CommandSubscription.fromTopic("c/tenant/device/qx/#", null);
        assertThat(subscription).isNull();
    }

    /**
     * Verifies subscription pattern with other ending part as # is not allowed.
     */
    @Test
    public void testSubscriptionEnd() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("c/tenant/device/q/a", null);
        assertThat(subscription).isNull();
    }

    /**
     * Verifies subscription pattern with other then 5 parts is not allowed.
     */
    @Test
    public void testSubscriptionParts() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("c/tenant/device/q/#/x", null);
        assertThat(subscription).isNull();
        final CommandSubscription subscription2 = CommandSubscription.fromTopic("c/tenant/device/q", null);
        assertThat(subscription2).isNull();
    }

    private String getCommandEndpoint() {
        return useLegacyCommandEndpoint() ? CommandConstants.COMMAND_LEGACY_ENDPOINT : CommandConstants.COMMAND_ENDPOINT;
    }

    /**
     * Checks whether the legacy Command & Control endpoint shall be used.
     * <p>
     * Returns {@code false} by default. Subclasses may return {@code true} here to perform tests using the legacy
     * command endpoint.
     *
     * @return {@code true} if the legacy command endpoint shall be used.
     */
    protected boolean useLegacyCommandEndpoint() {
        return false;
    }
}
