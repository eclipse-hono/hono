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

package org.eclipse.hono.cli.app;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Test cases verifying the behavior of {@code KafkaReceiver}.
 *
 */
@ExtendWith(VertxExtension.class)
public class KafkaReceiverTest {

    private KafkaReceiver receiver;

    /**
     * Sets up the receiver with mocks.
     *
     */
    @BeforeEach
    public void setup() {

        final Vertx vertx = mock(Vertx.class);
        final Context context = VertxMockSupport.mockContext(vertx);
        when(vertx.getOrCreateContext()).thenReturn(context);

        final KafkaConsumerConfigProperties config = new KafkaConsumerConfigProperties();
        final Map<String, String> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "hono-cli");

        config.setConsumerConfig(properties);

        receiver = new KafkaReceiver();
        receiver.setConfig(config);
        receiver.setVertx(vertx);
        receiver.tenantId = "TEST_TENANT";
    }

    /**
     * Verifies that the receiver is started successfully with message.type=telemetry.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testTelemetryStart(final VertxTestContext context) {
        receiver.messageType = "telemetry";

        receiver.start().onComplete(context.completing());
    }

    /**
     * Verifies that the receiver is started successfully with message.type=event.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testEventStart(final VertxTestContext context) {
        receiver.messageType = "event";

        receiver.start().onComplete(context.completing());
    }

    /**
     * Verifies that the receiver is started successfully with message.type=all.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testDefaultStart(final VertxTestContext context) {
        receiver.messageType = "all";

        receiver.start().onComplete(context.completing());

    }

    /**
     * Verifies that the receiver fails to start when invalid value is passed to message.type.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testInvalidTypeStart(final VertxTestContext context) {
        receiver.messageType = "xxxxx";
        receiver.start().onComplete(
                context.failing(result -> context.completeNow()));
    }

    /**
     * Verifies that the receiver fails to start when the tenant ID is not set.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testThatStartFailsIfTenantIdIsEmpty(final VertxTestContext context) {
        receiver.tenantId = "";
        receiver.start().onComplete(
                context.failing(result -> context.completeNow()));
    }

}
