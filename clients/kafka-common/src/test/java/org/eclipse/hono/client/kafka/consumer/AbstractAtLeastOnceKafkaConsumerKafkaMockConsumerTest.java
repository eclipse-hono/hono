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

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.client.consumer.KafkaConsumer;

/**
 * Verifies the behavior of {@link AbstractAtLeastOnceKafkaConsumer} with tests using {@link MockConsumer}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class AbstractAtLeastOnceKafkaConsumerKafkaMockConsumerTest {

    private static final Logger LOG = LoggerFactory
            .getLogger(AbstractAtLeastOnceKafkaConsumerKafkaMockConsumerTest.class);

    private static final String TOPIC = "test.topic";
    private static final int PARTITION = 0;
    private final TopicPartition topicPartition = new TopicPartition(TOPIC, PARTITION);

    private MockConsumer<String, Buffer> mockConsumer;
    private KafkaConsumer<String, Buffer> vertxKafkaConsumer;

    @BeforeEach
    void setUp(final Vertx vertx) {
        mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        vertxKafkaConsumer = KafkaConsumer.create(vertx, mockConsumer);

    }

    /**
     * Verifies that the underlying Kafka consumer is closed when {@link AbstractAtLeastOnceKafkaConsumer#stop()} is
     * invoked.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatStopClosesConsumer(final VertxTestContext ctx) {
        final AbstractAtLeastOnceKafkaConsumer<JsonObject> underTest = new TestConsumer(vertxKafkaConsumer, TOPIC);
        underTest.start()
                .onComplete(ctx.succeeding(started -> underTest.stop()
                        .onComplete(ctx.succeeding(stopped -> ctx.verify(() -> {

                            assertThat(underTest.stopped).isTrue();
                            assertThat(mockConsumer.closed()).isTrue();

                            ctx.completeNow();
                        })))));
    }

    /**
     * Verifies that when {@link AbstractAtLeastOnceKafkaConsumer#stop()} is invoked, the consumer cannot be started
     * again. This is important because the underlying Kafka consumer cannot be restarted.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatStartFailsIfConsumerIsAlreadyStopped(final VertxTestContext ctx) {
        // GIVEN a started consumer
        final AbstractAtLeastOnceKafkaConsumer<JsonObject> underTest = new TestConsumer(vertxKafkaConsumer, TOPIC);
        underTest.start()
                // WHEN stopping
                .onComplete(ctx.succeeding(started -> underTest.stop()
                        .onComplete(ctx.succeeding(stopped -> {
                            // THEN it cannot be started again
                            underTest.start().onComplete(ctx.failing(cause -> ctx.verify(() -> {
                                assertThat(cause).hasMessage("consumer already stopped");
                                ctx.completeNow();
                            })));
                        }))));
    }

    /**
     * Verifies that when {@link AbstractAtLeastOnceKafkaConsumer#start()} fails, if it cannot successfully poll
     * messages from Kafka.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatStartFailsIfFirstPollFails(final VertxTestContext ctx) {
        final AuthenticationException authenticationException = new AuthenticationException("auth failed");

        // WHEN the initial poll operation fails
        mockConsumer.setPollException(authenticationException);

        // GIVEN a started consumer
        new TestConsumer(vertxKafkaConsumer, TOPIC).start()
                // THEN the start fails
                .onComplete(ctx.failing(cause -> ctx.verify(() -> {

                    assertThat(cause).isInstanceOf(KafkaConsumerPollException.class);
                    assertThat(cause.getCause()).isEqualTo(authenticationException);

                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that when an error occurs during message polling, the consumer is closed and the close handler invoked
     * with the error.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatPollErrorClosesConsumer(final VertxTestContext ctx) {
        final AtomicReference<TestConsumer> testConsumerRef = new AtomicReference<>();
        final KafkaException pollError = new KafkaException("error");

        final TestConsumer testConsumer = TestConsumer.createWithCloseHandler(vertxKafkaConsumer, TOPIC,
                cause -> ctx.verify(() -> {

                    // THEN the test consumer is closed...
                    assertThat(testConsumerRef.get().stopped).isTrue();
                    assertThat(mockConsumer.closed()).isTrue();
                    // ...AND the close handler is invoked with a KafkaConsumerPollException, containing the cause
                    assertThat(cause).isInstanceOf(KafkaConsumerPollException.class);
                    assertThat(cause.getCause()).isEqualTo(pollError);

                    ctx.completeNow();
                }));

        testConsumerRef.set(testConsumer);

        // GIVEN a started test consumer
        testConsumer.start()
                .onComplete(ctx.succeeding(v -> {

                    // WHEN polling fails after the first poll operation
                    mockConsumer.setPollException(pollError);
                }));

    }

    /**
     * Verifies that the consumer polls multiple batches of messages, processes them in right order and commits the
     * offset after processing of a batch finished.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatConsumedMessagesAreProcessedInOrder(final VertxTestContext ctx) {
        final int numberOfBatches = 11;
        final int recordsPerBatch = 20;

        final AtomicInteger expectedOffset = new AtomicInteger();

        final Handler<JsonObject> messageHandler = msg -> {
            final Long currentOffset = msg.getLong(TestConsumer.RECORD_OFFSET);
            ctx.verify(() -> {

                // THEN the messages are processed in order
                LOG.debug("current offset: {}, expected offset: {}", currentOffset, expectedOffset.get());
                assertThat(currentOffset).isEqualTo(expectedOffset.getAndIncrement());

                final boolean firstMessageInBatch = currentOffset % recordsPerBatch == 0;
                if (firstMessageInBatch) {
                    // ... AND commit points to the first message of the batch (you commit the NEXT offset to be read)
                    assertThat(getCommittedOffset(topicPartition)).isEqualTo(currentOffset);
                }
            });

            if (currentOffset == (recordsPerBatch * (numberOfBatches - 1))) { // last batch started
                ctx.completeNow(); // we don't know when the last commit finished, but we verified committing already
            }
        };

        // GIVEN a started consumer
        TestConsumer.createWithMessageHandler(vertxKafkaConsumer, TOPIC, messageHandler).start()
                .onComplete(ctx.succeeding(v -> {

                    // WHEN consuming multiple batches of of records
                    mockConsumer.updateBeginningOffsets(Map.of(topicPartition, ((long) 0))); // define start offset
                    mockConsumer.rebalance(Collections.singletonList(topicPartition)); // trigger partition assignment
                    for (int i = 0; i < numberOfBatches; i++) {
                        scheduleBatch(i * recordsPerBatch, recordsPerBatch);
                    }

                }));
    }

    /**
     * Verifies that when the message handler throws a runtime exception, the offsets are committed and the consumer
     * polls again starting from the failed record without closing the consumer.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatErrorInMessageHandlerCausesRedelivery(final VertxTestContext ctx) {
        final int recordsPerBatch = 20;
        final int failingMessageOffset = recordsPerBatch - 1;

        final AtomicInteger messageHandlerInvocations = new AtomicInteger();

        final Handler<JsonObject> messageHandler = msg -> {

            if (messageHandlerInvocations.getAndIncrement() == failingMessageOffset) {
                // WHEN the message handler throws a runtime exception
                throw new RuntimeException();
            }

            ctx.verify(() -> {
                // THEN the offset of the failed message is committed...
                if (messageHandlerInvocations.get() >= recordsPerBatch) {
                    assertThat(getCommittedOffset(topicPartition)).isEqualTo(failingMessageOffset);
                }
            });

            // ...AND the consumer keeps consuming
            if (messageHandlerInvocations.get() == (recordsPerBatch * 2) - 1) {
                ctx.completeNow();
            }
        };

        final Handler<Throwable> closeHandler = cause -> ctx
                .failNow(new RuntimeException("Consumer closed unexpectedly", cause)); // must not happen

        // GIVEN a started consumer that polls two batches
        new TestConsumer(vertxKafkaConsumer, TOPIC, messageHandler, closeHandler).start()
                .onComplete(ctx.succeeding(v -> {

                    mockConsumer.updateBeginningOffsets(Map.of(topicPartition, ((long) 0))); // define start offset
                    mockConsumer.rebalance(Collections.singletonList(topicPartition)); // trigger partition assignment
                    scheduleBatch(0, recordsPerBatch);
                    scheduleBatch(failingMessageOffset, recordsPerBatch); // start from failed message

                }));
    }

    /**
     * Verifies that when a record contains a TTL which is elapsed, then the message handler is not invoked but the
     * offset is committed.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatTtlIsRespected(final VertxTestContext ctx) {
        final ConsumerRecord<String, Buffer> recordWithElapsedTtl = createRecordWithElapsedTtl();

        final Handler<JsonObject> messageHandler = msg -> {
            ctx.verify(() -> {

                // THEN the message handler is not invoked for the elapsed record
                assertThat(msg.getLong(TestConsumer.RECORD_OFFSET)).isNotEqualTo(0);
                // ... AND the offsets have been committed
                assertThat(getCommittedOffset(topicPartition)).isEqualTo(1);
            });
            ctx.completeNow();
        };

        // GIVEN a started consumer
        TestConsumer.createWithMessageHandler(vertxKafkaConsumer, TOPIC, messageHandler).start()
                .onComplete(ctx.succeeding(v -> {
                    mockConsumer.updateBeginningOffsets(Map.of(topicPartition, ((long) 0))); // define start offset
                    mockConsumer.rebalance(Collections.singletonList(topicPartition)); // trigger partition assignment

                    // WHEN a record with elapsed TLL is polled
                    mockConsumer.schedulePollTask(() -> mockConsumer.addRecord(recordWithElapsedTtl));
                    scheduleBatch(1, 1);
                }));
    }

    /**
     * Verifies that when a record contains a TTL which is elapsed but the consumer is configured to not respect the
     * TTL, then the message handler for the record is still invoked.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatTtlCanBeIgnored(final VertxTestContext ctx) {
        final Checkpoint messageHandlerInvocations = ctx.checkpoint(2);

        final ConsumerRecord<String, Buffer> recordWithElapsedTtl = createRecordWithElapsedTtl();

        // THEN the message handler is still invoked for the elapsed record
        final Handler<JsonObject> messageHandler = msg -> ctx.verify(messageHandlerInvocations::flag);

        // GIVEN a started consumer
        final TestConsumer consumer = TestConsumer.createWithMessageHandler(vertxKafkaConsumer, TOPIC, messageHandler);

        // WHEN the consumer is configured to not respect the TTL
        consumer.setRespectTtl(false);
        consumer.start().onComplete(ctx.succeeding(v -> {
            mockConsumer.updateBeginningOffsets(Map.of(topicPartition, ((long) 0))); // define start offset
            mockConsumer.rebalance(Collections.singletonList(topicPartition)); // trigger partition assignment

            mockConsumer.schedulePollTask(() -> mockConsumer.addRecord(recordWithElapsedTtl));
            scheduleBatch(1, 1);
        }));
    }

    private ConsumerRecord<String, Buffer> createRecordWithElapsedTtl() {
        final byte[] ttl1Second = "1".getBytes();
        final RecordHeader ttl = new RecordHeader("ttl", ttl1Second);

        final byte[] timestamp2SecondsAgo = Json.encode(Instant.now().minusSeconds(2).toEpochMilli()).getBytes();
        final RecordHeader creationTime = new RecordHeader("creation-time", timestamp2SecondsAgo);

        return new ConsumerRecord<>(TOPIC, PARTITION, 0, -1L, TimestampType.NO_TIMESTAMP_TYPE, -1L, -1, -1, null,
                Buffer.buffer(), new RecordHeaders(new Header[] { ttl, creationTime }));
    }

    private long getCommittedOffset(final TopicPartition topicPartition) {
        final long committedOffset;

        final Map<TopicPartition, OffsetAndMetadata> committed = mockConsumer.committed(Set.of(topicPartition));
        if (committed.isEmpty()) {
            LOG.debug("no offset committed yet for topic-partition [{}]", topicPartition);
            committedOffset = 0;
        } else {
            committedOffset = committed.get(topicPartition).offset();
            LOG.debug("committed offset for topic-partition [{}] is {}", topicPartition, committedOffset);
        }
        return committedOffset;
    }

    private void scheduleBatch(final int startOffset, final int recordsToAdd) {
        mockConsumer.schedulePollTask(() -> {
            for (int j = startOffset; j < startOffset + recordsToAdd; j++) {
                mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, PARTITION, j, null, Buffer.buffer()));
            }
        });
    }

}
