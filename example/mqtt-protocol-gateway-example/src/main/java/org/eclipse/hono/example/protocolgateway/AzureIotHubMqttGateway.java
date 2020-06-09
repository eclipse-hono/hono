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

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.AbstractMqttProtocolGateway;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.Command;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.MqttCommandContext;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.MqttDownstreamContext;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.MqttProtocolGatewayConfig;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.CommandResponseMessage;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.DownstreamMessage;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.EventMessage;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.TelemetryMessage;
import org.springframework.beans.factory.annotation.Autowired;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * A Protocol Gateway implementation that shows how production ready protocol gateways can be implemented using the base
 * class {@link AbstractMqttProtocolGateway} on the example of the "Azure IoT Hub". It provides parts of the MQTT
 * API of the Azure IoT Hub to show how to use the communication patterns that Hono provides.
 * <p>
 * This is not intended to be fully compatible with Azure IoT Hub. Especially it has the following limitations:
 * <ul>
 * <li>Device Twins are not supported</li>
 * <li>persistent sessions (cleanSession == 0) are not supported</li>
 * </ul>
 * <p>
 * <a href=
 * "https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-mqtt-support#sending-device-to-cloud-messages">Device-to-cloud
 * messages</a> are sent as events (if published with QoS 1) or telemetry messages (if published with QoS 0).
 *
 * Received one-way commands are forwarded to the device as
 * <a href= "https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-mqtt-support#receiving-cloud-to-device-messages">
 * cloud-to-device messages</a>. Received request/response commands are forwarded as
 * <a href= "https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-mqtt-support#respond-to-a-direct-method">direct
 * method messages</a> and direct method responses are sent as command responses.
 */
public class AzureIotHubMqttGateway extends AbstractMqttProtocolGateway {

    /**
     * The topic to which device-to-cloud messages are being sent.
     * <p>
     * The topic name is: {@code devices/{device_id}/messages/events/} or
     * {@code devices/{device_id}/messages/events/{property_bag}}.
     *
     * @see <a href=
     *      "https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-mqtt-support#sending-device-to-cloud-messages">
     *      Azure IoT Hub Documentation: "Sending device-to-cloud messages"</a>
     */
    public static final String EVENT_TOPIC_FORMAT_STRING = "devices/%s/messages/events/";

    /**
     * The topic filter to which devices have to subscribe for receiving cloud-to-device messages.
     * <p>
     * The topic filter is: {@code devices/{device_id}/messages/devicebound/#}.
     *
     * @see <a href=
     *      "https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-mqtt-support#receiving-cloud-to-device-messages">
     *      Azure IoT Hub Documentation: "Receiving cloud-to-device messages"</a>
     */
    public static final String CLOUD_TO_DEVICE_TOPIC_FILTER_FORMAT_STRING = "devices/%s/messages/devicebound/#";

    /**
     * The topic to which cloud-to-device messages are being sent.
     * <p>
     * The topic name is: {@code devices/{device_id}/messages/devicebound/} or
     * {@code devices/{device_id}/messages/devicebound/{property_bag}}.
     *
     * @see <a href=
     *      "https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-mqtt-support#receiving-cloud-to-device-messages">
     *      Azure IoT Hub Documentation: "Receiving cloud-to-device messages"</a>
     */
    public static final String CLOUD_TO_DEVICE_TOPIC_FORMAT_STRING = "devices/%s/messages/devicebound/";

    /**
     * The topic filter to which devices have to subscribe for receiving direct method messages.
     *
     * @see <a href= "https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-mqtt-support#respond-to-a-direct-method">
     *      Azure IoT Hub Documentation: "Respond to a direct method"</a>
     */
    public static final String DIRECT_METHOD_TOPIC_FILTER = "$iothub/methods/POST/#";

    /**
     * The topic to which direct method messages are being sent.
     * <p>
     * The topic name is: {@code $iothub/methods/POST/{method name}/?$rid={request id}}.
     * <p>
     * We use the command's subject as the method name.
     *
     * @see <a href= "https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-mqtt-support#respond-to-a-direct-method">
     *      Azure IoT Hub Documentation: "Respond to a direct method"</a>
     */
    public static final String DIRECT_METHOD_TOPIC_FORMAT_STRING = "$iothub/methods/POST/%s/?$rid=%s";

