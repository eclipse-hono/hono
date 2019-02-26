/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.adapter.mqtt.impl;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterProperties;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.buffer.Buffer;
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

    private MqttProtocolAdapterProperties config;
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
        adapter.mapTopic(newContext(message, null)).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the message cannot be mapped to a topic
            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ClientErrorException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the adapter rejects QoS 2 messages published to the <em>telemetry</em> endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicFailsForQoS2TelemetryMessage(final TestContext ctx) {

        givenAnAdapter();

        // WHEN a device publishes a message with QoS 2 to a "telemetry" topic
        final MqttPublishMessage message = newMessage(MqttQoS.EXACTLY_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT);
        adapter.mapTopic(newContext(message, null)).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the message cannot be mapped to a topic
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ClientErrorException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the adapter rejects QoS 0 messages published to the <em>event</em> endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicFailsForQoS0EventMessage(final TestContext ctx) {

        givenAnAdapter();

        // WHEN a device publishes a message with QoS 0 to an "event" topic
        final MqttPublishMessage message = newMessage(MqttQoS.AT_MOST_ONCE, EventConstants.EVENT_ENDPOINT);
        adapter.mapTopic(newContext(message, null)).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the message cannot be mapped to a topic
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ClientErrorException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the adapter rejects QoS 2 messages published to the <em>event</em> endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicFailsForQoS2EventMessage(final TestContext ctx) {

        givenAnAdapter();

        // WHEN a device publishes a message with QoS 2 to an "event" topic
        final MqttPublishMessage message = newMessage(MqttQoS.EXACTLY_ONCE, EventConstants.EVENT_ENDPOINT);
        adapter.mapTopic(newContext(message, null)).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the message cannot be mapped to a topic
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ClientErrorException) t).getErrorCode());
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
            // THEN the message cannot be published
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ClientErrorException) t).getErrorCode());
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
            // THEN the message cannot be published
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ClientErrorException) t).getErrorCode());
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
            // THEN the message cannot be published
            ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ClientErrorException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the adapter rejects a message published by a gateway whose tenant
     * does not match the tenant specified in the topic.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testOnPublishedAuthenticatedMessageFailsForNonMatchingTenant(final TestContext ctx) {

        givenAnAdapter();

        // WHEN an authenticated gateway publishes a message to a topic that does not match the gateway's tenant
        final MqttContext context = newContext(
                MqttQoS.AT_MOST_ONCE,
                TelemetryConstants.TELEMETRY_ENDPOINT + "/other-tenant/4711",
                new Device("my-tenant", "gateway"));
        adapter.onPublishedMessage(context).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the message cannot be published
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, ((ClientErrorException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the adapter supports all required topic names.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicSupportsShortAndLongTopicNames(final TestContext ctx) {

        givenAnAdapter();

        MqttPublishMessage message = newMessage(MqttQoS.AT_LEAST_ONCE, EventConstants.EVENT_ENDPOINT);
        MqttContext context = newContext(message, null);
        adapter.mapTopic(context).setHandler(ctx.asyncAssertSuccess());

        message = newMessage(MqttQoS.AT_LEAST_ONCE, EventConstants.EVENT_ENDPOINT);
        context = newContext(message, null);
        adapter.mapTopic(context).setHandler(
            address -> assertTrue(MetricsTags.EndpointType.EVENT == MetricsTags.EndpointType.fromString(address.result().getEndpoint()))
        );

        message = newMessage(MqttQoS.AT_LEAST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT);
        context = newContext(message, null);
        adapter.mapTopic(context).setHandler(
            address -> assertTrue(MetricsTags.EndpointType.TELEMETRY == MetricsTags.EndpointType.fromString(address.result().getEndpoint()))
        );

        message = newMessage(MqttQoS.AT_LEAST_ONCE, EventConstants.EVENT_ENDPOINT_SHORT);
        context = newContext(message, null);
        adapter.mapTopic(context).setHandler(
            address -> assertTrue(MetricsTags.EndpointType.EVENT == MetricsTags.EndpointType.fromString(address.result().getEndpoint()))
        );

        message = newMessage(MqttQoS.AT_LEAST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT_SHORT);
        context = newContext(message, null);
        adapter.mapTopic(context).setHandler(
            address -> assertTrue(MetricsTags.EndpointType.TELEMETRY == MetricsTags.EndpointType.fromString(address.result().getEndpoint()))
        );

        message = newMessage(MqttQoS.AT_LEAST_ONCE, "unknown");
        context = newContext(message, null);
        adapter.mapTopic(context).setHandler(ctx.asyncAssertFailure());

    }

    private void givenAnAdapter() {

        config = new MqttProtocolAdapterProperties();
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
        return MqttContext.fromPublishPacket(message, mock(MqttEndpoint.class), authenticatedDevice);
    }
}
