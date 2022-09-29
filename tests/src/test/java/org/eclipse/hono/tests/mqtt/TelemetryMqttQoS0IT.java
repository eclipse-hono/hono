/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.mqtt;

import java.util.Map;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.tests.EnabledIfProtocolAdaptersAreRunning;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.extension.ExtendWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Integration tests for uploading telemetry data to the MQTT adapter
 * using QoS 0.
 *
 */
@ExtendWith(VertxExtension.class)
@EnabledIfProtocolAdaptersAreRunning(mqttAdapter = true)
public class TelemetryMqttQoS0IT extends MqttPublishTestBase {

    private static final String TOPIC_TEMPLATE = "%s/%s/%s";

    @Override
    protected MqttQoS getQos() {
        return MqttQoS.AT_MOST_ONCE;
    }

    @Override
    protected Future<Integer> send(
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final boolean useShortTopicName,
            final boolean includeTenantIdInTopic,
            final Map<String, String> topicPropertyBag) {

        final String topic = String.format(
                TOPIC_TEMPLATE,
                useShortTopicName ? TelemetryConstants.TELEMETRY_ENDPOINT_SHORT : TelemetryConstants.TELEMETRY_ENDPOINT,
                includeTenantIdInTopic ? tenantId : "",
                deviceId);
        final Promise<Integer> result = Promise.promise();
        // throttle sending to allow adapter to be replenished with credits from consumer
        vertx.setTimer(5, go -> {
            mqttClient.publish(
                    getTopicWithPropertyBag(topic, topicPropertyBag),
                    payload,
                    getQos(),
                    false, // is duplicate
                    false, // is retained
                    result);
        });
        return result.future();
    }

    @Override
    protected void assertMessageReceivedRatio(final long received, final long sent, final VertxTestContext ctx) {

        final int expectedPercentage;
        if (IntegrationTestSupport.isTestEnvironment()) {
            LOGGER.info("running on CI test environment, allowing for 100 percent of messages to be lost ...");
            expectedPercentage = 0;
        } else {
            LOGGER.info("running on default environment, requiring 90 percent of messages to be received ...");
            expectedPercentage = 90;
        }
        final int expected = Math.round(sent * expectedPercentage / 100);
        if (received < expected) {
            // fail if less than 90% of sent messages have been received
            final String msg = String.format("did not receive expected number of messages [expected: %d, received: %d]",
                    expected, received);
            ctx.failNow(new IllegalStateException(msg));
        } else {
            ctx.completeNow();
        }
    }

    @Override
    protected long getTimeToWait() {
        if (IntegrationTestSupport.isTestEnvironment()) {
            return MESSAGES_TO_SEND * 100;
        } else {
            return MESSAGES_TO_SEND * 20;
        }
    }

    @Override
    protected Future<MessageConsumer> createConsumer(
            final String tenantId,
            final Handler<DownstreamMessage<? extends MessageContext>> messageConsumer) {

        return helper.applicationClient.createTelemetryConsumer(
                tenantId,
                messageConsumer::handle,
                close -> {});
    }
}