    /**
     * The prefix of the topic to which a response to a direct method is being sent.
     * <p>
     * The topic name is: {@code $iothub/methods/res/{status}/?$rid={request id}}.
     *
     * @see <a href= "https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-mqtt-support#respond-to-a-direct-method">
     *      Azure IoT Hub Documentation: "Respond to a direct method"</a>
     */
    public static final String DIRECT_METHOD_RESPONSE_TOPIC_PREFIX = "$iothub/methods/res/";

    private static final int DEVICE_TO_CLOUD_SIZE_LIMIT = 256 * 1024; // 256 KB
    private static final int DIRECT_METHOD_SIZE_LIMIT = 128 * 1024; // 128 KB
    private static final int CLOUD_TO_DEVICE_SIZE_LIMIT = 64 * 1024; // 64 KB

    @Autowired
    private DemoDeviceConfiguration demoDeviceConfig;

    /**
     * Creates an instance.
     *
     * @param amqpClientConfig The AMQP client configuration.
     * @param mqttServerConfig The MQTT server configuration.
     * @see AbstractMqttProtocolGateway#AbstractMqttProtocolGateway(ClientConfigProperties,
     *      MqttProtocolGatewayConfig) The constructor of the superclass for details.
     */
    public AzureIotHubMqttGateway(final ClientConfigProperties amqpClientConfig,
            final MqttProtocolGatewayConfig mqttServerConfig) {
        super(amqpClientConfig, mqttServerConfig);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<Device> authenticateDevice(final String username, final String password, final String clientId) {
        if (demoDeviceConfig.getUsername().equals(username) && demoDeviceConfig.getPassword().equals(password)) {
            return Future.succeededFuture(new Device(demoDeviceConfig.getTenantId(), demoDeviceConfig.getDeviceId()));
        } else {
            return Future.failedFuture(String.format("Authentication of device failed [username: %s]", username));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<DownstreamMessage> onPublishedMessage(final MqttDownstreamContext ctx) {
        final DownstreamMessage result;

        final String topic = ctx.topic();
        try {

            if (isDeviceToCloudTopic(topic, ctx.authenticatedDevice().getDeviceId())) {
                result = createDeviceToCloudMessage(ctx);
            } else if (isDirectMethodResponseTopic(topic)) {
                result = createDirectMethodResponseMessage(ctx);
            } else {
                throw new RuntimeException("unknown message type for topic " + topic);
            }

        } catch (RuntimeException e) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "published message is invalid", e));
        }

        // TODO Does Azure IoT Hub set the topic somewhere in the message?
        result.addApplicationProperty("topic", topic);

        PropertyBag.decode(topic).getPropertyBagIterator()
                .forEachRemaining(prop -> result.addApplicationProperty(prop.getKey(), prop.getValue()));

        return Future.succeededFuture(result);
    }

    private boolean isDeviceToCloudTopic(final String topic, final String deviceId) {
        return topic.startsWith(getEventTopic(deviceId));
    }

    private boolean isDirectMethodResponseTopic(final String topic) {
        return topic.startsWith(DIRECT_METHOD_RESPONSE_TOPIC_PREFIX);
    }

    private DownstreamMessage createDeviceToCloudMessage(final MqttDownstreamContext ctx) {
        final Buffer payload = ctx.message().payload();
        if (payload.length() > DEVICE_TO_CLOUD_SIZE_LIMIT) {
            throw new IllegalArgumentException(
                    String.format("device-to-cloud message is limited to %s KB", DEVICE_TO_CLOUD_SIZE_LIMIT));
        }

        final DownstreamMessage result;
        if (MqttQoS.AT_MOST_ONCE.equals(ctx.qosLevel())) {
            result = new TelemetryMessage(payload, false);
        } else {
            result = new EventMessage(payload);
        }

        if (ctx.message().isRetain()) {
            result.addApplicationProperty("x-opt-retain", true); // TODO Which value does Azure IoT Hub set here?
        }
        return result;
    }

