/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.example.protocolgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.Command;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.MqttCommandContext;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.MqttDownstreamContext;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.MqttProtocolGatewayConfig;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.CommandResponseMessage;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.DownstreamMessage;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.EventMessage;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.TelemetryMessage;
import org.eclipse.hono.util.CommandConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * Verifies behavior of {@link AzureIotHubMqttGateway}.
 */
public class AzureIotHubMqttGatewayTest {

    private static final String PARAMETERIZED_TEST_NAME_PATTERN = "{displayName} [{index}]; parameters: {argumentsWithNames}";
    private static final String CORRELATION_ID = "666";
    private static final String MESSAGE_ID = "777";
    private static final String APPLICATION_PROPERTY_KEY = "foo";
    private static final String APPLICATION_PROPERTY_VALUE = "bar";

    private static final String REPLY_ID = "XXXX";
    private static final String REPLY_TO_ADDRESS = "command_response/test-tenant/test-device/" + REPLY_ID;
    private static final String REQUEST_ID = RequestId.encode(REPLY_TO_ADDRESS, CORRELATION_ID);

    private static final String TENANT_ID = "test-tenant";
    private static final String DEVICE_ID = "device1";
    private static final String CLIENT_ID = "the-client-id";

    private static final String cloudToDeviceTopicFilter = String
            .format(AzureIotHubMqttGateway.CLOUD_TO_DEVICE_TOPIC_FILTER_FORMAT_STRING, DEVICE_ID);

    private static final String directMessageTopicFilter = AzureIotHubMqttGateway.DIRECT_METHOD_TOPIC_FILTER;

    private final Device device = new Device(TENANT_ID, DEVICE_ID);
    private final Message commandMessage = mock(Message.class);

    private final Buffer payload = new JsonObject().put("a-key", "a-value").toBuffer();
    private final DemoDeviceConfiguration demoDeviceConfig = new DemoDeviceConfiguration();

    private AzureIotHubMqttGateway underTest;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        when(commandMessage.getBody()).thenReturn(new Data(new Binary(payload.getBytes())));
        when(commandMessage.getMessageId()).thenReturn(MESSAGE_ID);
        when(commandMessage.getCorrelationId()).thenReturn(CORRELATION_ID);
        final HashMap<String, Object> propertiesMap = new HashMap<>();
        propertiesMap.put(APPLICATION_PROPERTY_KEY, APPLICATION_PROPERTY_VALUE);
        final ApplicationProperties applicationProperties = new ApplicationProperties(propertiesMap);
        when(commandMessage.getApplicationProperties()).thenReturn(applicationProperties);

        demoDeviceConfig.setDeviceId(DEVICE_ID);
        demoDeviceConfig.setTenantId(TENANT_ID);
        demoDeviceConfig.setUsername("the-username");
        demoDeviceConfig.setPassword("super secret");

