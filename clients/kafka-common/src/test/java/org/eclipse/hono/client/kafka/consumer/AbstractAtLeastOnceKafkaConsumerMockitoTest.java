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

package org.eclipse.hono.client.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.internal.verification.VerificationModeFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecords;
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordsImpl;

/**
 * Verifies the behavior of {@link AbstractAtLeastOnceKafkaConsumer} with test cases that are not supported by
 * {@link MockConsumer}. Uses Mockito.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class AbstractAtLeastOnceKafkaConsumerMockitoTest {

    private static final String TOPIC = "test.topic";
    private static final int PARTITION = 0;

    /**
     * Verifies that {@link AbstractAtLeastOnceKafkaConsumer#start()} subscribes for the topic and polls for the first
     * batch of messages.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatStartSubscribesAndPolls(final VertxTestContext ctx) {

        final KafkaConsumer<String, Buffer> mockKafkaConsumer = mockVertxKafkaConsumer(0);
        final AbstractAtLeastOnceKafkaConsumer<JsonObject> underTest = new TestConsumer(mockKafkaConsumer, TOPIC);
        underTest.start()
                .onComplete(ctx.succeeding(v -> ctx.verify(() -> {

                    verify(mockKafkaConsumer).subscribe(eq(Set.of(TOPIC)), VertxMockSupport.anyHandler());
                    verify(mockKafkaConsumer, VerificationModeFactory.atLeastOnce()).poll(any(),
                            VertxMockSupport.anyHandler());

                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that when an error occurs during message committing, the consumer is closed and the close handler
     * invoked with the error.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatCommitErrorClosesConsumer(final VertxTestContext ctx) {
        final AtomicReference<TestConsumer> testConsumerRef = new AtomicReference<>();
        final KafkaException commitError = new KafkaException("commit failed");

        final KafkaConsumer<String, Buffer> mockKafkaConsumer = mockVertxKafkaConsumer(1);

        // WHEN committing fails
        doAnswer(invocation -> {
            final Promise<Void> promise = invocation.getArgument(1);
            promise.handle(Future.failedFuture(commitError));
            return 1L;
        }).when(mockKafkaConsumer).commit(anyMap(), VertxMockSupport.anyHandler());

        final Handler<Throwable> closeHandler = cause -> ctx.verify(() -> {

            // THEN the test consumer is closed...
            assertThat(testConsumerRef.get().stopped).isTrue();
            // ...AND the close handler is invoked with a KafkaConsumerCommitException, containing the cause
            assertThat(cause).isInstanceOf(KafkaConsumerCommitException.class);
            assertThat(cause.getCause()).isEqualTo(commitError);

            ctx.completeNow();
        });

        final TestConsumer testConsumer = TestConsumer.createWithCloseHandler(mockKafkaConsumer, TOPIC, closeHandler);
        testConsumerRef.set(testConsumer);

        // GIVEN a started test consumer
        testConsumer.start();

    }

    @SuppressWarnings("unchecked")
    private KafkaConsumer<String, Buffer> mockVertxKafkaConsumer(final int recordsPerPoll) {

        final KafkaConsumer<String, Buffer> mockKafkaConsumer = mock(KafkaConsumer.class);

        when(mockKafkaConsumer.subscribe(anySet(), VertxMockSupport.anyHandler()))
                .thenAnswer(invocationOnMock -> {
                    final Promise<Void> promise = invocationOnMock.getArgument(1);
                    promise.handle(Future.succeededFuture());
                    return mockKafkaConsumer;
                });

        when(mockKafkaConsumer.subscribe(any(Pattern.class), VertxMockSupport.anyHandler()))
                .thenAnswer(invocationOnMock -> {
                    final Promise<Void> promise = invocationOnMock.getArgument(1);
                    promise.handle(Future.succeededFuture());
                    return mockKafkaConsumer;
                });

        doAnswer(invocation -> {
            final Promise<Map<TopicPartition, OffsetAndMetadata>> promise = invocation.getArgument(1);
            promise.handle(Future.succeededFuture());
            return 1L;
        }).when(mockKafkaConsumer).commit(anyMap(), VertxMockSupport.anyHandler());

        doAnswer(invocation -> {
            final Promise<Void> promise = invocation.getArgument(0);
            promise.handle(Future.succeededFuture());
            return 1L;
        }).when(mockKafkaConsumer).close(VertxMockSupport.anyHandler());

        final List<ConsumerRecord<String, Buffer>> recordList = new ArrayList<>();
        for (int i = 0; i < recordsPerPoll; i++) {
            recordList.add(new ConsumerRecord<>(TOPIC, PARTITION, i, null, Buffer.buffer()));
        }
        final Map<TopicPartition, List<ConsumerRecord<String, Buffer>>> pollResult = recordsPerPoll > 0
                ? Map.of(new TopicPartition(TOPIC, PARTITION), recordList)
                : Map.of();

        final AtomicBoolean pollAlreadyCalled = new AtomicBoolean();
        doAnswer(invocation -> {
            final Promise<KafkaConsumerRecords<String, Buffer>> task = invocation.getArgument(1);
            // only invoke the poll() result handler on the first poll() invocation,
            // otherwise there's an endless poll()/handleBatch() loop because no decoupling via vert.x is used here
            if (pollAlreadyCalled.compareAndSet(false, true)) {
                task.handle(Future.succeededFuture(new KafkaConsumerRecordsImpl<>(new ConsumerRecords<>(pollResult))));
            }
            return 1L;
        }).when(mockKafkaConsumer).poll(any(Duration.class), VertxMockSupport.anyHandler());

        return mockKafkaConsumer;

    }

}