    private DownstreamMessage createDirectMethodResponseMessage(final MqttDownstreamContext ctx) {

        validateDirectMethodPayload(ctx.message().payload());

        final PropertyBag propertyBag = PropertyBag.decode(ctx.topic());
        final RequestId requestId = RequestId.decode(propertyBag.getProperty("$rid"));

        final String status = ctx.topic().split("\\/")[3];

        final DownstreamMessage result = new CommandResponseMessage(requestId.getReplyId(),
                requestId.getCorrelationId(), status, ctx.message().payload());

        result.setContentType("application/json");
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isTopicFilterValid(final String topicFilter, final String tenantId, final String deviceId,
            final String clientId) {

        final boolean isCloudToDevice = getCloudToDeviceTopicFilter(deviceId).equals(topicFilter);
        final boolean isDirectMethod = DIRECT_METHOD_TOPIC_FILTER.equals(topicFilter);

        return isCloudToDevice || isDirectMethod;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Command onCommandReceived(final MqttCommandContext ctx) {

        final String topic;
        final String topicFilter;
        if (ctx.isRequestResponseCommand()) {
            validateDirectMethodPayload(ctx.getPayload());
            topic = getDirectMethodTopic(ctx);
            topicFilter = DIRECT_METHOD_TOPIC_FILTER;
        } else {
            validateCloudToDeviceMessage(ctx);
            topic = getCloudToDeviceTopic(ctx);
            topicFilter = getCloudToDeviceTopicFilter(ctx.getDevice().getDeviceId());
        }

        return new Command(topic, topicFilter, ctx.getPayload());
    }

    private void validateDirectMethodPayload(final Buffer payload) {
        if (payload.length() > DIRECT_METHOD_SIZE_LIMIT) {
            throw new IllegalArgumentException(
                    String.format("direct method response is limited to %s KB", DIRECT_METHOD_SIZE_LIMIT));
        }

        if (payload.length() > 0) {
            // validates that it is a JSON object
            final JsonObject jsonObject = payload.toJsonObject();
            log.trace("payload is JSON with {} entries", jsonObject.size());
        }
    }

    private void validateCloudToDeviceMessage(final MqttCommandContext ctx) {
        if (ctx.getPayload().length() > CLOUD_TO_DEVICE_SIZE_LIMIT) {
            throw new RuntimeException(
                    String.format("cloud-to-device message is limited to %s KB", CLOUD_TO_DEVICE_SIZE_LIMIT));
        }
    }

    private String getDirectMethodTopic(final MqttCommandContext ctx) {
        return getDirectMethodTopic(ctx.getSubject(), ctx.getReplyTo(), ctx.getCorrelationId());
    }

    private String getCloudToDeviceTopic(final MqttCommandContext ctx) {

        final String baseTopic = getCloudToDeviceTopic(ctx.getDevice().getDeviceId());

        final Map<String, Object> properties = Optional.ofNullable(ctx.getApplicationProperties())
                .map(ApplicationProperties::getValue)
                .orElse(new HashMap<>());

        addPropertyToMap(properties, "subject", ctx.getSubject());

        addPropertyToMap(properties, "$correlationId", ctx.getCorrelationId());
        addPropertyToMap(properties, "$messageId", ctx.getMessageId());

        return PropertyBag.encode(baseTopic, properties);
    }

    /**
     * Returns the event topic for the given device id.
     *
     * @param deviceId The id of the device that send messages to this topic.
     * @return The topic without a property bag.
     */
    public static String getEventTopic(final String deviceId) {
        return String.format(EVENT_TOPIC_FORMAT_STRING, deviceId);
    }

    /**
     * Returns the topic to which direct method messages are being sent for the given method name and request id.
     *
     * @param methodName The method name. This implementation uses the subject of the command message.
     * @param replyToAddress The reply-to address from the command message.
     * @param correlationId The correlation id from the command message.
     * @return The topic.
     */
    public static String getDirectMethodTopic(final String methodName, final String replyToAddress,
            final Object correlationId) {
        final String requestId = RequestId.encode(replyToAddress, correlationId);

        return String.format(DIRECT_METHOD_TOPIC_FORMAT_STRING, methodName, requestId);
    }

    /**
     * Returns the cloud-to-device topic for the given device id.
     *
     * @param deviceId The ID of the device to which the message is addressed.
     * @return The topic without a property bag.
     */
    public static String getCloudToDeviceTopic(final String deviceId) {
        return String.format(CLOUD_TO_DEVICE_TOPIC_FORMAT_STRING, deviceId);
    }

    /**
     * Returns the cloud-to-device topic filter for the given device id.
     *
     * @param deviceId The ID of the device that subscribes for cloud-to-device messages.
     * @return The topic filter.
     */
    public static String getCloudToDeviceTopicFilter(final String deviceId) {
        return String.format(CLOUD_TO_DEVICE_TOPIC_FILTER_FORMAT_STRING, deviceId);
    }

    private void addPropertyToMap(final Map<String, Object> map, final String key, final Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

}
