/*******************************************************************************
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.client.kafka.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.eclipse.hono.kafka.test.KafkaMockConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * Verifies the behavior of {@link AsyncHandlingAutoCommitKafkaConsumer}.
 */
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
@ExtendWith(VertxExtension.class)
public class AsyncHandlingAutoCommitKafkaConsumerTest {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncHandlingAutoCommitKafkaConsumerTest.class);

    private static final String TOPIC = "test.topic";
    private static final String TOPIC2 = "test.topic2";
    private static final Pattern TOPIC_PATTERN = Pattern.compile(Pattern.quote("test.") + ".*");
    private static final int PARTITION = 0;
    private static final TopicPartition TOPIC_PARTITION = new TopicPartition(TOPIC, PARTITION);
    private static final TopicPartition TOPIC2_PARTITION = new TopicPartition(TOPIC2, PARTITION);

    private MessagingKafkaConsumerConfigProperties consumerConfigProperties;
    private Vertx vertx;
    private KafkaMockConsumer<String, Buffer> mockConsumer;
    private AsyncHandlingAutoCommitKafkaConsumer<Buffer> consumer;

    /**
     * Sets up fixture.
     *
     * @param vertx The vert.x instance.
     * @param testInfo Test case meta data.
     */
    @BeforeEach
    public void setUp(final Vertx vertx, final TestInfo testInfo) {
        LOG.info("running {}", testInfo.getDisplayName());
        this.vertx = vertx;

        mockConsumer = new KafkaMockConsumer<>(OffsetResetStrategy.LATEST);

        consumerConfigProperties = new MessagingKafkaConsumerConfigProperties();
        consumerConfigProperties.setConsumerConfig(Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "servers"));
    }

    /**
     * Stops the created consumer.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void stopConsumer(final VertxTestContext ctx) {
        if (consumer != null) {
            consumer.stop()
                .onComplete(ar -> ctx.completeNow());
        } else {
            ctx.completeNow();
        }
    }

    /**
     * Verifies that trying to create a consumer without a group.id in the config fails.
     */
    @Test
    public void testConsumerCreationFailsForMissingGroupId() {
        final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> handler = record -> {
            LOG.debug("{}", record);
            return Future.succeededFuture();
        };
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");

        assertThrows(IllegalArgumentException.class,
                () -> new AsyncHandlingAutoCommitKafkaConsumer<>(vertx, Set.of("test"), handler, consumerConfig));
    }

    /**
     * Verifies that trying to create a consumer with a topic list succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConsumerCreationWithTopicListSucceeds(final VertxTestContext ctx) {
        final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> handler = record -> Future.succeededFuture();
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updateEndOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updatePartitions(TOPIC_PARTITION, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(TOPIC_PARTITION));
        consumer = new AsyncHandlingAutoCommitKafkaConsumer<>(vertx, Set.of(TOPIC), handler, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        consumer.start()
            .compose(ok -> readyTracker.future())
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that trying to create a consumer with a topic pattern succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConsumerCreationWithTopicPatternSucceeds(final VertxTestContext ctx) {
        final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> handler = record -> Future.succeededFuture();
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updateEndOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updatePartitions(TOPIC_PARTITION, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(TOPIC_PARTITION));
        consumer = new AsyncHandlingAutoCommitKafkaConsumer<>(vertx, TOPIC_PATTERN, handler, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        consumer.start()
            .compose(ok -> readyTracker.future())
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the maximum number of records in processing by the consumer does not exceed
     * the limit of 1.5 times the <em>max.poll.records</em> config value.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConsumerRespectsMaxRecordsInProcessingLimit(final VertxTestContext ctx) {
        final int maxPollRecords = 10;
        final int throttlingThreshold = maxPollRecords
                * AsyncHandlingAutoCommitKafkaConsumer.THROTTLING_THRESHOLD_PERCENTAGE_OF_MAX_POLL_RECORDS / 100;
        final int maxRecordsInProcessing = maxPollRecords + Math.max(throttlingThreshold, 1);

        final int numTestBatches = 5;
        final int numRecordsPerBatch = maxPollRecords;
        final int numRecords = numTestBatches * numRecordsPerBatch;
        final AtomicInteger offsetCounter = new AtomicInteger();
        final Promise<Void> allRecordsReceivedPromise = Promise.promise();
        final List<Promise<Void>> uncompletedRecordHandlingPromises = new ArrayList<>();
        final List<KafkaConsumerRecord<String, Buffer>> receivedRecords = new ArrayList<>();
        final AtomicInteger observedMaxRecordsInProcessing = new AtomicInteger();
        final AtomicInteger testBatchesToAdd = new AtomicInteger(numTestBatches);

        // let the consumer record handler only complete the record processing when consumer record fetching is already paused (or if all records have been received)
        final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> recordHandler = record -> {
            receivedRecords.add(record);

            final Promise<Void> recordHandlingCompleted = Promise.promise();
            uncompletedRecordHandlingPromises.add(recordHandlingCompleted);
            if (consumer.isRecordFetchingPaused() || receivedRecords.size() == numRecords) {
                if (uncompletedRecordHandlingPromises.size() > observedMaxRecordsInProcessing.get()) {
                    observedMaxRecordsInProcessing.set(uncompletedRecordHandlingPromises.size());
                }
                if (receivedRecords.size() == numRecords) {
                    LOG.trace("complete all remaining {} record handling promises", uncompletedRecordHandlingPromises.size());
                    uncompletedRecordHandlingPromises.forEach(Promise::tryComplete);
                    uncompletedRecordHandlingPromises.clear();
                } else {
                    // complete record handling promises until consumer record fetching isn't paused anymore
                    completeUntilConsumerRecordFetchingResumed(uncompletedRecordHandlingPromises.iterator());
                }
            }
            if (receivedRecords.size() == numRecords) {
                vertx.runOnContext(v -> allRecordsReceivedPromise.tryComplete());
            }
            return recordHandlingCompleted.future();
        };
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        consumerConfig.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Integer.toString(maxPollRecords));
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updateEndOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updatePartitions(TOPIC_PARTITION, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(TOPIC_PARTITION));
        consumer = new AsyncHandlingAutoCommitKafkaConsumer<>(vertx, Set.of(TOPIC), recordHandler, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        consumer.start()
            .compose(ok -> readyTracker.future())
            .onComplete(ctx.succeeding(v2 -> {
                // schedule the poll tasks
                schedulePollTasksWithConsumerPausedCheck(offsetCounter, numRecordsPerBatch, testBatchesToAdd);
                final long timerId = vertx.setTimer(8000, tid -> {
                    LOG.info("received records:\n{}",
                            receivedRecords.stream().map(Object::toString).collect(Collectors.joining(",\n")));
                    allRecordsReceivedPromise.tryFail(String.format("only received %d out of %d expected messages after 8s",
                            uncompletedRecordHandlingPromises.size(), numRecords));
                });
                allRecordsReceivedPromise.future().onComplete(ctx.succeeding(v -> {
                    vertx.cancelTimer(timerId);
                    ctx.verify(() -> {
                        assertWithMessage("observed max no. of records in processing")
                                .that(observedMaxRecordsInProcessing.get()).isEqualTo(maxRecordsInProcessing);
                    });
                    ctx.completeNow();
                }));
            }));
    }

    /**
     * Schedules poll tasks providing each a batch of records, if consumer record fetching isn't paused at that time.
     * This is needed because the MockConsumer doesn't take the <em>max.poll.records</em> config value into account
     * and records added while a consumer is paused would just be returned on the next poll, leading to the batch size
     * not having the expected value.
     */
    private void schedulePollTasksWithConsumerPausedCheck(final AtomicInteger offsetCounter, final int numRecordsPerBatch,
            final AtomicInteger numBatchesToAdd) {
        mockConsumer.schedulePollTask(() -> {
            // only add records if consumer record fetching isn't paused
            if (mockConsumer.paused().isEmpty()) {
                for (int j = 0; j < numRecordsPerBatch; j++) {
                    final int offset = offsetCounter.getAndIncrement();
                    mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, PARTITION, offset, "key_" + offset, Buffer.buffer()));
                }
                numBatchesToAdd.decrementAndGet();
            }
            if (numBatchesToAdd.get() > 0) {
                schedulePollTasksWithConsumerPausedCheck(offsetCounter, numRecordsPerBatch, numBatchesToAdd);
            }
        });
    }

    private void completeUntilConsumerRecordFetchingResumed(final Iterator<Promise<Void>> iterator) {
        if (consumer.isRecordFetchingPaused() && iterator.hasNext()) {
            vertx.runOnContext(v -> {
                LOG.trace("complete record handling promise");
                iterator.next().tryComplete();
                iterator.remove();
                completeUntilConsumerRecordFetchingResumed(iterator);
            });
        }
    }

    /**
     * Verifies that the consumer commits the last fully handled records on rebalance.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if the test execution gets interrupted.
     */
    @Test
    public void testConsumerCommitsOffsetsOnRebalance(final VertxTestContext ctx) throws InterruptedException {
        final int numTestRecords = 5;
        final VertxTestContext receivedRecordsCtx = new VertxTestContext();
        final Checkpoint receivedRecordsCheckpoint = receivedRecordsCtx.checkpoint(numTestRecords);
        final Map<Long, Promise<Void>> recordsHandlingPromiseMap = new HashMap<>();
        final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> handler = record -> {
            final Promise<Void> promise = Promise.promise();
            if (recordsHandlingPromiseMap.put(record.offset(), promise) != null) {
                receivedRecordsCtx.failNow(new IllegalStateException("received record with duplicate offset"));
            }
            receivedRecordsCheckpoint.flag();
            return promise.future();
        };
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        consumerConfig.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "300000"); // periodic commit shall not play a role here
        consumerConfig.put(AsyncHandlingAutoCommitKafkaConsumer.CONFIG_HONO_OFFSETS_COMMIT_RECORD_COMPLETION_TIMEOUT_MILLIS, "0");
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updateEndOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updatePartitions(TOPIC_PARTITION, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(TOPIC_PARTITION));
        consumer = new AsyncHandlingAutoCommitKafkaConsumer<>(vertx, Set.of(TOPIC), handler, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        consumer.start()
            .compose(ok -> readyTracker.future())
            .onComplete(ctx.succeeding(v2 -> {
                mockConsumer.schedulePollTask(() -> {
                    IntStream.range(0, numTestRecords).forEach(offset -> {
                        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, PARTITION, offset, "key_" + offset, Buffer.buffer()));
                    });
                });
            }));
        assertWithMessage("records received in 5s")
                .that(receivedRecordsCtx.awaitCompletion(5, TimeUnit.SECONDS))
                .isTrue();
        if (receivedRecordsCtx.failed()) {
            ctx.failNow(receivedRecordsCtx.causeOfFailure());
            return;
        }

        // records received, complete the handling of some of them
        recordsHandlingPromiseMap.get(0L).complete();
        recordsHandlingPromiseMap.get(1L).complete();
        // offset 3 not completed yet, hence offset 1 is the latest in the row of fully handled records
        final AtomicInteger latestFullyHandledOffset = new AtomicInteger(1);
        recordsHandlingPromiseMap.get(4L).complete();

        // define VertxTestContexts for 3 checks (3x rebalance/commit)
        final AtomicInteger checkIndex = new AtomicInteger(0);
        final List<VertxTestContext> commitCheckContexts = IntStream.range(0, 3)
                .mapToObj(i -> new VertxTestContext()).collect(Collectors.toList());
        final List<Checkpoint> commitCheckpoints = commitCheckContexts.stream()
                .map(c -> c.checkpoint(1)).collect(Collectors.toList());
        final InterruptableSupplier<Boolean> waitForCurrentCommitCheckResult = () -> {
            assertWithMessage("partition assigned in 5s for checking of commits")
                    .that(commitCheckContexts.get(checkIndex.get()).awaitCompletion(5, TimeUnit.SECONDS))
                    .isTrue();
            if (commitCheckContexts.get(checkIndex.get()).failed()) {
                ctx.failNow(commitCheckContexts.get(checkIndex.get()).causeOfFailure());
                return false;
            }
            return true;
        };

        consumer.setOnPartitionsAssignedHandler(partitions -> {
            final Map<TopicPartition, OffsetAndMetadata> committed = mockConsumer.committed(Set.of(TOPIC_PARTITION));
            ctx.verify(() -> {
                final OffsetAndMetadata offsetAndMetadata = committed.get(TOPIC_PARTITION);
                assertThat(offsetAndMetadata).isNotNull();
                assertThat(offsetAndMetadata.offset()).isEqualTo(latestFullyHandledOffset.get() + 1L);
            });
            commitCheckpoints.get(checkIndex.get()).flag();
        });
        // now force a rebalance which should trigger the above onPartitionsAssignedHandler
        mockConsumer.rebalance(List.of(TOPIC_PARTITION));
        if (!waitForCurrentCommitCheckResult.get()) {
            return;
        }
        checkIndex.incrementAndGet();

        // now another rebalance (ie. commit trigger) - no change in offsets
        mockConsumer.rebalance(List.of(TOPIC_PARTITION));
        if (!waitForCurrentCommitCheckResult.get()) {
            return;
        }
        checkIndex.incrementAndGet();

        // now complete some more promises
        recordsHandlingPromiseMap.get(2L).complete();
        recordsHandlingPromiseMap.get(3L).complete();
        // offset 4 already complete
        latestFullyHandledOffset.set(4);
        // again rebalance/commit
        mockConsumer.rebalance(List.of(TOPIC_PARTITION));
        if (waitForCurrentCommitCheckResult.get()) {
            ctx.completeNow();
        }
    }

    /**
     * Verifies that the consumer commits record offsets on rebalance, having waited some time for record
     * handling to be completed.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if the test execution gets interrupted.
     */
    @Test
    public void testConsumerCommitsOffsetsOnRebalanceAfterWaitingForRecordCompletion(final VertxTestContext ctx)
            throws InterruptedException {
        final int numTestRecords = 5;
        final VertxTestContext receivedRecordsCtx = new VertxTestContext();
        final Checkpoint receivedRecordsCheckpoint = receivedRecordsCtx.checkpoint(numTestRecords);
        final Map<Long, Promise<Void>> recordsHandlingPromiseMap = new HashMap<>();
        final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> handler = record -> {
            final Promise<Void> promise = Promise.promise();
            if (recordsHandlingPromiseMap.put(record.offset(), promise) != null) {
                receivedRecordsCtx.failNow(new IllegalStateException("received record with duplicate offset"));
            }
            receivedRecordsCheckpoint.flag();
            return promise.future();
        };
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        consumerConfig.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "300000"); // periodic commit shall not play a role here
        consumerConfig.put(AsyncHandlingAutoCommitKafkaConsumer.CONFIG_HONO_OFFSETS_COMMIT_RECORD_COMPLETION_TIMEOUT_MILLIS, "21000");
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updateEndOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updatePartitions(TOPIC_PARTITION, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(TOPIC_PARTITION));
        final AtomicReference<Handler<Void>> onNextPartitionsRevokedBlockingHandlerRef = new AtomicReference<>();
        consumer = new AsyncHandlingAutoCommitKafkaConsumer<>(vertx, Set.of(TOPIC), handler, consumerConfig) {
            @Override
            protected void onPartitionsRevokedBlocking(
                    final Set<io.vertx.kafka.client.common.TopicPartition> partitionsSet) {
                Optional.ofNullable(onNextPartitionsRevokedBlockingHandlerRef.get())
                        .ifPresent(handler -> handler.handle(null));
                onNextPartitionsRevokedBlockingHandlerRef.set(null);
                super.onPartitionsRevokedBlocking(partitionsSet);
            }
        };
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        final Context consumerVertxContext = vertx.getOrCreateContext();
        consumerVertxContext.runOnContext(v -> {
            consumer.start()
                .compose(ok -> readyTracker.future())
                .onComplete(ctx.succeeding(v2 -> {
                    mockConsumer.schedulePollTask(() -> {
                        IntStream.range(0, numTestRecords).forEach(offset -> {
                            mockConsumer.addRecord(
                                    new ConsumerRecord<>(TOPIC, PARTITION, offset, "key_" + offset, Buffer.buffer()));
                        });
                    });
                }));
        });
        assertWithMessage("records received in 5s")
                .that(receivedRecordsCtx.awaitCompletion(5, TimeUnit.SECONDS))
                .isTrue();
        if (receivedRecordsCtx.failed()) {
            ctx.failNow(receivedRecordsCtx.causeOfFailure());
            return;
        }
        // records received, complete the handling of all except the first 2 records
        LongStream.range(2, numTestRecords).forEach(offset -> recordsHandlingPromiseMap.get(offset).complete());
        ctx.verify(() -> assertThat(recordsHandlingPromiseMap.get(1L).future().isComplete()).isFalse());

        // partitions revoked handler shall get called after the blocking partitions-revoked handling has waited for the records to be marked as completed
        consumer.setOnPartitionsRevokedHandler(s -> {
            ctx.verify(() -> assertThat(recordsHandlingPromiseMap.get(1L).future().isComplete()).isTrue());
        });
        final Checkpoint commitCheckDone = ctx.checkpoint(1);
        consumer.setOnPartitionsAssignedHandler(partitions -> {
            final Map<TopicPartition, OffsetAndMetadata> committed = mockConsumer.committed(Set.of(TOPIC_PARTITION));
            ctx.verify(() -> {
                final OffsetAndMetadata offsetAndMetadata = committed.get(TOPIC_PARTITION);
                assertThat(offsetAndMetadata).isNotNull();
                assertThat(offsetAndMetadata.offset()).isEqualTo(numTestRecords);
            });
            commitCheckDone.flag();
        });
        // trigger a rebalance where the currently assigned partition is revoked
        // (and then assigned again - otherwise its offset wouldn't be returned by mockConsumer.committed())
        // the remaining 2 records are to be marked as completed with some delay
        onNextPartitionsRevokedBlockingHandlerRef.set(v -> {
            consumerVertxContext.runOnContext(v2 -> {
                recordsHandlingPromiseMap.get(0L).complete();
                recordsHandlingPromiseMap.get(1L).complete();
            });
        });
        mockConsumer.setRevokeAllOnRebalance(true);
        mockConsumer.updateBeginningOffsets(Map.of(TOPIC2_PARTITION, 0L));
        mockConsumer.updateEndOffsets(Map.of(TOPIC2_PARTITION, 0L));
        mockConsumer.setNextPollRebalancePartitionAssignment(List.of(TOPIC_PARTITION, TOPIC2_PARTITION));
    }

    /**
     * Verifies that the consumer commits the last fully handled records when it is stopped.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if the test execution gets interrupted.
     */
    @Test
    public void testConsumerCommitsOffsetsOnStop(final VertxTestContext ctx) throws InterruptedException {
        final int numTestRecords = 5;
        final VertxTestContext receivedRecordsCtx = new VertxTestContext();
        final Checkpoint receivedRecordsCheckpoint = receivedRecordsCtx.checkpoint(numTestRecords);
        final Map<Long, Promise<Void>> recordsHandlingPromiseMap = new HashMap<>();
        final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> handler = record -> {
            final Promise<Void> promise = Promise.promise();
            if (recordsHandlingPromiseMap.put(record.offset(), promise) != null) {
                receivedRecordsCtx.failNow(new IllegalStateException("received record with duplicate offset"));
            }
            receivedRecordsCheckpoint.flag();
            return promise.future();
        };
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        consumerConfig.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "300000"); // periodic commit shall not play a role here
        consumerConfig.put(AsyncHandlingAutoCommitKafkaConsumer.CONFIG_HONO_OFFSETS_COMMIT_RECORD_COMPLETION_TIMEOUT_MILLIS, "0");
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updateEndOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updatePartitions(TOPIC_PARTITION, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(TOPIC_PARTITION));
        consumer = new AsyncHandlingAutoCommitKafkaConsumer<>(vertx, Set.of(TOPIC), handler, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        consumer.start()
            .compose(ok -> readyTracker.future())
            .onComplete(ctx.succeeding(v2 -> {
                mockConsumer.schedulePollTask(() -> {
                    IntStream.range(0, numTestRecords).forEach(offset -> {
                        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, PARTITION, offset, "key_" + offset, Buffer.buffer()));
                    });
                });
            }));
        assertWithMessage("records received in 5s")
                .that(receivedRecordsCtx.awaitCompletion(5, TimeUnit.SECONDS))
                .isTrue();
        if (receivedRecordsCtx.failed()) {
            ctx.failNow(receivedRecordsCtx.causeOfFailure());
            return;
        }

        // records received, complete the handling of some of them
        recordsHandlingPromiseMap.get(0L).complete();
        recordsHandlingPromiseMap.get(1L).complete();
        // offset 3 not completed yet, hence offset 1 is the latest in the row of fully handled records
        final int latestFullyHandledOffset = 1;
        recordsHandlingPromiseMap.get(4L).complete();

        mockConsumer.setSkipSettingClosedFlagOnNextClose(); // otherwise mockConsumer committed() can't be called
        // now close the consumer
        consumer.stop().onComplete(v -> {
            final Map<TopicPartition, OffsetAndMetadata> committed = mockConsumer.committed(Set.of(TOPIC_PARTITION));
            ctx.verify(() -> {
                final OffsetAndMetadata offsetAndMetadata = committed.get(TOPIC_PARTITION);
                assertThat(offsetAndMetadata).isNotNull();
                assertThat(offsetAndMetadata.offset()).isEqualTo(latestFullyHandledOffset + 1L);
            });
            ctx.completeNow();
        });
    }

    /**
     * Verifies that the consumer commits the last fully handled records periodically.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConsumerCommitsOffsetsPeriodically(final VertxTestContext ctx) {
        final Promise<Void> testRecordsReceived = Promise.promise();
        final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> handler = record -> {
            testRecordsReceived.complete();
            return Future.succeededFuture();
        };
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        // 1000ms commit interval - keep the value not too low,
        // otherwise the frequent commit task on the event loop thread will prevent the test main thread from getting things done
        consumerConfig.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updateEndOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updatePartitions(TOPIC_PARTITION, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(TOPIC_PARTITION));

        consumer = new AsyncHandlingAutoCommitKafkaConsumer<>(vertx, Set.of(TOPIC), handler, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        consumer.start()
            .compose(ok -> readyTracker.future())
            .onComplete(ctx.succeeding(v2 -> {
                mockConsumer.schedulePollTask(() -> {
                    mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, PARTITION, 0, "key_0", Buffer.buffer()));
                });
            }));
        testRecordsReceived.future().onComplete(v -> {
            // we have no hook to integrate into for the commit check
            // therefore do the check multiple times with some delay in between
            final AtomicInteger checkCount = new AtomicInteger(0);
            vertx.setPeriodic(200, tid -> {
                checkCount.incrementAndGet();
                // check offsets
                final Map<TopicPartition, OffsetAndMetadata> committed = mockConsumer.committed(Set.of(TOPIC_PARTITION));
                if (!committed.isEmpty()) {
                    ctx.verify(() -> {
                        final OffsetAndMetadata offsetAndMetadata = committed.get(TOPIC_PARTITION);
                        assertThat(offsetAndMetadata).isNotNull();
                        assertThat(offsetAndMetadata.offset()).isEqualTo(1L);
                    });
                    ctx.completeNow();
                    vertx.cancelTimer(tid);
                } else {
                    if (checkCount.get() >= 10) {
                        vertx.cancelTimer(tid);
                        ctx.failNow(new AssertionError("offset should have been committed"));
                    }
                }
            });
        });
    }

    /**
     * Verifies that the consumer commits the initial partition offset on the first offset commit after
     * the partition got assigned to the consumer.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if the test execution gets interrupted.
     */
    @Test
    public void testConsumerCommitsInitialOffset(final VertxTestContext ctx) throws InterruptedException {
        final Promise<Void> testRecordsReceived = Promise.promise();
        final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> handler = record -> {
            testRecordsReceived.complete();
            return Future.succeededFuture();
        };
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        // 1000ms commit interval - keep the value not too low,
        // otherwise the frequent commit task on the event loop thread will prevent the test main thread from getting things done
        consumerConfig.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updateEndOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updatePartitions(TOPIC_PARTITION, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(TOPIC_PARTITION));

        final VertxTestContext consumerStartedCtx = new VertxTestContext();
        final Checkpoint consumerStartedCheckpoint = consumerStartedCtx.checkpoint(2);
        consumer = new AsyncHandlingAutoCommitKafkaConsumer<>(vertx, Set.of(TOPIC), handler, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.setOnRebalanceDoneHandler(s -> consumerStartedCheckpoint.flag());
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        vertx.getOrCreateContext().runOnContext(v -> {
            consumer.start()
                .compose(ok -> readyTracker.future())
                .onSuccess(v2 -> consumerStartedCheckpoint.flag());
            });
        assertWithMessage("consumer started in 5s")
                .that(consumerStartedCtx.awaitCompletion(5, TimeUnit.SECONDS))
                .isTrue();
        if (consumerStartedCtx.failed()) {
            ctx.failNow(consumerStartedCtx.causeOfFailure());
            return;
        }
        final List<Map<TopicPartition, OffsetAndMetadata>> reportedCommits = new ArrayList<>();
        mockConsumer.addCommitListener(reportedCommits::add);

        final CountDownLatch rebalance1Done = new CountDownLatch(1);
        consumer.setOnPartitionsAssignedHandler(partitions -> {
            final Map<TopicPartition, OffsetAndMetadata> committed = mockConsumer.committed(Set.of(TOPIC_PARTITION, TOPIC2_PARTITION));
            ctx.verify(() -> {
                // the rebalance where topicPartition got revoked should have triggered a commit of offset 0 for topicPartition
                assertThat(reportedCommits.size()).isEqualTo(1);
                final OffsetAndMetadata offsetAndMetadata = committed.get(TOPIC_PARTITION);
                assertThat(offsetAndMetadata).isNotNull();
                assertThat(offsetAndMetadata.offset()).isEqualTo(0);
            });
        });
        consumer.setOnRebalanceDoneHandler(s -> rebalance1Done.countDown());
        // now force a rebalance which should trigger the above onPartitionsAssignedHandler
        mockConsumer.updateBeginningOffsets(Map.of(TOPIC2_PARTITION, 0L));
        mockConsumer.updateEndOffsets(Map.of(TOPIC2_PARTITION, 0L));
        mockConsumer.rebalance(List.of(TOPIC2_PARTITION));
        if (!rebalance1Done.await(5, TimeUnit.SECONDS)) {
            ctx.failNow(new IllegalStateException("partitionsAssigned handler not invoked"));
        }

        final CountDownLatch rebalance2Done = new CountDownLatch(1);
        consumer.setOnPartitionsAssignedHandler(partitions -> {
            final Map<TopicPartition, OffsetAndMetadata> committed = mockConsumer.committed(Set.of(TOPIC_PARTITION, TOPIC2_PARTITION));
            ctx.verify(() -> {
                // the 2nd rebalance where topic2Partition got revoked and topicPartition got assigned
                // should have triggered a commit of offset 0 for topic2Partition
                assertThat(reportedCommits.size()).isEqualTo(2);
                final OffsetAndMetadata offsetAndMetadata = committed.get(TOPIC2_PARTITION);
                assertThat(offsetAndMetadata).isNotNull();
                assertThat(offsetAndMetadata.offset()).isEqualTo(0);
            });
        });
        consumer.setOnRebalanceDoneHandler(s -> rebalance2Done.countDown());
        // now again force a rebalance which should trigger the above onPartitionsAssignedHandler
        // - this time again with the first partition
        mockConsumer.updateBeginningOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updateEndOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.rebalance(List.of(TOPIC_PARTITION));
        if (!rebalance2Done.await(5, TimeUnit.SECONDS)) {
            ctx.failNow(new IllegalStateException("partitionsAssigned handler not invoked"));
        }

        consumer.setOnPartitionsAssignedHandler(partitions -> {
            ctx.verify(() -> {
                // the 3rd rebalance where all partitions got revoked should have triggered no new commits
                assertThat(reportedCommits.size()).isEqualTo(2);
            });
            ctx.completeNow();
        });
        // now force a 3rd rebalance, assigning no partition
        mockConsumer.rebalance(List.of());
    }

    /**
     * Verifies that a scenario of a partition being revoked and not assigned again, while there are
     * still not fully handled records, is identified by the consumer.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if the test execution gets interrupted.
     */
    @Test
    public void testScenarioWithPartitionRevokedWhileHandlingIncomplete(final VertxTestContext ctx) throws InterruptedException {
        final int numTestRecords = 5;
        final VertxTestContext receivedRecordsCtx = new VertxTestContext();
        final Checkpoint receivedRecordsCheckpoint = receivedRecordsCtx.checkpoint(numTestRecords);
        final Map<Long, Promise<Void>> recordsHandlingPromiseMap = new HashMap<>();
        final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> handler = record -> {
            final Promise<Void> promise = Promise.promise();
            if (recordsHandlingPromiseMap.put(record.offset(), promise) != null) {
                receivedRecordsCtx.failNow(new IllegalStateException("received record with duplicate offset"));
            }
            receivedRecordsCheckpoint.flag();
            return promise.future();
        };
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        consumerConfig.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "300000"); // periodic commit shall not play a role here
        consumerConfig.put(AsyncHandlingAutoCommitKafkaConsumer.CONFIG_HONO_OFFSETS_COMMIT_RECORD_COMPLETION_TIMEOUT_MILLIS, "0");
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updateEndOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updatePartitions(TOPIC_PARTITION, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(TOPIC_PARTITION));
        consumer = new AsyncHandlingAutoCommitKafkaConsumer<>(vertx, Set.of(TOPIC), handler, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        consumer.start()
            .compose(ok -> readyTracker.future())
            .onComplete(ctx.succeeding(v2 -> {
                mockConsumer.schedulePollTask(() -> {
                    IntStream.range(0, numTestRecords).forEach(offset -> {
                        mockConsumer.addRecord(
                                new ConsumerRecord<>(TOPIC, PARTITION, offset, "key_" + offset, Buffer.buffer()));
                    });
                });
            }));
        assertWithMessage("records received in 5s")
                .that(receivedRecordsCtx.awaitCompletion(5, TimeUnit.SECONDS))
                .isTrue();
        if (receivedRecordsCtx.failed()) {
            ctx.failNow(receivedRecordsCtx.causeOfFailure());
            return;
        }
        // records received, but their handling isn't completed yet
        // do a rebalance with the currently assigned partition not being assigned anymore after it
        mockConsumer.updateBeginningOffsets(Map.of(TOPIC2_PARTITION, 0L));
        mockConsumer.updateEndOffsets(Map.of(TOPIC2_PARTITION, 0L));
        final CountDownLatch rebalanceWithTopic2Done = new CountDownLatch(1);
        consumer.setOnPartitionsAssignedHandler(partitions -> {
            rebalanceWithTopic2Done.countDown();
        });
        mockConsumer.rebalance(List.of(TOPIC2_PARTITION));
        rebalanceWithTopic2Done.await();

        // mark the handling of some records as completed
        recordsHandlingPromiseMap.get(0L).complete();
        recordsHandlingPromiseMap.get(1L).complete();
        recordsHandlingPromiseMap.get(2L).complete();

        final Checkpoint commitCheckDone = ctx.checkpoint(1);
        consumer.setOnPartitionsAssignedHandler(partitions -> {
            LOG.info("rebalancing ...");
            final Map<TopicPartition, OffsetAndMetadata> committed = mockConsumer.committed(Set.of(TOPIC_PARTITION));
            ctx.verify(() -> {
                // the last rebalance where topicPartition got revoked should have just
                // triggered a commit of offset 0; the 3 records that only got completed
                // after the rebalance shouldn't have been taken into account in the commit
                final OffsetAndMetadata offsetAndMetadata = committed.get(TOPIC_PARTITION);
                assertThat(offsetAndMetadata).isNotNull();
                assertThat(offsetAndMetadata.offset()).isEqualTo(0);
            });
            commitCheckDone.flag();
        });
        // now force a rebalance which should trigger the above onPartitionsAssignedHandler
        mockConsumer.rebalance(List.of(TOPIC_PARTITION));
    }

    /**
     * Verifies that the consumer commits offsets for records whose ttl has expired.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if the test execution gets interrupted.
     */
    @Test
    public void testConsumerCommitsOffsetsOfSkippedExpiredRecords(final VertxTestContext ctx) throws InterruptedException {
        final int numNonExpiredTestRecords = 5;
        final VertxTestContext receivedRecordsCtx = new VertxTestContext();
        final Checkpoint expiredRecordCheckpoint = receivedRecordsCtx.checkpoint(1);
        final Checkpoint receivedRecordsCheckpoint = receivedRecordsCtx.checkpoint(numNonExpiredTestRecords);
        final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> handler = record -> {
            receivedRecordsCheckpoint.flag();
            return Future.succeededFuture();
        };
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        consumerConfig.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "300000"); // periodic commit shall not play a role here
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updateEndOffsets(Map.of(TOPIC_PARTITION, 0L));
        mockConsumer.updatePartitions(TOPIC_PARTITION, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(TOPIC_PARTITION));
        consumer = new AsyncHandlingAutoCommitKafkaConsumer<>(vertx, Set.of(TOPIC), handler, consumerConfig) {
            @Override
            protected void onRecordHandlerSkippedForExpiredRecord(final KafkaConsumerRecord<String, Buffer> record) {
                super.onRecordHandlerSkippedForExpiredRecord(record);
                expiredRecordCheckpoint.flag();
            }
        };
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        final Context consumerVertxContext = vertx.getOrCreateContext();
        consumerVertxContext.runOnContext(v -> {
            consumer.start()
                .compose(ok -> readyTracker.future())
                .onComplete(ctx.succeeding(v2 -> {
                    mockConsumer.schedulePollTask(() -> {
                        // add record with elapsed ttl
                        mockConsumer.addRecord(createRecordWithElapsedTtl());
                        IntStream.range(1, numNonExpiredTestRecords + 1).forEach(offset -> {
                            mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, PARTITION, offset, "key_" + offset, Buffer.buffer()));
                        });
                    });
                }));
        });
        assertWithMessage("records received in 5s")
                .that(receivedRecordsCtx.awaitCompletion(5, TimeUnit.SECONDS))
                .isTrue();
        if (receivedRecordsCtx.failed()) {
            ctx.failNow(receivedRecordsCtx.causeOfFailure());
            return;
        }
        final int numExpiredTestRecords = 1;
        final int latestFullyHandledOffset = numNonExpiredTestRecords + numExpiredTestRecords - 1;
        final VertxTestContext commitCheckContext = new VertxTestContext();
        final Checkpoint commitCheckpoint = commitCheckContext.checkpoint(1);

        consumer.setOnPartitionsAssignedHandler(partitions -> {
            final Map<TopicPartition, OffsetAndMetadata> committed = mockConsumer.committed(Set.of(TOPIC_PARTITION));
            ctx.verify(() -> {
                final OffsetAndMetadata offsetAndMetadata = committed.get(TOPIC_PARTITION);
                assertThat(offsetAndMetadata).isNotNull();
                assertThat(offsetAndMetadata.offset()).isEqualTo(latestFullyHandledOffset + 1L);
            });
            commitCheckpoint.flag();
        });
        // now force a rebalance which should trigger the above onPartitionsAssignedHandler
        // (rebalance is done as part of the poll() invocation; the vert.x consumer will schedule that invocation
        // via an action executed on the event loop thread; do this here as well, meaning the record handler
        // run on the event loop thread will be finished once the rebalance get triggered).
        final CountDownLatch latch = new CountDownLatch(1);
        consumerVertxContext.runOnContext(v -> latch.countDown());
        latch.await();
        mockConsumer.rebalance(List.of(TOPIC_PARTITION));
        assertWithMessage("partition assigned in 5s for checking of commits")
                .that(commitCheckContext.awaitCompletion(5, TimeUnit.SECONDS))
                .isTrue();
        if (commitCheckContext.failed()) {
            ctx.failNow(commitCheckContext.causeOfFailure());
            return;
        }
        ctx.completeNow();
    }

    private ConsumerRecord<String, Buffer> createRecordWithElapsedTtl() {
        final byte[] ttl1Second = "1".getBytes();
        final RecordHeader ttl = new RecordHeader("ttl", ttl1Second);

        final byte[] timestamp2SecondsAgo = Json.encode(Instant.now().minusSeconds(2).toEpochMilli()).getBytes();
        final RecordHeader creationTime = new RecordHeader("creation-time", timestamp2SecondsAgo);

        return new ConsumerRecord<>(
                TOPIC,
                PARTITION,
                0,
                ConsumerRecord.NO_TIMESTAMP,
                TimestampType.NO_TIMESTAMP_TYPE,
                ConsumerRecord.NULL_SIZE,
                ConsumerRecord.NULL_SIZE,
                "key_0",
                Buffer.buffer(),
                new RecordHeaders(new Header[] { ttl, creationTime }),
                Optional.empty());
    }

    /**
     * Supplier whose get() method might throw an {@link InterruptedException}.
     * @param <T> The type of results supplied by this supplier.
     */
    @FunctionalInterface
    interface InterruptableSupplier<T> {
        /**
         * Gets a result.
         * @return The result.
         * @throws InterruptedException If getting the result was interrupted.
         */
        T get() throws InterruptedException;
    }
}
