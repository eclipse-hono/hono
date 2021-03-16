/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.application.client.kafka.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.hono.client.kafka.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.noop.NoopSpan;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link KafkaBasedCommandSender}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class KafkaBasedCommandSenderTest {
    private KafkaBasedCommandSender commandSender;
    private MockProducer<String, Buffer> mockProducer;
    private String tenantId;
    private String deviceId;

    /**
     *
     * Sets up fixture.
     *
     */
    @BeforeEach
    void setUp() {
        final KafkaProducerConfigProperties producerConfig;
        final CachingKafkaProducerFactory<String, Buffer> producerFactory;

        producerConfig = new KafkaProducerConfigProperties();
        mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        producerFactory = KafkaClientUnitTestHelper.newProducerFactory(mockProducer);

        commandSender = new KafkaBasedCommandSender(producerFactory, producerConfig, NoopTracerFactory.create());

        tenantId = UUID.randomUUID().toString();
        deviceId = UUID.randomUUID().toString();
    }

    /**
     * Verifies that a command sent asynchronously to a device succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendAsyncCommandSucceeds(final VertxTestContext ctx) {
        final Map<String, Object> headerProperties = new HashMap<>();
        final String correlationId = UUID.randomUUID().toString();
        final String subject = "setVolume";
        headerProperties.put("appKey", "appValue");

        commandSender
                .sendAsyncCommand(tenantId, deviceId, subject, null, Buffer.buffer("{\"value\": 20}"), correlationId,
                null, headerProperties, NoopSpan.INSTANCE.context())
                .onComplete(ctx.succeeding(ok -> {
                    ctx.verify(() -> {
                        final ProducerRecord<String, Buffer> commandRecord = mockProducer.history().get(0);
                        assertThat(commandRecord.key()).isEqualTo(deviceId);
                        assertThat(commandRecord.headers()).containsOnlyOnce(
                                new RecordHeader(MessageHelper.SYS_PROPERTY_SUBJECT, subject.getBytes()));
                        assertThat(commandRecord.headers()).containsOnlyOnce(
                                new RecordHeader(MessageHelper.SYS_PROPERTY_CORRELATION_ID, correlationId.getBytes()));
                        assertThat(commandRecord.headers()).containsOnlyOnce(
                                new RecordHeader("appKey", "appValue".getBytes()));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a one-way command sent to a device succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendOneWayCommandSucceeds(final VertxTestContext ctx) {
        final Map<String, Object> headerProperties = new HashMap<>();
        final String subject = "setVolume";
        headerProperties.put("appKey", "appValue");

        commandSender
                .sendOneWayCommand(tenantId, deviceId, subject, null, Buffer.buffer("{\"value\": 20}"), headerProperties,
                NoopSpan.INSTANCE.context())
                .onComplete(ctx.succeeding(ok -> {
                    ctx.verify(() -> {
                        final ProducerRecord<String, Buffer> commandRecord = mockProducer.history().get(0);
                        assertThat(commandRecord.key()).isEqualTo(deviceId);
                        assertThat(commandRecord.headers()).containsOnlyOnce(
                                new RecordHeader(MessageHelper.SYS_PROPERTY_SUBJECT, subject.getBytes()));
                        assertThat(commandRecord.headers()).containsOnlyOnce(
                                new RecordHeader("appKey", "appValue".getBytes()));
                    });
                    ctx.completeNow();
                }));
    }
}
