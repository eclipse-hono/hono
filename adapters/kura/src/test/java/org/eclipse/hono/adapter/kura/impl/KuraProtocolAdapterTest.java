/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.kura.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;

import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.Span;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * Verifies behavior of {@link KuraProtocolAdapter}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class KuraProtocolAdapterTest {

    private KuraAdapterProperties config;
    private KuraProtocolAdapter adapter;
    private Span span;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        config = new KuraAdapterProperties();
        adapter = new KuraProtocolAdapter();
        adapter.setConfig(config);

        span = TracingMockSupport.mockSpan();
    }

    /**
     * Verifies that the adapter maps control messages with QoS 1 published from a Kura gateway to
     * the Event endpoint.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicMapsKuraControlMessagesToEventApi(final VertxTestContext ctx) {

        // GIVEN an adapter configured to use the standard topic.control-prefix $EDC

        // WHEN a message is published to a topic with the Kura $EDC prefix as endpoint
        final MqttContext context = newContext(MqttQoS.AT_LEAST_ONCE, "$EDC/my-scope/4711", span);
        adapter.mapTopic(context).onComplete(ctx.succeeding(msg -> {
                ctx.verify(() -> {
                    // THEN the message is mapped to the event API
                    assertAddress(msg, EventConstants.EVENT_ENDPOINT, "my-scope", "4711");
                    // and has the control message content type
                    assertThat(context.contentType()).isEqualTo(config.getCtrlMsgContentType());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the adapter maps control messages with QoS 0 published from a Kura gateway to
     * the Telemetry endpoint.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicMapsKuraControlMessagesToTelemetryApi(final VertxTestContext ctx) {

        // GIVEN an adapter configured to use the standard topic.control-prefix $EDC
        // and a custom control message content type
        config.setCtrlMsgContentType("control-msg");

        // WHEN a message is published to a topic with the Kura $EDC prefix as endpoint
        final MqttContext context = newContext(MqttQoS.AT_MOST_ONCE, "$EDC/my-scope/4711", span);
        adapter.mapTopic(context).onComplete(ctx.succeeding(msg -> {
            ctx.verify(() -> {
                // THEN the message is mapped to the telemetry API
                assertAddress(msg, TelemetryConstants.TELEMETRY_ENDPOINT, "my-scope", "4711");
                // and has the custom control message content type
                assertThat(context.contentType()).isEqualTo(config.getCtrlMsgContentType());
            });
            ctx.completeNow();
        }));

    }

    /**
     * Verifies that the adapter recognizes control messages published to a topic with a custom control prefix.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicRecognizesControlMessagesWithCustomControlPrefix(final VertxTestContext ctx) {

        // GIVEN an adapter configured to use a custom topic.control-prefix
        config.setControlPrefix("bumlux");

        // WHEN a message is published to a topic with the custom prefix as endpoint
        final MqttContext context = newContext(MqttQoS.AT_MOST_ONCE, "bumlux/my-scope/4711", span);
        adapter.mapTopic(context).onComplete(ctx.succeeding(msg -> {
            ctx.verify(() -> {
                // THEN the message is mapped to the event API
                assertAddress(msg, TelemetryConstants.TELEMETRY_ENDPOINT, "my-scope", "4711");
                // and is recognized as a control message
                assertThat(context.contentType()).isEqualTo(config.getCtrlMsgContentType());
            });
            ctx.completeNow();
        }));

    }

    /**
     * Verifies that the adapter forwards data messages with QoS 0 published from a Kura gateway to
     * the Telemetry endpoint.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicMapsKuraDataMessagesToTelemetryApi(final VertxTestContext ctx) {

        // GIVEN an adapter configured with a custom data message content type
        config.setDataMsgContentType("data-msg");

        // WHEN a message is published to an application topic with QoS 0
        final MqttContext context = newContext(MqttQoS.AT_MOST_ONCE, "my-scope/4711", span);
        adapter.mapTopic(context).onComplete(ctx.succeeding(msg -> {
            ctx.verify(() -> {
                // THEN the message is mapped to the telemetry API
                assertAddress(msg, TelemetryConstants.TELEMETRY_ENDPOINT, "my-scope", "4711");
                // and has the configured data message content type
                assertThat(context.contentType()).isEqualTo(config.getDataMsgContentType());
            });
            ctx.completeNow();
        }));

    }

    /**
     * Verifies that the adapter forwards application messages with QoS 1 published from a Kura gateway to
     * the Event endpoint.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicMapsKuraDataMessagesToEventApi(final VertxTestContext ctx) {

        // GIVEN an adapter

        // WHEN a message is published to an application topic with QoS 1
        final MqttContext context = newContext(MqttQoS.AT_LEAST_ONCE, "my-scope/4711", span);
        adapter.mapTopic(context).onComplete(ctx.succeeding(msg -> {
            ctx.verify(() -> {
                // THEN the message is forwarded to the event API
                assertAddress(msg, EventConstants.EVENT_ENDPOINT, "my-scope", "4711");
                // and is recognized as a data message
                assertThat(context.contentType()).isEqualTo(config.getDataMsgContentType());
            });
            ctx.completeNow();
        }));

    }

    private void assertAddress(final ResourceIdentifier address, final String endpoint, final String tenantId, final String deviceId) {
        assertThat(address.getEndpoint()).isEqualTo(endpoint);
        assertThat(address.getTenantId()).isEqualTo(tenantId);
        assertThat(address.getResourceId()).isEqualTo(deviceId);
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

    private static MqttContext newContext(final MqttQoS qosLevel, final String topic, final Span span) {
        return newContext(qosLevel, topic, null, span);
    }

    private static MqttContext newContext(final MqttQoS qosLevel, final String topic, final Device authenticatedDevice, final Span span) {

        final MqttPublishMessage message = newMessage(qosLevel, topic);
        return newContext(message, authenticatedDevice, span);
    }

    private static MqttContext newContext(final MqttPublishMessage message, final Device authenticatedDevice, final Span span) {
        return MqttContext.fromPublishPacket(message, mock(MqttEndpoint.class), span, authenticatedDevice);
    }
}
