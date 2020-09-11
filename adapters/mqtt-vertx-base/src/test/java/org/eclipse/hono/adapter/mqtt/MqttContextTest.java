/**
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * Verifies behavior of {@link MqttContext}.
 *
 */
public class MqttContextTest {

    private Span span;

    /**
     * Setups the fixture.
     */
    @BeforeEach
    public void setup() {
        span = mock(Span.class);
        final SpanContext spanContext = mock(SpanContext.class);
        when(span.context()).thenReturn(spanContext);
    }

    /**
     * Verifies that the tenant is determined from the authenticated device.
     */
    @Test
    public void testTenantIsRetrievedFromAuthenticatedDevice() {
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("t");
        final Device device = new Device("tenant", "device");
        final MqttContext context = MqttContext.fromPublishPacket(msg, mock(MqttEndpoint.class), span, device);
        assertEquals("tenant", context.tenant());
    }

    /**
     * Verifies that the tenant is determined from the published message's topic if
     * the device has not been authenticated.
     */
    @Test
    public void testTenantIsRetrievedFromTopic() {
        final MqttPublishMessage msg = newMessage(TelemetryConstants.TELEMETRY_ENDPOINT_SHORT, "tenant", "device");
        final MqttContext context = MqttContext.fromPublishPacket(msg, mock(MqttEndpoint.class), span);
        assertEquals("tenant", context.tenant());
    }

    /**
     * Verifies the values retrieved from the <em>property-bag</em> of a message's topic.
     */
    @Test
    public void verifyPropertyBagRetrievedFromTopic() {
        final Device device = new Device("tenant", "device");
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("event/tenant/device/?param1=value1&param2=value2");
        final MqttContext context = MqttContext.fromPublishPacket(msg, mock(MqttEndpoint.class), span, device);

        assertNotNull(context.propertyBag());
        assertEquals("value1", context.propertyBag().getProperty("param1"));
        assertEquals("value2", context.propertyBag().getProperty("param2"));
        assertEquals("event/tenant/device", context.topic().toString());
    }

    /**
     * Verifies that the factory method expands short names of endpoints.
     */
    @Test
    public void testFactoryMethodExpandsShortNames() {

        assertEndpoint(
                newMessage(TelemetryConstants.TELEMETRY_ENDPOINT_SHORT, "tenant", "device"),
                MetricsTags.EndpointType.TELEMETRY,
                span);
        assertEndpoint(
                newMessage(EventConstants.EVENT_ENDPOINT_SHORT, "tenant", "device"),
                MetricsTags.EndpointType.EVENT,
                span);
        assertEndpoint(
                newMessage(CommandConstants.COMMAND_ENDPOINT_SHORT, "tenant", "device"),
                MetricsTags.EndpointType.COMMAND,
                span);
    }

    private static void assertEndpoint(final MqttPublishMessage msg, final MetricsTags.EndpointType expectedEndpoint, final Span span) {
        final MqttContext context = MqttContext.fromPublishPacket(msg, mock(MqttEndpoint.class), span);
        assertEquals(expectedEndpoint, context.endpoint());
    }

    private static MqttPublishMessage newMessage(final String endpoint, final String tenant, final String device) {

        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn(String.format("%s/%s/%s", endpoint, tenant, device));
        return msg;
    }
}
