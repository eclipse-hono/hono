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

package org.eclipse.hono.adapter.kura;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.Before;
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
 * Verifies behavior of {@link KuraProtocolAdapter}.
 * 
 */
@RunWith(VertxUnitRunner.class)
public class KuraProtocolAdapterTest {

    /**
     * Time out each test after 5 seconds.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

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
    public void testMapTopicMapsKuraControlMessagesToEventApi(final TestContext ctx) {

        // GIVEN an adapter configured to use the standard topic.control-prefix $EDC

        // WHEN a message is published to a topic with the Kura $EDC prefix as endpoint
        final MqttContext context = newContext(MqttQoS.AT_LEAST_ONCE, "$EDC/my-scope/4711");
        final Async determineAddressSuccess = ctx.async();
        final Future<ResourceIdentifier> addressTracker = adapter.mapTopic(context).map(msg -> {
            determineAddressSuccess.complete();
            return msg;
        });

        // THEN the message is mapped to the event API
        determineAddressSuccess.await();
        assertAddress(addressTracker.result(), EventConstants.EVENT_ENDPOINT, "my-scope", "4711");
        // and has the control message content type
        assertThat(context.contentType(), is(config.getCtrlMsgContentType()));
    }

    /**
     * Verifies that the adapter maps control messages with QoS 0 published from a Kura gateway to
     * the Telemetry endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicMapsKuraControlMessagesToTelemetryApi(final TestContext ctx) {

        // GIVEN an adapter configured to use the standard topic.control-prefix $EDC
        // and a custom control message content type
        config.setCtrlMsgContentType("control-msg");

        // WHEN a message is published to a topic with the Kura $EDC prefix as endpoint
        final MqttContext context = newContext(MqttQoS.AT_MOST_ONCE, "$EDC/my-scope/4711");
        final Async determineAddressSuccess = ctx.async();
        final Future<ResourceIdentifier> addressTracker = adapter.mapTopic(context).map(msg -> {
            determineAddressSuccess.complete();
            return msg;
        });

        // THEN the message is mapped to the telemetry API
        determineAddressSuccess.await();
        assertAddress(addressTracker.result(), TelemetryConstants.TELEMETRY_ENDPOINT, "my-scope", "4711");
        // and has the custom control message content type
        assertThat(context.contentType(), is(config.getCtrlMsgContentType()));
    }

    /**
     * Verifies that the adapter recognizes control messages published to a topic with a custom control prefix.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicRecognizesControlMessagesWithCustomControlPrefix(final TestContext ctx) {

        // GIVEN an adapter configured to use a custom topic.control-prefix
        config.setControlPrefix("bumlux");

        // WHEN a message is published to a topic with the custom prefix as endpoint
        final MqttContext context = newContext(MqttQoS.AT_MOST_ONCE, "bumlux/my-scope/4711");
        final Async determineAddressSuccess = ctx.async();
        final Future<ResourceIdentifier> addressTracker = adapter.mapTopic(context).map(msg -> {
            determineAddressSuccess.complete();
            return msg;
        });

        // THEN the message is mapped to the event API
        determineAddressSuccess.await();
        assertAddress(addressTracker.result(), TelemetryConstants.TELEMETRY_ENDPOINT, "my-scope", "4711");
        // and is recognized as a control message
        assertThat(context.contentType(), is(config.getCtrlMsgContentType()));
    }

    /**
     * Verifies that the adapter forwards data messages with QoS 0 published from a Kura gateway to
     * the Telemetry endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicMapsKuraDataMessagesToTelemetryApi(final TestContext ctx) {

        // GIVEN an adapter configured with a custom data message content type
        config.setDataMsgContentType("data-msg");

        // WHEN a message is published to an application topic with QoS 0
        final MqttContext context = newContext(MqttQoS.AT_MOST_ONCE, "my-scope/4711");
        final Async determineAddressSuccess = ctx.async();
        final Future<ResourceIdentifier> addressTracker = adapter.mapTopic(context).map(msg -> {
            determineAddressSuccess.complete();
            return msg;
        });

        // THEN the message is mapped to the telemetry API
        determineAddressSuccess.await();
        assertAddress(addressTracker.result(), TelemetryConstants.TELEMETRY_ENDPOINT, "my-scope", "4711");
        // and has the configured data message content type
        assertThat(context.contentType(), is(config.getDataMsgContentType()));
    }

    /**
     * Verifies that the adapter forwards application messages with QoS 1 published from a Kura gateway to
     * the Event endpoint.
     * 
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapTopicMapsKuraDataMessagesToEventApi(final TestContext ctx) {

        // GIVEN an adapter

        // WHEN a message is published to an application topic with QoS 1
        final MqttContext context = newContext(MqttQoS.AT_LEAST_ONCE, "my-scope/4711");
        final Async determineAddressSuccess = ctx.async();
        final Future<ResourceIdentifier> addressTracker = adapter.mapTopic(context).map(msg -> {
            determineAddressSuccess.complete();
            return msg;
        });

        // THEN the message is forwarded to the event API
        determineAddressSuccess.await();
        assertAddress(addressTracker.result(), EventConstants.EVENT_ENDPOINT, "my-scope", "4711");
        // and is recognized as a data message
        assertThat(context.contentType(), is(config.getDataMsgContentType()));
    }

    private void assertAddress(final ResourceIdentifier address, final String endpoint, final String tenantId, final String deviceId) {
        assertThat(address.getEndpoint(), is(endpoint));
        assertThat(address.getTenantId(), is(tenantId));
        assertThat(address.getResourceId(), is(deviceId));
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
