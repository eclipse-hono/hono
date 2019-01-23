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
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;
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
        final MqttPublishMessage msg = newMessage(TelemetryConstants.TELEMETRY_ENDPOINT_SHORT, "tenant", "device");
        final MqttContext context = MqttContext.fromPublishPacket(msg, mock(MqttEndpoint.class));
        assertThat(context.tenant(), is("tenant"));
    }

    /**
     * Verifies that the factory method expands short names of endpoints.
     */
    @Test
    public void testFactoryMethodExpandsShortNames() {

        assertEndpoint(
                newMessage(TelemetryConstants.TELEMETRY_ENDPOINT_SHORT, "tenant", "device"),
                TelemetryConstants.TELEMETRY_ENDPOINT);
        assertEndpoint(
                newMessage(EventConstants.EVENT_ENDPOINT_SHORT, "tenant", "device"),
                EventConstants.EVENT_ENDPOINT);
        assertEndpoint(
                newMessage(CommandConstants.COMMAND_ENDPOINT_SHORT, "tenant", "device"),
                CommandConstants.COMMAND_ENDPOINT);
    }

    private static void assertEndpoint(final MqttPublishMessage msg, final String expectedEndpoint) {
        final MqttContext context = MqttContext.fromPublishPacket(msg, mock(MqttEndpoint.class));
        assertThat(context.endpoint(), is(expectedEndpoint));
    }

    private static MqttPublishMessage newMessage(final String endpoint, final String tenant, final String device) {

        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn(String.format("%s/%s/%s", endpoint, tenant, device));
        return msg;
    }
}
