/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import static org.mockito.Mockito.verify;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.kafka.KafkaMessageContext;
import org.eclipse.hono.client.SendMessageTimeoutException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.kafka.consumer.MessagingKafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
import org.eclipse.hono.kafka.test.KafkaMockConsumer;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Context;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.client.serialization.BufferSerializer;

/**
 * Tests verifying behavior of {@link KafkaBasedCommandSender}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class KafkaBasedCommandSenderTest {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBasedCommandSenderTest.class);

    private KafkaBasedCommandSender commandSender;
    private Promise<Void> commandSenderReadyTracker;
    private MessagingKafkaConsumerConfigProperties consumerConfig;
    private MessagingKafkaProducerConfigProperties producerConfig;
    private MockProducer<String, Buffer> mockProducer;
    private KafkaMockConsumer<String, Buffer> mockConsumer;
    private String tenantId;
    private String deviceId;
    private Vertx vertx;
    private Tracer tracer;
    private Span span;

    /**
     *
     * Sets up fixture.
     *
     * @param vertx The vert.x instance to use.
     */
    @BeforeEach
    void setUp(final Vertx vertx) {
        this.vertx = vertx;
        consumerConfig = new MessagingKafkaConsumerConfigProperties();
        mockConsumer = new KafkaMockConsumer<>(OffsetResetStrategy.LATEST);
        producerConfig = new MessagingKafkaProducerConfigProperties();
        producerConfig.setProducerConfig(Map.of("client.id", "application-test-sender"));

        span = TracingMockSupport.mockSpan();
        tracer = TracingMockSupport.mockTracer(span);

        mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final var producerFactory = CachingKafkaProducerFactory
                .testFactory(vertx, (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
        commandSenderReadyTracker = Promise.promise();
        commandSender = new KafkaBasedCommandSender(
                vertx,
                consumerConfig,
                producerFactory,
                producerConfig,
                tracer);
        commandSender.addOnKafkaProducerReadyHandler(commandSenderReadyTracker);
        tenantId = UUID.randomUUID().toString();
        deviceId = UUID.randomUUID().toString();
    }

    /**
     * Stops the created command sender.
     *
     * @param context The vert.x test context.
     */
    @AfterEach
    void shutDown(final VertxTestContext context) {
        commandSender.stop().onComplete(r -> context.completeNow());
    }

    /**
     * Verifies that a command sent asynchronously to a device succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendAsyncCommandSucceeds(final VertxTestContext ctx) {
        final String correlationId = UUID.randomUUID().toString();
        final String subject = "setVolume";

        commandSender.start()
            .compose(ok -> commandSenderReadyTracker.future())
            .compose(ok -> commandSender.sendAsyncCommand(
                    tenantId,
                    deviceId,
                    subject,
                    correlationId,
                    new JsonObject().put("value", 20).toBuffer(),
                    "application/json",
                    Map.of("foo", "bar"),
                    NoopSpan.INSTANCE.context()))
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    final ProducerRecord<String, Buffer> commandRecord = mockProducer.history().get(0);
                    assertThat(commandRecord.key()).isEqualTo(deviceId);

                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            commandRecord.headers(),
                            MessageHelper.APP_PROPERTY_DEVICE_ID,
                            deviceId);
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            commandRecord.headers(),
                            MessageHelper.SYS_PROPERTY_SUBJECT,
                            subject);
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            commandRecord.headers(),
                            MessageHelper.SYS_PROPERTY_CONTENT_TYPE,
                            "application/json");
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            commandRecord.headers(),
                            MessageHelper.SYS_PROPERTY_CORRELATION_ID,
                            correlationId);
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            commandRecord.headers(),
                            KafkaRecordHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX + ".foo",
                            "bar");
                    verify(span).finish();
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
        final String subject = "setVolume";

        commandSender.start()
            .compose(ok -> commandSenderReadyTracker.future())
            .compose(ok -> commandSender.sendOneWayCommand(
                    tenantId,
                    deviceId,
                    subject,
                    new JsonObject().put("value", 20).toBuffer(),
                    "application/json",
                    NoopSpan.INSTANCE.context()))
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    final ProducerRecord<String, Buffer> commandRecord = mockProducer.history().get(0);
                    assertThat(commandRecord.key()).isEqualTo(deviceId);

                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            commandRecord.headers(),
                            MessageHelper.APP_PROPERTY_DEVICE_ID,
                            deviceId);
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            commandRecord.headers(),
                            MessageHelper.SYS_PROPERTY_SUBJECT,
                            subject);
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            commandRecord.headers(),
                            MessageHelper.SYS_PROPERTY_CONTENT_TYPE,
                            "application/json");
                    verify(span).finish();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the sending a command fails as the timeout is reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendCommandAndReceiveResponseTimesOut(final VertxTestContext ctx) {
        final Context context = vertx.getOrCreateContext();
        commandSender.setKafkaConsumerSupplier(() -> mockConsumer);
        context.runOnContext(v -> {
            commandSender.start()
                .compose(ok -> commandSenderReadyTracker.future())
                .compose(ok -> commandSender.sendCommand(
                        tenantId,
                        deviceId,
                        "testCommand",
                        Buffer.buffer("data"),
                        "text/plain",
                        (Map<String, Object>) null, // no failure header props
                        Duration.ofMillis(5),
                        null))
                .onComplete(ctx.failing(error -> {
                    ctx.verify(() -> {
                        // VERIFY that the error is caused due to time out.
                        assertThat(error).isInstanceOf(SendMessageTimeoutException.class);
                        verify(span).finish();
                    });
                    ctx.completeNow();
                }));
        });
    }

    /**
     * Verifies that a command sent to a device succeeds and also a response is received from the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendCommandAndReceiveResponse(final VertxTestContext ctx) {
        final String correlationId = UUID.randomUUID().toString();
        final int responseStatus = HttpURLConnection.HTTP_OK;

        sendCommandAndReceiveResponse(ctx, correlationId, responseStatus, "success", true, responseStatus);
    }

    /**
     * Verifies that sending a command fails if the response status indicates a failure.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendFailingCommandAndReceiveResponse(final VertxTestContext ctx) {
        final String correlationId = UUID.randomUUID().toString();
        final int responseStatus = HttpURLConnection.HTTP_FORBIDDEN;

        sendCommandAndReceiveResponse(ctx, correlationId, responseStatus, "failure", false, responseStatus);
    }

    /**
     * Verifies that sending a command fails if the response does not contain a status header.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendCommandAndReceiveResponseWithoutStatus(final VertxTestContext ctx) {
        final String correlationId = UUID.randomUUID().toString();

        sendCommandAndReceiveResponse(ctx, correlationId, null, "failure", false, 500);
    }

    private void sendCommandAndReceiveResponse(
            final VertxTestContext ctx,
            final String correlationId,
            final Integer responseStatus,
            final String responsePayload,
            final boolean expectSuccess,
            final int expectedStatusCode) {

        final Context context = vertx.getOrCreateContext();
        final Promise<Void> onProducerRecordSentPromise = Promise.promise();
        mockProducer = new MockProducer<>(true, new StringSerializer(), new BufferSerializer()) {
            @Override
            public synchronized java.util.concurrent.Future<RecordMetadata> send(final ProducerRecord<String, Buffer> record,
                    final Callback callback) {
                return super.send(record, (metadata, exception) -> {
                    callback.onCompletion(metadata, exception);
                    context.runOnContext(v -> { // decouple from current execution in order to run after the "send" result handler
                        onProducerRecordSentPromise.complete();
                    });
                });
            }
        };
        final var producerFactory = CachingKafkaProducerFactory.testFactory(
                vertx,
                (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
        commandSender = new KafkaBasedCommandSender(
                vertx,
                consumerConfig,
                producerFactory,
                producerConfig,
                NoopTracerFactory.create());
        commandSender.addOnKafkaProducerReadyHandler(commandSenderReadyTracker);

        final String command = "setVolume";
        final ConsumerRecord<String, Buffer> commandResponseRecord = commandResponseRecord(tenantId,
                deviceId, correlationId, responseStatus, Buffer.buffer(responsePayload));
        final String responseTopic = new HonoTopic(HonoTopic.Type.COMMAND_RESPONSE, tenantId).toString();
        final TopicPartition responseTopicPartition = new TopicPartition(responseTopic, 0);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(responseTopicPartition));
        mockConsumer.updatePartitions(responseTopicPartition, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.updateBeginningOffsets(Map.of(responseTopicPartition, 0L));
        mockConsumer.updateEndOffsets(Map.of(responseTopicPartition, 0L));
        onProducerRecordSentPromise.future().onComplete(ar -> {
            LOG.debug("producer record sent, add command response record to mockConsumer");
            // Send a command response with the same correlation id as that of the command
            mockConsumer.addRecord(commandResponseRecord);
        });

        // This correlation id is used for both command and its response.
        commandSender.setCorrelationIdSupplier(() -> correlationId);
        commandSender.setKafkaConsumerSupplier(() -> mockConsumer);

        context.runOnContext(v -> {
            // Send a command to the device
            commandSender.start()
                .compose(ok -> commandSenderReadyTracker.future())
                .compose(ok -> commandSender.sendCommand(tenantId, deviceId, command, Buffer.buffer("test"), "text/plain"))
                .onComplete(ar -> {
                    ctx.verify(() -> {
                        if (expectSuccess) {
                            assertThat(ar.succeeded()).isTrue(); // assert that send operation succeeded

                            // Verify the command response that has been received
                            final DownstreamMessage<KafkaMessageContext> response = ar.result();
                            assertThat(response.getDeviceId()).isEqualTo(deviceId);
                            assertThat(response.getStatus()).isEqualTo(responseStatus);
                            assertThat(response.getPayload().toString()).isEqualTo(responsePayload);
                        } else {
                            assertThat(ar.succeeded()).isFalse(); // assert that send operation failed
                            assertThat(ar.cause()).isInstanceOf(ServiceInvocationException.class);
                            assertThat(((ServiceInvocationException) ar.cause()).getErrorCode())
                                    .isEqualTo(expectedStatusCode);
                            assertThat(ar.cause().getMessage()).isEqualTo(responsePayload);
                        }
                    });
                    ctx.completeNow();
                });
        });
    }

    private ConsumerRecord<String, Buffer> commandResponseRecord(final String tenantId, final String deviceId,
            final String correlationId, final Integer status, final Buffer payload) {
        final List<Header> headers = new ArrayList<>();

        headers.add(new RecordHeader(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId.getBytes()));
        headers.add(new RecordHeader(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId.getBytes()));
        headers.add(new RecordHeader(MessageHelper.SYS_PROPERTY_CORRELATION_ID, correlationId.getBytes()));
        Optional.ofNullable(status)
            .ifPresent(s -> headers.add(new RecordHeader(
                    MessageHelper.APP_PROPERTY_STATUS,
                    String.valueOf(s).getBytes())));
        return new ConsumerRecord<>(
                new HonoTopic(HonoTopic.Type.COMMAND_RESPONSE, tenantId).toString(),
                0,
                0,
                -1L,
                TimestampType.NO_TIMESTAMP_TYPE,
                -1,
                -1,
                deviceId,
                payload,
                new RecordHeaders(headers.toArray(Header[]::new)),
                Optional.empty());
    }
}
