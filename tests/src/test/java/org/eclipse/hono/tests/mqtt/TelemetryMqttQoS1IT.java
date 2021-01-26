/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.amqp.AmqpMessageContext;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.extension.ExtendWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;


/**
 * Integration tests for uploading telemetry data to the MQTT adapter
 * using QoS 1.
 *
 */
@ExtendWith(VertxExtension.class)
public class TelemetryMqttQoS1IT extends MqttPublishTestBase {

    private static final String TOPIC_TEMPLATE = "%s/%s/%s";

    @Override
    protected MqttQoS getQos() {
        return MqttQoS.AT_LEAST_ONCE;
    }

    @Override
    protected Future<Void> send(
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final boolean useShortTopicName) {

        final String topic = String.format(
                TOPIC_TEMPLATE,
                useShortTopicName ? TelemetryConstants.TELEMETRY_ENDPOINT_SHORT : TelemetryConstants.TELEMETRY_ENDPOINT,
                tenantId,
                deviceId);
        final Promise<Void> result = Promise.promise();
        mqttClient.publish(
                topic,
                payload,
                getQos(),
                false, // is duplicate
                false, // is retained
                sendAttempt -> handlePublishAttempt(sendAttempt, result));
        return result.future();
    }

    @Override
    protected Future<MessageConsumer> createConsumer(
            final String tenantId,
            final Handler<DownstreamMessage<AmqpMessageContext>> messageConsumer) {

        return helper.amqpApplicationClient.createTelemetryConsumer(tenantId, messageConsumer, remoteClose -> {});
    }
}
