/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.tests.mqtt;

import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.util.EventConstants;
import org.junit.runner.RunWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Integration tests for uploading events to the MQTT adapter.
 *
 */
@RunWith(VertxUnitRunner.class)
public class EventMqttIT extends MqttTestBase {

    private static final String TOPIC_TEMPLATE = EventConstants.EVENT_ENDPOINT + "/%s/%s";

    @Override
    protected void send(String tenantId, String deviceId, Buffer payload, final Handler<AsyncResult<Integer>> publishSentHandler) {

        final String topic = String.format(TOPIC_TEMPLATE, tenantId, deviceId);
        mqttClient.publish(topic, payload, MqttQoS.AT_LEAST_ONCE, false, false, publishSentHandler);
    }

    @Override
    protected Future<MessageConsumer> createConsumer(String tenantId, Consumer<Message> messageConsumer) {

        return helper.downstreamClient.createEventConsumer(tenantId, messageConsumer, remoteClose -> {});
    }
}
