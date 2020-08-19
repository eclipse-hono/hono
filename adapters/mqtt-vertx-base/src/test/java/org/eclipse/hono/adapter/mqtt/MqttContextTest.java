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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.service.auth.device.TenantDetailsDeviceCredentials;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantObjectContainer;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * Verifies behavior of {@link MqttContext}.
 *
 */
public class MqttContextTest {

    /**
     * Verifies that the tenant is determined via the MqttRequestTenantAndAuthIdExtractor.
     */
    @Test
    public void testTenantIsRetrievedViaExtractor() {
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("t");
        final Device device = new Device("tenant", "device");
        final TenantObject tenantObject = TenantObject.from("tenant", true);

        final MqttRequestTenantDetailsProvider tenantAndAuthIdExtractor = mock(MqttRequestTenantDetailsProvider.class);
        when(tenantAndAuthIdExtractor.getFromMqttEndpointOrPublishTopic(any(MqttEndpoint.class), any(), any()))
                .thenReturn(Future.succeededFuture(getCredentials(tenantObject, "device")));
        final Future<MqttContext> contextFuture = MqttContext
                .fromPublishPacket(msg, mock(MqttEndpoint.class), tenantAndAuthIdExtractor, device, null);

        assertTrue(contextFuture.succeeded());
        assertEquals(tenantObject, contextFuture.result().tenantObject());
    }

    /**
     * Verifies the values retrieved from the <em>property-bag</em> of a message's topic.
     */
    @Test
    public void verifyPropertyBagRetrievedFromTopic() {
        final Device device = new Device("tenant", "device");
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("event/tenant/device/?param1=value1&param2=value2");
        final MqttContext context = MqttContext.fromPublishPacket(msg, mock(MqttEndpoint.class),
                TenantObject.from("tenant", true), "device", device);

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
                MetricsTags.EndpointType.TELEMETRY);
        assertEndpoint(
                newMessage(EventConstants.EVENT_ENDPOINT_SHORT, "tenant", "device"),
                MetricsTags.EndpointType.EVENT);
        assertEndpoint(
                newMessage(CommandConstants.COMMAND_ENDPOINT_SHORT, "tenant", "device"),
                MetricsTags.EndpointType.COMMAND);
    }

    private TenantObjectContainer getCredentials(final TenantObject tenantObject, final String authId) {
        return new TenantDetailsDeviceCredentials(tenantObject, authId, "unspecified");
    }

    private static void assertEndpoint(final MqttPublishMessage msg, final MetricsTags.EndpointType expectedEndpoint) {

        final MqttContext context = MqttContext.fromPublishPacket(msg, mock(MqttEndpoint.class),
                TenantObject.from("tenantIgnored", true), "authIdIgnored", null);
        assertEquals(expectedEndpoint, context.endpoint());
    }

    private static MqttPublishMessage newMessage(final String endpoint, final String tenant, final String device) {

        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn(String.format("%s/%s/%s", endpoint, tenant, device));
        return msg;
    }
}
