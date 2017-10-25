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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.hono.util.ResourceIdentifier;
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

    /**
     * Verifies that the adapter rejects messages published to topics containing an endpoint
     * other than <em>telemetry</em> or <em>event</em>.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testGetSenderFailsForUnsupportedEndpoint(final TestContext ctx) {

        // GIVEN an adapter
        VertxBasedMqttProtocolAdapter adapter = new VertxBasedMqttProtocolAdapter();

        // WHEN a message is published to topic "unknown"
        final MqttPublishMessage message = mock(MqttPublishMessage.class);
        final ResourceIdentifier topic = ResourceIdentifier.from("unknown", "tenant", "4711");
        final Async determineSenderFailure = ctx.async();
        adapter.getSender(message, topic).recover(t -> {
            determineSenderFailure.complete();
            return Future.failedFuture(t);
        });
        // THEN no downstream sender can be created for the message
        determineSenderFailure.await(2000);
    }

    /**
     * Verifies that the adapter rejects QoS 1 messages published to the <em>telemetry</em> endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testGetSenderFailsForQoS1TelemetryMessage(final TestContext ctx) {

        // GIVEN an adapter
        VertxBasedMqttProtocolAdapter adapter = new VertxBasedMqttProtocolAdapter();

        // WHEN a message is published to endpoint "telemetry" with QoS 1
        final MqttPublishMessage message = mock(MqttPublishMessage.class);
        when(message.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        final ResourceIdentifier topic = ResourceIdentifier.from(VertxBasedMqttProtocolAdapter.TELEMETRY_ENDPOINT, "tenant", "4711");
        final Async determineSenderFailure = ctx.async();
        adapter.getSender(message, topic).recover(t -> {
            determineSenderFailure.complete();
            return Future.failedFuture(t);
        });
        // THEN no downstream sender can be created for the message
        determineSenderFailure.await(2000);
    }

    /**
     * Verifies that the adapter rejects QoS 1 messages published to the <em>telemetry</em> endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testGetSenderFailsForQoS0EventMessage(final TestContext ctx) {

        // GIVEN an adapter
        VertxBasedMqttProtocolAdapter adapter = new VertxBasedMqttProtocolAdapter();

        // WHEN a message is published to endpoint "event" with QoS 0
        final MqttPublishMessage message = mock(MqttPublishMessage.class);
        when(message.qosLevel()).thenReturn(MqttQoS.AT_MOST_ONCE);
        final ResourceIdentifier topic = ResourceIdentifier.from(VertxBasedMqttProtocolAdapter.EVENT_ENDPOINT, "tenant", "4711");
        final Async determineSenderFailure = ctx.async();
        adapter.getSender(message, topic).recover(t -> {
            determineSenderFailure.complete();
            return Future.failedFuture(t);
        });
        // THEN no downstream sender can be created for the message
        determineSenderFailure.await(2000);
    }
}