        underTest = new AzureIotHubMqttGateway(new ClientConfigProperties(), new MqttProtocolGatewayConfig(),
                demoDeviceConfig);
    }

    /**
     * Verifies the command object when a one-way command message is received.
     */
    @Test
    public void testReceiveOneWayCommand() {

        // GIVEN a command context for a one-way command
        final MqttCommandContext commandContext = MqttCommandContext.fromAmqpMessage(commandMessage, device);

        // WHEN the message is received
        final Command command = underTest.onCommandReceived(commandContext);

        // THEN the returned command object contains the correct topic filter and payload...
        assertThat(command.getTopicFilter()).isEqualTo(cloudToDeviceTopicFilter);
        assertThat(command.getPayload()).isEqualTo(payload);

        // ...AND the topic contains the expected properties
        final PropertyBag propertyBag = PropertyBag.decode(command.getTopic());

        assertThat(propertyBag.topicWithoutPropertyBag()).isEqualTo("devices/" + DEVICE_ID + "/messages/devicebound/");

        assertThat(propertyBag.getProperty("$messageId")).isEqualTo(MESSAGE_ID);
        assertThat(propertyBag.getProperty("$correlationId")).isEqualTo(CORRELATION_ID);
        assertThat(propertyBag.getProperty(APPLICATION_PROPERTY_KEY)).isEqualTo(APPLICATION_PROPERTY_VALUE);

    }

    /**
     * Verifies the command object when a request/response command message is received.
     */
    @Test
    public void testReceiveRequestResponseCommand() {
        final String subject = "the-subject";

        // GIVEN a command context for a request/response command
        when(commandMessage.getReplyTo()).thenReturn(REPLY_TO_ADDRESS);
        when(commandMessage.getSubject()).thenReturn(subject);

        final MqttCommandContext commandContext = MqttCommandContext.fromAmqpMessage(commandMessage, device);

        // WHEN the message is received
        final Command command = underTest.onCommandReceived(commandContext);

        // THEN the returned command object contains the correct topic filter, payload and topic
        assertThat(command.getTopicFilter()).isEqualTo(directMessageTopicFilter);
        assertThat(command.getPayload()).isEqualTo(payload);

        assertThat(command.getTopic()).isEqualTo("$iothub/methods/POST/" + subject + "/?$rid=" + REQUEST_ID);

    }

    /**
     * Verifies that authentication succeeds for the credentials in the demo-device config.
     */
    @Test
    public void authenticateDeviceSucceeds() {

        final Future<Device> deviceFuture = underTest.authenticateDevice(demoDeviceConfig.getUsername(),
                demoDeviceConfig.getPassword(), CLIENT_ID);

        assertThat(deviceFuture.succeeded()).isTrue();
        final Device result = deviceFuture.result();

        assertThat(result).isNotNull();
        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getDeviceId()).isEqualTo(DEVICE_ID);

    }

    /**
     * Verifies that authentication fails for unknown credentials.
     */
    @Test
    public void authenticateDeviceFailsForWrongCredentials() {
        final String user = demoDeviceConfig.getUsername();
        assertThat(underTest.authenticateDevice(user, "wrong-password", CLIENT_ID).succeeded()).isFalse();

        final String password = demoDeviceConfig.getPassword();
        assertThat(underTest.authenticateDevice("wrong-username", password, CLIENT_ID).succeeded()).isFalse();
    }

    /**
     * Verifies that MQTT messages with QoS 0 on the event topic are sent downstream as telemetry messages.
     */
    @Test
    public void testOnPublishedMessageForTelemetryMessage() {

        // GIVEN an MQTT message with QoS 0
        final MqttPublishMessage mqttPublishMessage = mock(MqttPublishMessage.class);
        when(mqttPublishMessage.payload()).thenReturn(payload);
        when(mqttPublishMessage.topicName()).thenReturn(AzureIotHubMqttGateway.getEventTopic(DEVICE_ID));
        when(mqttPublishMessage.qosLevel()).thenReturn(MqttQoS.AT_MOST_ONCE);

        final MqttDownstreamContext downstreamContext = MqttDownstreamContext.fromPublishPacket(mqttPublishMessage,
                mock(MqttEndpoint.class), device);

        // WHEN the message is received
        final Future<DownstreamMessage> messageFuture = underTest.onPublishedMessage(downstreamContext);

        // THEN a telemetry message with the payload is returned
        assertThat(messageFuture.succeeded()).isTrue();
        final DownstreamMessage result = messageFuture.result();

        assertThat(result).isInstanceOfAny(TelemetryMessage.class);
        assertThat(result.getPayload()).isEqualTo(payload.getBytes());

    }

    /**
     * Verifies that MQTT messages with QoS 1 on the event topic are sent downstream as event messages.
     */
    @Test
    public void testOnPublishedMessageForEventMessage() {

        // GIVEN an MQTT message with QoS 1
        final MqttPublishMessage mqttPublishMessage = mock(MqttPublishMessage.class);
        when(mqttPublishMessage.payload()).thenReturn(payload);
        when(mqttPublishMessage.topicName()).thenReturn(AzureIotHubMqttGateway.getEventTopic(DEVICE_ID));
        when(mqttPublishMessage.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);

        final MqttDownstreamContext downstreamContext = MqttDownstreamContext.fromPublishPacket(mqttPublishMessage,
                mock(MqttEndpoint.class), device);

        // WHEN the message is received
        final Future<DownstreamMessage> messageFuture = underTest.onPublishedMessage(downstreamContext);

        // THEN an event message with the payload is returned
        assertThat(messageFuture.succeeded()).isTrue();
        final DownstreamMessage result = messageFuture.result();

        assertThat(result).isInstanceOfAny(EventMessage.class);
        assertThat(result.getPayload()).isEqualTo(payload.getBytes());

    }

    /**
     * Verifies that MQTT messages on the direct method response topic are sent downstream as command response messages.
     */
    @Test
    public void testOnPublishedMessageForCommandResponse() {
        final int status = 200;

        // GIVEN an MQTT message with the direct method response topic
        final MqttPublishMessage mqttPublishMessage = mock(MqttPublishMessage.class);
        when(mqttPublishMessage.payload()).thenReturn(payload);
        when(mqttPublishMessage.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(mqttPublishMessage.topicName()).thenReturn(AzureIotHubMqttGateway.DIRECT_METHOD_RESPONSE_TOPIC_PREFIX
                + status + "/?$rid=" + REQUEST_ID);

        final MqttDownstreamContext downstreamContext = MqttDownstreamContext.fromPublishPacket(mqttPublishMessage,
                mock(MqttEndpoint.class), device);

        // WHEN the message is received
        final Future<DownstreamMessage> messageFuture = underTest.onPublishedMessage(downstreamContext);

        // THEN a command response message with the payload is returned...
        assertThat(messageFuture.succeeded()).isTrue();
        final DownstreamMessage result = messageFuture.result();

        assertThat(result).isInstanceOfAny(CommandResponseMessage.class);
        assertThat(result.getPayload()).isEqualTo(payload.getBytes());

        // ...AND its parameters are set correctly
        final CommandResponseMessage responseMessage = (CommandResponseMessage) result;
        assertThat(responseMessage.getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(responseMessage.getStatus()).isEqualTo(status);
        assertThat(responseMessage.getTargetAddress(TENANT_ID, DEVICE_ID)).isEqualTo(String.format("%s/%s/%s/%s",
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, TENANT_ID, DEVICE_ID, REPLY_ID));
        assertThat(responseMessage.getContentType()).isEqualTo("application/json");
    }

    /**
     * Verifies that the topic filters for cloud-to-device messages and for direct method responses are validated
     * successfully and other topic filters fail.
     */
    @Test
    public void isTopicFilterValid() {

        assertThat(underTest.isTopicFilterValid(cloudToDeviceTopicFilter, null, DEVICE_ID, null)).isTrue();
        assertThat(underTest.isTopicFilterValid(directMessageTopicFilter, null, null, null)).isTrue();

        final String unknownTopicFilter = "foo/#";
        assertThat(underTest.isTopicFilterValid(unknownTopicFilter, null, DEVICE_ID, null)).isFalse();
    }

}
