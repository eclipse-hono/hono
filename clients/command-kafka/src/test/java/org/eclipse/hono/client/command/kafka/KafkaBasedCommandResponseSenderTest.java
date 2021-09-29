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
package org.eclipse.hono.client.command.kafka;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.Commands;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerConfigProperties;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingType;
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
        final String additionalHeader1Name = "testHeader1";
        final String additionalHeader1Value = "testHeader1Value";
        final String additionalHeader2Name = "testHeader2";
        final String additionalHeader2Value = "testHeader2Value";
        final Map<String, Object> additionalProperties = Map.of(additionalHeader1Name, additionalHeader1Value,
                additionalHeader2Name, additionalHeader2Value);
        final CommandResponse commandResponse = CommandResponse.fromAddressAndCorrelationId(
                String.format("%s/%s/%s", CommandConstants.COMMAND_RESPONSE_ENDPOINT, tenantId,
                        Commands.getDeviceFacingReplyToId("", deviceId, MessagingType.kafka)),
                correlationId,
                Buffer.buffer(payload),
                contentType,
                status);
        commandResponse.setAdditionalProperties(additionalProperties);
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final CachingKafkaProducerFactory<String, Buffer> factory = CachingKafkaProducerFactory
                .testFactory((n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
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
                        assertThat(headers.headers(MessageHelper.APP_PROPERTY_DEVICE_ID)).hasSize(1);
                        assertThat(headers).contains(
                                new RecordHeader(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId.getBytes()));

                        assertThat(headers.headers(MessageHelper.SYS_PROPERTY_CORRELATION_ID)).hasSize(1);
                        assertThat(headers).contains(
                                new RecordHeader(MessageHelper.SYS_PROPERTY_CORRELATION_ID, correlationId.getBytes()));

                        assertThat(headers.headers(MessageHelper.APP_PROPERTY_STATUS)).hasSize(1);
                        assertThat(headers).contains(
                                new RecordHeader(MessageHelper.APP_PROPERTY_STATUS, Json.encode(status).getBytes()));

                        assertThat(headers.headers(MessageHelper.SYS_PROPERTY_CONTENT_TYPE)).hasSize(1);
                        assertThat(headers).contains(
                                new RecordHeader(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, contentType.getBytes()));

                        assertThat(headers.headers(additionalHeader1Name)).hasSize(1);
                        assertThat(headers).contains(
                                new RecordHeader(additionalHeader1Name, additionalHeader1Value.getBytes()));
                        assertThat(headers.headers(additionalHeader2Name)).hasSize(1);
                        assertThat(headers).contains(
                                new RecordHeader(additionalHeader2Name, additionalHeader2Value.getBytes()));
                    });
                    ctx.completeNow();
                }));
    }
}
