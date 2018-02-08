/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.mqtt.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * Verifies behavior of {@link VertxBasedMqttProtocolAdapter}.
 * 
 */
@RunWith(VertxUnitRunner.class)
public class VertxBasedMqttProtocolAdapterTest {

    /**
     * Time out all tests after 5 seconds.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

    private ProtocolAdapterProperties config;
    private VertxBasedMqttProtocolAdapter adapter;

    /**
     * Verifies that the adapter rejects messages published to topics containing an endpoint
     * other than <em>telemetry</em> or <em>event</em>.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicFailsForUnknownEndpoint(final TestContext ctx) {

        givenAnAdapter();

        // WHEN a device publishes a message to a topic with an unknown endpoint
        final MqttPublishMessage message = newMessage(MqttQoS.AT_MOST_ONCE, "unknown");
        adapter.mapTopic(message).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN no downstream sender can be created for the message
        }));
    }

    /**
     * Verifies that the adapter rejects QoS 1 messages published to the <em>telemetry</em> endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicFailsForQoS1TelemetryMessage(final TestContext ctx) {

        givenAnAdapter();

        // WHEN a device publishes a message with QoS 1 to a "telemetry" topic
        final MqttPublishMessage message = newMessage(MqttQoS.AT_LEAST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT);
        adapter.mapTopic(message).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN no downstream sender can be created for the message
        }));
    }

    /**
     * Verifies that the adapter rejects QoS 1 messages published to the <em>telemetry</em> endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicFailsForQoS0EventMessage(final TestContext ctx) {

        givenAnAdapter();

        // WHEN a device publishes a message with QoS 0 to an "event" topic
        final MqttPublishMessage message = newMessage(MqttQoS.AT_MOST_ONCE, EventConstants.EVENT_ENDPOINT);
        adapter.mapTopic(message).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN no downstream sender can be created for the message
        }));
    }

    /**
     * Verifies that the adapter fails to map a topic without a tenant ID received from an anonymous device.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testOnPublishedMessageFailsForMissingTenant(final TestContext ctx) {

        givenAnAdapter();

        // WHEN an anonymous device publishes a message to a topic that does not contain a tenant ID
        final MqttContext context = newContext(MqttQoS.AT_MOST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT);
        adapter.onPublishedMessage(context).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the message cannot be mapped to an address
        }));
    }

    /**
     * Verifies that the adapter fails to map a topic without a device ID received from an anonymous device.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testOnPublishedMessageFailsForMissingDeviceId(final TestContext ctx) {

        givenAnAdapter();

        // WHEN an anonymous device publishes a message to a topic that does not contain a device ID
        final MqttContext context = newContext(MqttQoS.AT_MOST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT + "/my-tenant");
        adapter.onPublishedMessage(context).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the message cannot be mapped to an address
        }));
    }

    /**
     * Verifies that the adapter fails to map a topic without a device ID received from an authenticated device.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testOnPublishedAuthenticatedMessageFailsForMissingDeviceId(final TestContext ctx) {

        givenAnAdapter();

        // WHEN an authenticated device publishes a message to a topic that does not contain a device ID
        final MqttContext context = newContext(
                MqttQoS.AT_MOST_ONCE,
                TelemetryConstants.TELEMETRY_ENDPOINT + "/my-tenant",
                new Device("my-tenant", "device"));
        adapter.onPublishedMessage(context).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the message cannot be mapped to an address
        }));
    }

    /**
     * Verifies that the adapter uses an authenticated device's identity when mapping a topic without tenant ID.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testOnPublishedMessageUsesDeviceIdentityForTopicWithoutTenant(final TestContext ctx) {

        givenAnAdapter();

        // WHEN an authenticated device publishes a message to a topic that does not contain a tenant ID
        final MqttPublishMessage message = newMessage(MqttQoS.AT_MOST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT);
        final MqttContext context = newContext(message, new Device("my-tenant", "4711"));
        final Async addressCheck = ctx.async();
        final Future<ResourceIdentifier> checkedAddress = adapter.mapTopic(message)
            .compose(address -> adapter.checkAddress(context, address))
            .map(address -> {
                addressCheck.complete();
                return address;
            });

        // THEN the mapped address contains the authenticated device's tenant and device ID
        addressCheck.await();
        final ResourceIdentifier downstreamAddress = checkedAddress.result();
        assertThat(downstreamAddress.getEndpoint(), is(TelemetryConstants.TELEMETRY_ENDPOINT));
        assertThat(downstreamAddress.getTenantId(), is("my-tenant"));
    }

    private void givenAnAdapter() {

        config = new ProtocolAdapterProperties();
        adapter = new VertxBasedMqttProtocolAdapter();
        adapter.setConfig(config);
    }

    private static MqttPublishMessage newMessage(final MqttQoS qosLevel, final String topic) {
        return newMessage(qosLevel, topic, Buffer.buffer("test"));
    }

    private static MqttPublishMessage newMessage(final MqttQoS qosLevel, final String topic, final Buffer payload) {

        final MqttPublishMessage message = mock(MqttPublishMessage.class);
        when(message.qosLevel()).thenReturn(qosLevel);
        when(message.topicName()).thenReturn(topic);
        when(message.payload()).thenReturn(payload);
        return message;
    }

    private static MqttContext newContext(final MqttQoS qosLevel, final String topic) {
        return newContext(qosLevel, topic, null);
    }

    private static MqttContext newContext(final MqttQoS qosLevel, final String topic, final Device authenticatedDevice) {

        final MqttPublishMessage message = newMessage(qosLevel, topic);
        return newContext(message, authenticatedDevice);
    }

    private static MqttContext newContext(final MqttPublishMessage message, final Device authenticatedDevice) {
        return new MqttContext(message, mock(MqttEndpoint.class), authenticatedDevice);
    }
}
