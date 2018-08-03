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
import org.eclipse.hono.util.EventConstants;
import org.junit.runner.RunWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Integration tests for uploading events to the MQTT adapter.
 *
 */
@RunWith(VertxUnitRunner.class)
public class EventMqttIT extends MqttTestBase {

    private static final String TOPIC_TEMPLATE = "%s/%s/%s";

    @Override
    protected Future<Void> send(
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final boolean useShortTopicName) {

        final String topic = String.format(
                TOPIC_TEMPLATE,
                useShortTopicName ? EventConstants.EVENT_ENDPOINT_SHORT : EventConstants.EVENT_ENDPOINT,
                tenantId,
                deviceId);
        final Future<Void> result = Future.future();
        mqttClient.publishCompletionHandler(id -> result.complete());
        mqttClient.publish(topic, payload, MqttQoS.AT_LEAST_ONCE, false, false);
        return result;
    }

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.honoClient.createEventConsumer(tenantId, messageConsumer, remoteClose -> {});
    }

    @Override
    protected void assertAdditionalMessageProperties(final TestContext ctx, final Message msg) {
        // assert that events are marked as "durable"
        ctx.assertTrue(msg.isDurable());
    }
}
