/**
 * Copyright (c) 2019, 2023 Contributors to the Eclipse Foundation
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.opentracing.Span;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;
import org.eclipse.hono.adapter.mqtt.MqttConnectContext; // Added for MqttConnectContext

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
        span = TracingMockSupport.mockSpan();
    }

    /**
     * Verifies that the tenant is determined from the authenticated device.
     */
    @Test
    public void testTenantIsRetrievedFromAuthenticatedDevice() {
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("t");
        final var device = new DeviceUser("tenant", "device");
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
        final var device = new DeviceUser("tenant", "device");
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("event/tenant/device/?param1=value1&param2=value2");
        final MqttContext context = MqttContext.fromPublishPacket(msg, mock(MqttEndpoint.class), span, device);

        assertNotNull(context.propertyBag());
        assertEquals("value1", context.propertyBag().getProperty("param1"));
        assertEquals("value2", context.propertyBag().getProperty("param2"));
        assertEquals("event/tenant/device", context.topic().toString());
    }

    /**
     * Verifies the <em>content-type</em> value retrieved from the <em>property-bag</em> in a message's topic.
     */
    @Test
    public void verifyContentTypeFromPropertyBag() {
        final String contentType = "application/vnd.eclipse.ditto+json";
        final String encodedContentType = URLEncoder.encode(contentType, StandardCharsets.UTF_8);
        final var device = new DeviceUser("tenant", "device");
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        final MqttProperties msgProperties = new MqttProperties();
        msgProperties.add(new MqttProperties.StringProperty(MqttProperties.MqttPropertyType.CONTENT_TYPE.value(), "offending/value+json"));
        when(msg.topicName()).thenReturn(
                String.format("event/tenant/device/?Content-Type=%s&param2=value2&param3=value3", encodedContentType));
        when(msg.properties()).thenReturn(
                msgProperties);
        final MqttContext context = MqttContext.fromPublishPacket(msg, mock(MqttEndpoint.class), span, device);

        assertNotNull(context.propertyBag());
        assertEquals(contentType, context.propertyBag().getProperty(MessageHelper.SYS_PROPERTY_CONTENT_TYPE));
        assertEquals(contentType, context.contentType());
    }

    /**
     * Verifies the <em>content-type</em> value retrieved from the <em>properties</em> in a message.
     */
    @Test
    public void verifyContentTypeFromProperties() {
        final String contentType = "application/vnd.eclipse.ditto+json";
        final var device = new DeviceUser("tenant", "device");
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        final MqttProperties msgProperties = new MqttProperties();
        msgProperties.add(new MqttProperties.StringProperty(MqttProperties.MqttPropertyType.CONTENT_TYPE.value(), contentType));
        when(msg.topicName()).thenReturn(
                "event/tenant/device/?param2=value2&param3=value3");
        when(msg.properties()).thenReturn(
                msgProperties);
        final MqttContext context = MqttContext.fromPublishPacket(msg, mock(MqttEndpoint.class), span, device);

        assertNotNull(context.propertyBag());
        assertNull(context.propertyBag().getProperty(MessageHelper.SYS_PROPERTY_CONTENT_TYPE));
        assertEquals(contentType, context.contentType());
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

    @Test
    public void testCreateContextWithSessionExpiryInterval() {
        final MqttEndpoint mockEndpoint = mock(MqttEndpoint.class);
        final MqttProperties connectProperties = new MqttProperties();
        final int expectedInterval = 3600;
        connectProperties.add(new MqttProperties.IntegerProperty(MqttProperties.SESSION_EXPIRY_INTERVAL_IDENTIFIER, expectedInterval));

        when(mockEndpoint.connectProperties()).thenReturn(connectProperties);

        final MqttConnectContext contextWithInterval = MqttConnectContext.fromConnectPacket(mockEndpoint, span);
        assertNotNull(contextWithInterval.getSessionExpiryInterval());
        assertEquals(Long.valueOf(expectedInterval), contextWithInterval.getSessionExpiryInterval());

        // Test case where property is not present
        final MqttEndpoint mockEndpointNoProperty = mock(MqttEndpoint.class);
        when(mockEndpointNoProperty.connectProperties()).thenReturn(new MqttProperties()); // Empty properties
        final MqttConnectContext contextWithoutInterval = MqttConnectContext.fromConnectPacket(mockEndpointNoProperty, span);
        assertNull(contextWithoutInterval.getSessionExpiryInterval());
    }

    @Test
    public void testCreateContextWithMessageExpiryInterval() {
        final MqttPublishMessage mockPublishMessage = mock(MqttPublishMessage.class);
        final MqttEndpoint mockEndpoint = mock(MqttEndpoint.class);
        final DeviceUser mockDeviceUser = mock(DeviceUser.class);
        final MqttProperties publishProperties = new MqttProperties();
        final int expectedInterval = 60;
        publishProperties.add(new MqttProperties.IntegerProperty(MqttProperties.MESSAGE_EXPIRY_INTERVAL_IDENTIFIER, expectedInterval));

        when(mockPublishMessage.properties()).thenReturn(publishProperties);
        // Ensure topicName is not null to avoid NPE in PropertyBag creation if fromPublishPacket tries to access it
        when(mockPublishMessage.topicName()).thenReturn("telemetry/tenant/device");


        final MqttContext contextWithInterval = MqttContext.fromPublishPacket(mockPublishMessage, mockEndpoint, span, mockDeviceUser);
        assertNotNull(contextWithInterval.getMessageExpiryInterval());
        assertEquals(Long.valueOf(expectedInterval), contextWithInterval.getMessageExpiryInterval());

        // Test case where property is not present
        final MqttPublishMessage mockPublishMessageNoProperty = mock(MqttPublishMessage.class);
        when(mockPublishMessageNoProperty.properties()).thenReturn(new MqttProperties()); // Empty properties
        when(mockPublishMessageNoProperty.topicName()).thenReturn("telemetry/tenant/device");
        final MqttContext contextWithoutInterval = MqttContext.fromPublishPacket(mockPublishMessageNoProperty, mockEndpoint, span, mockDeviceUser);
        assertNull(contextWithoutInterval.getMessageExpiryInterval());
    }
}
