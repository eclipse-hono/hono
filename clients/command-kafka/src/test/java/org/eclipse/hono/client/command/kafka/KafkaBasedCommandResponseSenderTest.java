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
package org.eclipse.hono.client.command.kafka;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.Commands;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies behavior of {@link KafkaBasedCommandResponseSender}.
 */
@ExtendWith(VertxExtension.class)
public class KafkaBasedCommandResponseSenderTest {

    private MessagingKafkaProducerConfigProperties kafkaProducerConfig;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        kafkaProducerConfig = new MessagingKafkaProducerConfigProperties();
        kafkaProducerConfig.setProducerConfig(new HashMap<>());
    }

    @Test
    void testIfValidCommandResponseKafkaRecordIsSent(final VertxTestContext ctx, final Vertx vertx) {

        // GIVEN a command response sender
        final String tenantId = "test-tenant";
        final String deviceId = "test-device";
        final String correlationId = UUID.randomUUID().toString();
        final String contentType = "text/plain";
        final String payload = "the-payload";
        final int status = 200;
        final String additionalHeader1Name = "testHeader1";
        final String additionalHeader1Value = "testHeader1Value";
        final String additionalHeader2Name = "testHeader2";
        final String additionalHeader2Value = "testHeader2Value";
        final Map<String, Object> additionalProperties = Map.of(
                additionalHeader1Name, additionalHeader1Value,
                additionalHeader2Name, additionalHeader2Value);
        final CommandResponse commandResponse = CommandResponse.fromAddressAndCorrelationId(
                String.format("%s/%s/%s", CommandConstants.COMMAND_RESPONSE_ENDPOINT, tenantId,
                        Commands.getDeviceFacingReplyToId("", deviceId, MessagingType.kafka)),
                correlationId,
                Buffer.buffer(payload),
                contentType,
                status);
        commandResponse.setAdditionalProperties(additionalProperties);

        final TenantObject tenant = TenantObject.from(tenantId);
        tenant.setResourceLimits(new ResourceLimits().setMaxTtlCommandResponse(10L));
        final Span span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);
        final var mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final var factory = CachingKafkaProducerFactory
                .testFactory(vertx, (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
        final Promise<Void> readyTracker = Promise.promise();
        final var sender = new KafkaBasedCommandResponseSender(vertx, factory, kafkaProducerConfig, tracer);
        sender.addOnKafkaProducerReadyHandler(readyTracker);

        // WHEN sending a command response
        sender.start()
            .compose(ok -> readyTracker.future())
            .compose(ok -> sender.sendCommandResponse(
                tenant,
                new RegistrationAssertion(deviceId),
                commandResponse, NoopSpan.INSTANCE.context()))
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
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            headers,
                            MessageHelper.APP_PROPERTY_TENANT_ID,
                            tenantId);
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            headers,
                            MessageHelper.APP_PROPERTY_DEVICE_ID,
                            deviceId);
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            headers,
                            MessageHelper.SYS_PROPERTY_CORRELATION_ID,
                            correlationId);
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            headers,
                            MessageHelper.APP_PROPERTY_STATUS,
                            status);
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            headers,
                            MessageHelper.SYS_PROPERTY_CONTENT_TYPE,
                            contentType);

                    final var creationTimeHeader = headers.headers(MessageHelper.SYS_PROPERTY_CREATION_TIME);
                    assertThat(creationTimeHeader).hasSize(1);
                    final Long creationTimeMillis = Json.decodeValue(
                            Buffer.buffer(creationTimeHeader.iterator().next().value()),
                            Long.class);
                    assertThat(creationTimeMillis).isGreaterThan(0L);
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            headers,
                            MessageHelper.SYS_HEADER_PROPERTY_TTL,
                            10000L);
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            headers,
                            additionalHeader1Name,
                            additionalHeader1Value);
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            headers,
                            additionalHeader2Name,
                            additionalHeader2Value);
                    verify(span).finish();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the sender registers itself for notifications of the type {@link TenantChangeNotification}.
     */
    @Test
    public void testThatNotificationConsumerIsRegistered() {
        final EventBus eventBus = mock(EventBus.class);
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);

        final Span span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);
        final var mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final var factory = CachingKafkaProducerFactory
                .testFactory(vertx, (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
        @SuppressWarnings("unused")
        final var sender = new KafkaBasedCommandResponseSender(vertx, factory, kafkaProducerConfig, tracer);

        verify(eventBus).consumer(eq(NotificationEventBusSupport.getEventBusAddress(TenantChangeNotification.TYPE)),
                VertxMockSupport.anyHandler());

        verifyNoMoreInteractions(eventBus);
    }
}
