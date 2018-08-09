/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.runner.RunWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Integration tests for uploading telemetry data to the MQTT adapter
 * using QoS 1.
 *
 */
@RunWith(VertxUnitRunner.class)
public class TelemetryMqttQoS1IT extends MqttPublishTestBase {

    private static final String TOPIC_TEMPLATE = "%s/%s/%s";

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
        final Future<Void> result = Future.future();
        mqttClient.publishCompletionHandler(id -> result.complete());
        mqttClient.publish(topic, payload, MqttQoS.AT_LEAST_ONCE, false, false);
        return result;
    }

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.honoClient.createTelemetryConsumer(tenantId, messageConsumer, remoteClose -> {});
    }
}
