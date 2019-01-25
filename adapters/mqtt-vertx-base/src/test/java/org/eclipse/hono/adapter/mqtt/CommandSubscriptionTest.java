/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.impl.MqttTopicSubscriptionImpl;
import org.eclipse.hono.auth.Device;
import org.junit.Test;

/**
 * Verifies behavior of {@link CommandSubscription}.
 *
 */
public class CommandSubscriptionTest {

    private Device device = new Device("tenant", "device");

    /**
     * Verifies subscription pattern without authenticated device and correct pattern.
     */
    @Test
    public void testSubscriptionUnauth() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("control/tenant/device/req/#", null);
        assertNotNull(subscription);
        assertThat(subscription.getDeviceId(), is("device"));
        assertThat(subscription.getTenant(), is("tenant"));
        assertThat(subscription.getEndpoint(), is("control"));
        assertThat(subscription.getRequestPart(), is("req"));
    }

    /**
     * Verifies subscription pattern without authenticated device and correct short pattern.
     */
    @Test
    public void testSubscriptionUnauthShort() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("c/tenant/device/q/#", null);
        assertNotNull(subscription);
        assertThat(subscription.getDeviceId(), is("device"));
        assertThat(subscription.getTenant(), is("tenant"));
        assertThat(subscription.getEndpoint(), is("c"));
        assertThat(subscription.getRequestPart(), is("q"));
    }

    /**
     * Verifies subscription pattern with authenticated device and correct pattern.
     */
    @Test
    public void testSubscriptionAuth() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("control/tenant/device/req/#", device);
        assertNotNull(subscription);
    }

    /**
     * Verifies subscription pattern with authenticated device, correct pattern and valid qos.
     */
    @Test
    public void testSubscriptionAuthWithQoS() {
        final MqttTopicSubscription mqttTopicSubscription = new MqttTopicSubscriptionImpl("control/tenant/device/req/#",
                MqttQoS.AT_LEAST_ONCE);
        final CommandSubscription subscription = CommandSubscription.fromTopic(mqttTopicSubscription, device,
                "testMqttClient");
        assertNotNull(subscription);
        assertThat(subscription.getDeviceId(), is("device"));
        assertThat(subscription.getTenant(), is("tenant"));
        assertThat(subscription.getTopic(), is("control/tenant/device/req/#"));
        assertThat(subscription.getQos(), is(MqttQoS.AT_LEAST_ONCE));
        assertThat(subscription.getClientId(), is("testMqttClient"));
    }

    /**
     * Verifies subscription pattern with authenticated device and correct short pattern.
     */
    @Test
    public void testSubscriptionAuthShort() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("c/tenant/device/q/#", device);
        assertNotNull(subscription);
    }

    /**
     * Verifies subscription pattern with authenticated device and correct pattern with different tenant/device as in
     * authentication is not allowed.
     */
    @Test
    public void testSubscriptionAuthDeviceDifferent() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("control/tenantA/deviceB/req/#", device);
        assertNull(subscription);
    }

    /**
     * Verifies subscription pattern with authenticated device and correct pattern without given tenant/device.
     */
    @Test
    public void testSubscriptionAuthWithPattern() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("control/+/+/req/#", device);
        assertNotNull(subscription);
        assertThat(subscription.getDeviceId(), is("device"));
        assertThat(subscription.getTenant(), is("tenant"));
    }

    /**
     * Verifies subscription pattern without authenticated device and not given tenant/device is not allowed.
     */
    @Test
    public void testSubscriptionUnauthWithPattern() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("control/+/+/req/#", null);
        assertNull(subscription);
    }

    /**
     * Verifies subscription pattern with other endpoint as c and control is not allowed.
     */
    @Test
    public void testSubscriptionEndpoint() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("cx/tenant/device/q/#", null);
        assertNull(subscription);
    }

    /**
     * Verifies subscription pattern with other req part as q and req is not allowed.
     */
    @Test
    public void testSubscriptionReq() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("c/tenant/device/qx/#", null);
        assertNull(subscription);
    }

    /**
     * Verifies subscription pattern with other ending part as # is not allowed.
     */
    @Test
    public void testSubscriptionEnd() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("c/tenant/device/q/a", null);
        assertNull(subscription);
    }

    /**
     * Verifies subscription pattern with other then 5 parts is not allowed.
     */
    @Test
    public void testSubscriptionParts() {
        final CommandSubscription subscription = CommandSubscription.fromTopic("c/tenant/device/q/#/x", null);
        assertNull(subscription);
        final CommandSubscription subscription2 = CommandSubscription.fromTopic("c/tenant/device/q", null);
        assertNull(subscription2);
    }

}
