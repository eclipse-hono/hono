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

package org.eclipse.hono.adapter.kura;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
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
 * Verifies behavior of {@link KuraProtocolAdapter}.
 * 
 */
@RunWith(VertxUnitRunner.class)
public class KuraProtocolAdapterTest {

    private KuraAdapterProperties config;
    private KuraProtocolAdapter adapter;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        config = new KuraAdapterProperties();
        adapter = new KuraProtocolAdapter();
        adapter.setConfig(config);
    }

    /**
     * Verifies that the adapter maps control messages with QoS 1 published from a Kura gateway to
     * the Event endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testGetDownstreamMessageMapsKuraControlMessagesToEventApi(final TestContext ctx) {

        // GIVEN an adapter configured to use the standard topic.control-prefix $EDC

        // WHEN a message is published to a topic with the Kura $EDC prefix as endpoint
        final MqttPublishMessage message = newMessage(MqttQoS.AT_LEAST_ONCE, "$EDC/my-scope/4711");
        final Async determineAddressSuccess = ctx.async();
        Future<Message> msgTracker = adapter.getDownstreamMessage(message).map(msg -> {
            determineAddressSuccess.complete();
            return msg;
        });

        // THEN the message is forwarded to the event API
        determineAddressSuccess.await(2000);
        assertMessageProperties(msgTracker.result(), config.getCtrlMsgContentType(),
                EventConstants.EVENT_ENDPOINT, "my-scope", "4711");
    }

    /**
     * Verifies that the adapter maps control messages with QoS 0 published from a Kura gateway to
     * the Telemetry endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testGetDownstreamMessageMapsKuraControlMessagesToTelemetryApi(final TestContext ctx) {

        // GIVEN an adapter configured to use the standard topic.control-prefix $EDC
        // and a custom control message content type
        config.setCtrlMsgContentType("control-msg");

        // WHEN a message is published to a topic with the Kura $EDC prefix as endpoint
        final MqttPublishMessage message = newMessage(MqttQoS.AT_MOST_ONCE, "$EDC/my-scope/4711");
        final Async determineAddressSuccess = ctx.async();
        Future<Message> msgTracker = adapter.getDownstreamMessage(message).map(msg -> {
            determineAddressSuccess.complete();
            return msg;
        });

        // THEN the message is forwarded to the telemetry API
        // and has the custom control message content type
        determineAddressSuccess.await(2000);
        assertMessageProperties(msgTracker.result(), config.getCtrlMsgContentType(),
                TelemetryConstants.TELEMETRY_ENDPOINT, "my-scope", "4711");
    }

    /**
     * Verifies that the adapter recognizes control messages published to a topic with a custom control prefix.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testGetDownstreamMessageRecognizesControlMessagesWithCustomControlPrefix(final TestContext ctx) {

        // GIVEN an adapter configured to use a custom topic.control-prefix
        config.setControlPrefix("bumlux");

        // WHEN a message is published to a topic with the custom prefix as endpoint
        final MqttPublishMessage message = newMessage(MqttQoS.AT_MOST_ONCE, "bumlux/my-scope/4711");
        final Async determineAddressSuccess = ctx.async();
        Future<Message> msgTracker = adapter.getDownstreamMessage(message).map(msg -> {
            determineAddressSuccess.complete();
            return msg;
        });

        // THEN the message is recognized as a control message and forwarded to the event API
        determineAddressSuccess.await(2000);
        assertMessageProperties(msgTracker.result(), config.getCtrlMsgContentType(),
                TelemetryConstants.TELEMETRY_ENDPOINT, "my-scope", "4711");
    }

    /**
     * Verifies that the adapter forwards data messages with QoS 0 published from a Kura gateway to
     * the Telemetry endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testGetDownstreamMessageMapsKuraDataMessagesToTelemetryApi(final TestContext ctx) {

        // GIVEN an adapter configured with a custom data message content type
        config.setDataMsgContentType("data-msg");

        // WHEN a message is published to an application topic with QoS 0
        final MqttPublishMessage message = newMessage(MqttQoS.AT_MOST_ONCE, "my-scope/4711");
        final Async determineAddressSuccess = ctx.async();
        Future<Message> msgTracker = adapter.getDownstreamMessage(message).map(msg -> {
            determineAddressSuccess.complete();
            return msg;
        });

        // THEN the message is forwarded to the telemetry API
        // and has the configured data message content type
        determineAddressSuccess.await(2000);
        assertMessageProperties(msgTracker.result(), config.getDataMsgContentType(),
                TelemetryConstants.TELEMETRY_ENDPOINT, "my-scope", "4711");
    }

    /**
     * Verifies that the adapter forwards application messages with QoS 1 published from a Kura gateway to
     * the Event endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testGetDownstreamMessageMapsKuraDataMessagesToEventApi(final TestContext ctx) {

        // GIVEN an adapter

        // WHEN a message is published to an application topic with QoS 1
        final MqttPublishMessage message = newMessage(MqttQoS.AT_LEAST_ONCE, "my-scope/4711");
        final Async determineAddressSuccess = ctx.async();
        Future<Message> msgTracker = adapter.getDownstreamMessage(message).map(msg -> {
            determineAddressSuccess.complete();
            return msg;
        });

        // THEN the message is forwarded to the event API
        determineAddressSuccess.await(2000);
        assertMessageProperties(msgTracker.result(), config.getDataMsgContentType(),
                EventConstants.EVENT_ENDPOINT, "my-scope", "4711");
    }

    private void assertMessageProperties(final Message msg, final String contentType, final String endpoint, String tenantId, final String deviceId) {
        assertThat(msg.getContentType(), is(contentType));
        ResourceIdentifier address = ResourceIdentifier.fromString(msg.getAddress());
        assertThat(address.getEndpoint(), is(endpoint));
        assertThat(MessageHelper.getDeviceId(msg), is(deviceId));
    }

    private static MqttPublishMessage newMessage(final MqttQoS qosLevel, final String topic) {
        final MqttPublishMessage message = mock(MqttPublishMessage.class);
        when(message.qosLevel()).thenReturn(qosLevel);
        when(message.topicName()).thenReturn(topic);
        return message;
    }
}
