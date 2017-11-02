/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.mqtt;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * Verifies behavior of {@link VertxBasedMqttProtocolAdapter}.
 * 
 */
@RunWith(VertxUnitRunner.class)
public class VertxBasedMqttProtocolAdapterTest {

    private ProtocolAdapterProperties config;
    private VertxBasedMqttProtocolAdapter adapter;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        config = new ProtocolAdapterProperties();
        adapter = new VertxBasedMqttProtocolAdapter();
        adapter.setConfig(config);
    }

    /**
     * Verifies that the adapter rejects messages published to topics containing an endpoint
     * other than <em>telemetry</em> or <em>event</em>.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testGetDownstreamMessageFailsForUnknownEndpoint(final TestContext ctx) {

        // GIVEN an adapter

        // WHEN a device publishes a message to a topic with an unknown endpoint
        final MqttPublishMessage message = newMessage(MqttQoS.AT_MOST_ONCE, "unknown");
        final Async determineAddressFailure = ctx.async();
        adapter.getDownstreamMessage(message).recover(t -> {
            determineAddressFailure.complete();
            return Future.failedFuture(t);
        });
        // THEN no downstream sender can be created for the message
        determineAddressFailure.await(2000);
    }

    /**
     * Verifies that the adapter rejects QoS 1 messages published to the <em>telemetry</em> endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testGetDownstreamMessageFailsForQoS1TelemetryMessage(final TestContext ctx) {

        // GIVEN an adapter

        // WHEN a device publishes a message with QoS 1 to a "telemetry" topic
        final MqttPublishMessage message = newMessage(MqttQoS.AT_LEAST_ONCE, AbstractVertxBasedMqttProtocolAdapter.TELEMETRY_ENDPOINT);
        final Async determineAddressFailure = ctx.async();
        adapter.getDownstreamMessage(message).recover(t -> {
            determineAddressFailure.complete();
            return Future.failedFuture(t);
        });
        // THEN no downstream sender can be created for the message
        determineAddressFailure.await(2000);
    }

    /**
     * Verifies that the adapter rejects QoS 1 messages published to the <em>telemetry</em> endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testGetDownstreamMessageFailsForQoS0EventMessage(final TestContext ctx) {

        // GIVEN an adapter

        // WHEN a device publishes a message with QoS 0 to an "event" topic
        final MqttPublishMessage message = newMessage(MqttQoS.AT_MOST_ONCE, AbstractVertxBasedMqttProtocolAdapter.EVENT_ENDPOINT);
        final Async messageFailure = ctx.async();
        adapter.getDownstreamMessage(message).recover(t -> {
            messageFailure.complete();
            return Future.failedFuture(t);
        });
        // THEN no downstream sender can be created for the message
        messageFailure.await(2000);
    }

    /**
     * Verifies that the adapter fails to map a topic without a tenant ID received from an anonymous device.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testGetDownstreamMessageFailsForMissingTenant(final TestContext ctx) {

        // GIVEN an adapter

        // WHEN an anonymous device publishes a message to a topic that does not contain a tenant ID
        final MqttPublishMessage message = newMessage(MqttQoS.AT_MOST_ONCE, VertxBasedMqttProtocolAdapter.TELEMETRY_ENDPOINT);
        final Async determineAddressFailure = ctx.async();
        adapter.getDownstreamMessage(message).recover(t -> {
            determineAddressFailure.complete();
            return Future.failedFuture(t);
        });

        // THEN the message cannot be mapped to an address
        determineAddressFailure.await(2000);
    }

    /**
     * Verifies that the adapter fails to map a topic without a device ID received from an anonymous device.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testGetDownstreamMessageFailsForMissingDeviceId(final TestContext ctx) {

        // GIVEN an adapter

        // WHEN an anonymous device publishes a message to a topic that does not contain a device ID
        final MqttPublishMessage message = newMessage(MqttQoS.AT_MOST_ONCE, VertxBasedMqttProtocolAdapter.TELEMETRY_ENDPOINT + "/my-tenant");
        final Async determineAddressFailure = ctx.async();
        adapter.getDownstreamMessage(message).recover(t -> {
            determineAddressFailure.complete();
            return Future.failedFuture(t);
        });

        // THEN the message cannot be mapped to an address
        determineAddressFailure.await(2000);
    }

    /**
     * Verifies that the adapter uses an authenticated device's identity when mapping a topic without tenant ID.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testGetDownstreamMessageUsesDeviceIdentityForTopicWithoutTenant(final TestContext ctx) {

        // GIVEN an adapter

        // WHEN an authenticated device publishes a message to a topic that does not contain a tenant ID
        final MqttPublishMessage message = newMessage(MqttQoS.AT_MOST_ONCE, VertxBasedMqttProtocolAdapter.TELEMETRY_ENDPOINT);
        final Async determineAddressSuccess = ctx.async();
        Future<Message> downstreamMessage = adapter.getDownstreamMessage(message, new Device("my-tenant", "4711")).map(msg -> {
            determineAddressSuccess.complete();
            return msg;
        });

        // THEN the mapped address contains the authenticated device's tenant and device ID
        determineAddressSuccess.await(2000);
        final ResourceIdentifier downstreamAddress = ResourceIdentifier.fromString(downstreamMessage.result().getAddress());
        assertThat(downstreamAddress.getEndpoint(), is(VertxBasedMqttProtocolAdapter.TELEMETRY_ENDPOINT));
        assertThat(downstreamAddress.getTenantId(), is("my-tenant"));
        assertThat(MessageHelper.getDeviceId(downstreamMessage.result()), is("4711"));
    }

    private static MqttPublishMessage newMessage(final MqttQoS qosLevel, final String topic) {
        final MqttPublishMessage message = mock(MqttPublishMessage.class);
        when(message.qosLevel()).thenReturn(qosLevel);
        when(message.topicName()).thenReturn(topic);
        return message;
    }
}
