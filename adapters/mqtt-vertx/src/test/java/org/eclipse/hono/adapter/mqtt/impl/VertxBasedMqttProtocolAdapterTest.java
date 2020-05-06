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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterProperties;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * Verifies behavior of {@link VertxBasedMqttProtocolAdapter}.
 * 
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class VertxBasedMqttProtocolAdapterTest {

    private MqttProtocolAdapterProperties config;
    private VertxBasedMqttProtocolAdapter adapter;

    private static void assertServiceInvocationException(final VertxTestContext ctx, final Throwable t, final int expectedStatusCode) {
        ctx.verify(() -> {
            assertThat(t).isInstanceOf(ServiceInvocationException.class);
            assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(expectedStatusCode);
        });
    }

    /**
     * Verifies that the adapter rejects messages published to topics containing an endpoint
     * other than <em>telemetry</em> or <em>event</em>.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicFailsForUnknownEndpoint(final VertxTestContext ctx) {

        givenAnAdapter();

        // WHEN a device publishes a message to a topic with an unknown endpoint
        final MqttPublishMessage message = newMessage(MqttQoS.AT_MOST_ONCE, "unknown");
        adapter.mapTopic(newContext(message, null)).onComplete(ctx.failing(t -> {
            // THEN the message cannot be mapped to a topic
            assertServiceInvocationException(ctx, t, HttpURLConnection.HTTP_NOT_FOUND);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter rejects QoS 2 messages published to the <em>telemetry</em> endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicFailsForQoS2TelemetryMessage(final VertxTestContext ctx) {

        givenAnAdapter();

        // WHEN a device publishes a message with QoS 2 to a "telemetry" topic
        final MqttPublishMessage message = newMessage(MqttQoS.EXACTLY_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT);
        adapter.mapTopic(newContext(message, null)).onComplete(ctx.failing(t -> {
            // THEN the message cannot be mapped to a topic
            assertServiceInvocationException(ctx, t, HttpURLConnection.HTTP_BAD_REQUEST);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter rejects QoS 0 messages published to the <em>event</em> endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicFailsForQoS0EventMessage(final VertxTestContext ctx) {

        givenAnAdapter();

        // WHEN a device publishes a message with QoS 0 to an "event" topic
        final MqttPublishMessage message = newMessage(MqttQoS.AT_MOST_ONCE, EventConstants.EVENT_ENDPOINT);
        adapter.mapTopic(newContext(message, null)).onComplete(ctx.failing(t -> {
            // THEN the message cannot be mapped to a topic
            assertServiceInvocationException(ctx, t, HttpURLConnection.HTTP_BAD_REQUEST);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter rejects QoS 2 messages published to the <em>event</em> endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicFailsForQoS2EventMessage(final VertxTestContext ctx) {

        givenAnAdapter();

        // WHEN a device publishes a message with QoS 2 to an "event" topic
        final MqttPublishMessage message = newMessage(MqttQoS.EXACTLY_ONCE, EventConstants.EVENT_ENDPOINT);
        adapter.mapTopic(newContext(message, null)).onComplete(ctx.failing(t -> {
            // THEN the message cannot be mapped to a topic
            assertServiceInvocationException(ctx, t, HttpURLConnection.HTTP_BAD_REQUEST);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter fails to map a topic without a tenant ID received from an anonymous device.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testOnPublishedMessageFailsForMissingTenant(final VertxTestContext ctx) {

        givenAnAdapter();

        // WHEN an anonymous device publishes a message to a topic that does not contain a tenant ID
        final MqttContext context = newContext(MqttQoS.AT_MOST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT);
        adapter.onPublishedMessage(context).onComplete(ctx.failing(t -> {
            // THEN the message cannot be published
            assertServiceInvocationException(ctx, t, HttpURLConnection.HTTP_BAD_REQUEST);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter fails to map a topic without a device ID received from an anonymous device.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testOnPublishedMessageFailsForMissingDeviceId(final VertxTestContext ctx) {

        givenAnAdapter();

        // WHEN an anonymous device publishes a message to a topic that does not contain a device ID
        final MqttContext context = newContext(MqttQoS.AT_MOST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT + "/my-tenant");
        adapter.onPublishedMessage(context).onComplete(ctx.failing(t -> {
            // THEN the message cannot be published
            assertServiceInvocationException(ctx, t, HttpURLConnection.HTTP_BAD_REQUEST);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter fails to map a topic without a device ID received from an authenticated device.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testOnPublishedAuthenticatedMessageFailsForMissingDeviceId(final VertxTestContext ctx) {

        givenAnAdapter();

        // WHEN an authenticated device publishes a message to a topic that does not contain a device ID
        final MqttContext context = newContext(
                MqttQoS.AT_MOST_ONCE,
                TelemetryConstants.TELEMETRY_ENDPOINT + "/my-tenant",
                new Device("my-tenant", "device"));
        adapter.onPublishedMessage(context).onComplete(ctx.failing(t -> {
            // THEN the message cannot be published
            assertServiceInvocationException(ctx, t, HttpURLConnection.HTTP_BAD_REQUEST);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter rejects a message published by a gateway whose tenant
     * does not match the tenant specified in the topic.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testOnPublishedAuthenticatedMessageFailsForNonMatchingTenant(final VertxTestContext ctx) {

        givenAnAdapter();

        // WHEN an authenticated gateway publishes a message to a topic that does not match the gateway's tenant
        final MqttContext context = newContext(
                MqttQoS.AT_MOST_ONCE,
                TelemetryConstants.TELEMETRY_ENDPOINT + "/other-tenant/4711",
                new Device("my-tenant", "gateway"));
        adapter.onPublishedMessage(context).onComplete(ctx.failing(t -> {
            // THEN the message cannot be published
            assertServiceInvocationException(ctx, t, HttpURLConnection.HTTP_FORBIDDEN);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter supports all required topic names.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicSupportsShortAndLongTopicNames(final VertxTestContext ctx) {

        givenAnAdapter();

        MqttPublishMessage message = newMessage(MqttQoS.AT_LEAST_ONCE, EventConstants.EVENT_ENDPOINT);
        MqttContext context = newContext(message, null);
        adapter.mapTopic(context).onComplete(ctx.succeeding(address -> {
            ctx.verify(() -> assertThat(MetricsTags.EndpointType.fromString(address.getEndpoint())).isEqualTo(MetricsTags.EndpointType.EVENT));
        }));

        message = newMessage(MqttQoS.AT_LEAST_ONCE, EventConstants.EVENT_ENDPOINT_SHORT);
        context = newContext(message, null);
        adapter.mapTopic(context).onComplete(ctx.succeeding(address -> {
            ctx.verify(() -> assertThat(MetricsTags.EndpointType.fromString(address.getEndpoint())).isEqualTo(MetricsTags.EndpointType.EVENT));
        }));

        message = newMessage(MqttQoS.AT_LEAST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT);
        context = newContext(message, null);
        adapter.mapTopic(context).onComplete(ctx.succeeding(address -> {
            ctx.verify(() -> assertThat(MetricsTags.EndpointType.fromString(address.getEndpoint())).isEqualTo(MetricsTags.EndpointType.TELEMETRY));
        }));

        message = newMessage(MqttQoS.AT_LEAST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT_SHORT);
        context = newContext(message, null);
        adapter.mapTopic(context).onComplete(ctx.succeeding(address -> {
            ctx.verify(() -> assertThat(MetricsTags.EndpointType.fromString(address.getEndpoint())).isEqualTo(MetricsTags.EndpointType.TELEMETRY));
        }));

        message = newMessage(MqttQoS.AT_LEAST_ONCE, "unknown");
        context = newContext(message, null);
        adapter.mapTopic(context).onComplete(ctx.failing());
        ctx.completeNow();

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
