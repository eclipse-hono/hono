/*
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
package org.eclipse.hono.adapter.client.command.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.UUID;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.hono.adapter.client.command.CommandResponse;
import org.eclipse.hono.adapter.client.telemetry.kafka.TestHelper;
import org.eclipse.hono.client.kafka.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies behavior of {@link KafkaBasedCommandResponseSender}.
 */
@ExtendWith(VertxExtension.class)
public class KafkaBasedCommandResponseSenderTest {
    private final Tracer tracer = NoopTracerFactory.create();
    private KafkaProducerConfigProperties kafkaProducerConfig;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        kafkaProducerConfig = new KafkaProducerConfigProperties();
        kafkaProducerConfig.setProducerConfig(new HashMap<>());
    }

    @Test
    void testIfValidCommandResponseKafkaRecordIsSent(final VertxTestContext ctx) {
        // GIVEN a command response sender
        final String tenantId = "test-tenant";
        final String deviceId = "test-device";
        final String correlationId = UUID.randomUUID().toString();
        final String contentType = "the-content-type";
        final String payload = "the-payload";
        final int status = 200;
        final CommandResponse commandResponse = CommandResponse.fromCorrelationId(
                correlationId,
                String.format("%s/%s/%s/0", CommandConstants.COMMAND_RESPONSE_ENDPOINT, tenantId, deviceId),
                Buffer.buffer(payload),
                contentType,
                status);
        final MockProducer<String, Buffer> mockProducer = TestHelper.newMockProducer(true);
        final CachingKafkaProducerFactory<String, Buffer> factory = TestHelper.newProducerFactory(mockProducer);
        final KafkaBasedCommandResponseSender sender = new KafkaBasedCommandResponseSender(factory, kafkaProducerConfig,
                tracer);

        // WHEN sending a command response
        sender.sendCommandResponse(commandResponse, NoopSpan.INSTANCE.context())
                .onComplete(ctx.succeeding(t -> {
                    ctx.verify(() -> {
                        // THEN the producer record is created from the given values...
                        final ProducerRecord<String, Buffer> record = mockProducer.history().get(0);

                        assertThat(record.key()).isEqualTo(deviceId);
                        assertThat(record.topic())
                                .isEqualTo(new HonoTopic(HonoTopic.Type.COMMAND_RESPONSE, tenantId).toString());
                        assertThat(record.value().toString()).isEqualTo(payload);

                        //Verify if the record contains the necessary headers.
                        final Headers headers = record.headers();
                        assertThat(headers).containsOnlyOnce(
                                new RecordHeader(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId.getBytes()));
                        assertThat(headers).containsOnlyOnce(
                                new RecordHeader(MessageHelper.SYS_PROPERTY_CORRELATION_ID, correlationId.getBytes()));
                        assertThat(headers).containsOnlyOnce(
                                new RecordHeader(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId.getBytes()));
                        assertThat(headers).containsOnlyOnce(
                                new RecordHeader(MessageHelper.APP_PROPERTY_STATUS, Json.encode(status).getBytes()));
                        assertThat(headers).containsOnlyOnce(
                                new RecordHeader(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, contentType.getBytes()));
                    });
                    ctx.completeNow();
                }));
    }
}
