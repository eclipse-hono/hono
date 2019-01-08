/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.adapter.mqtt;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.eclipse.hono.auth.Device;
import org.junit.Test;

import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;


/**
 * Verifies behavior of {@link MqttContext}.
 *
 */
public class MqttContextTest {

    /**
     * Verifies that the tenant is determined from the authenticated device.
     */
    @Test
    public void testTenantIsRetrievedFromAuthenticatedDevice() {
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("t");
        final Device device = new Device("tenant", "device");
        final MqttContext context = MqttContext.fromPublishPacket(msg, mock(MqttEndpoint.class), device);
        assertThat(context.tenant(), is("tenant"));
    }

    /**
     * Verifies that the tenant is determined from the published message's topic if
     * the device has not been authenticated.
     */
    @Test
    public void testTenantIsRetrievedFromTopic() {
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("t/tenant/device");
        final MqttContext context = MqttContext.fromPublishPacket(msg, mock(MqttEndpoint.class));
        assertThat(context.tenant(), is("tenant"));
    }
}
