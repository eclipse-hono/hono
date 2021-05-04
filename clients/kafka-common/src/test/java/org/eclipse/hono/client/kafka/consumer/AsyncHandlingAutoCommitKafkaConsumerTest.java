/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.client.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * Verifies the behavior of {@link AsyncHandlingAutoCommitKafkaConsumer}.
 */
@Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
@ExtendWith(VertxExtension.class)
public class AsyncHandlingAutoCommitKafkaConsumerTest {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncHandlingAutoCommitKafkaConsumerTest.class);

    private static final String TOPIC = "test.topic";
    private static final String TOPIC2 = "test.topic2";
    private static final Pattern TOPIC_PATTERN = Pattern.compile(Pattern.quote("test.") + ".*");
    private static final int PARTITION = 0;
    private static final TopicPartition topicPartition = new TopicPartition(TOPIC, PARTITION);
    private static final TopicPartition topic2Partition = new TopicPartition(TOPIC2, PARTITION);
    private static final Node LEADER = new Node(1, "broker1", 9092);

    private KafkaConsumerConfigProperties consumerConfigProperties;
    private Vertx vertx;
    private Context context;
    private HonoKafkaConsumer consumer;
    private KafkaMockConsumer mockConsumer;

    /**
     * Sets up fixture.
     *
     * @param vertx The vert.x instance.
     */
    @BeforeEach
    public void setUp(final Vertx vertx) {
        this.vertx = vertx;
        context = vertx.getOrCreateContext();

        mockConsumer = new KafkaMockConsumer(OffsetResetStrategy.LATEST);

        final Map<String, String> commonProperties = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "servers");
        consumerConfigProperties = new KafkaConsumerConfigProperties();
        consumerConfigProperties.setCommonClientConfig(commonProperties);
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

        assertThatThrownBy(() -> {
            new AsyncHandlingAutoCommitKafkaConsumer(vertx, Set.of("test"), handler, consumerConfig);
        }).isInstanceOf(IllegalArgumentException.class);
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

        consumer = new AsyncHandlingAutoCommitKafkaConsumer(vertx, Set.of(TOPIC), handler, consumerConfig) {
            @Override
            KafkaConsumer<String, Buffer> createKafkaConsumer() {
                return KafkaConsumer.create(vertx, mockConsumer);
            }
        };
        mockConsumer.updateEndOffsets(Map.of(topicPartition, ((long) 0)));
        mockConsumer.updatePartitions(TOPIC, List.of(getPartitionInfo(TOPIC, PARTITION)));
        mockConsumer.setRebalancePartitionAssignmentOnSubscribe(List.of(topicPartition));
        context.runOnContext(v -> {
            consumer.start().onComplete(ctx.completing());
        });
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

        consumer = new AsyncHandlingAutoCommitKafkaConsumer(vertx, TOPIC_PATTERN, handler, consumerConfig) {
            @Override
            KafkaConsumer<String, Buffer> createKafkaConsumer() {
                return KafkaConsumer.create(vertx, mockConsumer);
            }
        };
        mockConsumer.updateEndOffsets(Map.of(topicPartition, ((long) 0)));
        mockConsumer.updatePartitions(TOPIC, List.of(getPartitionInfo(TOPIC, PARTITION)));
        mockConsumer.setRebalancePartitionAssignmentOnSubscribe(List.of(topicPartition));
        context.runOnContext(v -> {
            consumer.start().onComplete(ctx.completing());
        });
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

        consumer = new AsyncHandlingAutoCommitKafkaConsumer(vertx, Set.of(TOPIC), handler, consumerConfig) {
            @Override
            KafkaConsumer<String, Buffer> createKafkaConsumer() {
                return KafkaConsumer.create(vertx, mockConsumer);
            }
        };
        mockConsumer.updateEndOffsets(Map.of(topicPartition, ((long) 0)));
        mockConsumer.updatePartitions(TOPIC, List.of(getPartitionInfo(TOPIC, PARTITION)));
        mockConsumer.setRebalancePartitionAssignmentOnSubscribe(List.of(topicPartition));
        context.runOnContext(v -> {
            consumer.start().onComplete(ctx.succeeding(v2 -> {
                mockConsumer.schedulePollTask(() -> {
                    IntStream.range(0, numTestRecords).forEach(offset -> {
                        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, PARTITION, offset, "key_" + offset, Buffer.buffer()));
                    });
                });
            }));
        });
        assertThat(receivedRecordsCtx.awaitCompletion(5, TimeUnit.SECONDS))
                .as("records received in 5s").isTrue();
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
            assertThat(commitCheckContexts.get(checkIndex.get()).awaitCompletion(5, TimeUnit.SECONDS))
                    .as("partition assigned in 5s for checking of commits").isTrue();
            if (commitCheckContexts.get(checkIndex.get()).failed()) {
                ctx.failNow(commitCheckContexts.get(checkIndex.get()).causeOfFailure());
                return false;
            }
            return true;
        };

        consumer.setOnPartitionsAssignedHandler(partitions -> {
            final Map<TopicPartition, OffsetAndMetadata> committed = mockConsumer.committed(Set.of(topicPartition));
            ctx.verify(() -> {
                final OffsetAndMetadata offsetAndMetadata = committed.get(topicPartition);
                assertThat(offsetAndMetadata).isNotNull();
                assertThat(offsetAndMetadata.offset()).isEqualTo(latestFullyHandledOffset.get() + 1L);
            });
            commitCheckpoints.get(checkIndex.get()).flag();
        });
        // now force a rebalance which should trigger the above onPartitionsAssignedHandler
        mockConsumer.rebalance(List.of(topicPartition));
        if (!waitForCurrentCommitCheckResult.get()) {
            return;
        }
        checkIndex.incrementAndGet();

        // now another rebalance (ie. commit trigger) - no change in offsets
        mockConsumer.rebalance(List.of(topicPartition));
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
        mockConsumer.rebalance(List.of(topicPartition));
        if (waitForCurrentCommitCheckResult.get()) {
            ctx.completeNow();
        }
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

        consumer = new AsyncHandlingAutoCommitKafkaConsumer(vertx, Set.of(TOPIC), handler, consumerConfig) {
            @Override
            KafkaConsumer<String, Buffer> createKafkaConsumer() {
                return KafkaConsumer.create(vertx, mockConsumer);
            }
        };
        mockConsumer.updateEndOffsets(Map.of(topicPartition, ((long) 0)));
        mockConsumer.updatePartitions(TOPIC, List.of(getPartitionInfo(TOPIC, PARTITION)));
        mockConsumer.setRebalancePartitionAssignmentOnSubscribe(List.of(topicPartition));
        context.runOnContext(v -> {
            consumer.start().onComplete(ctx.succeeding(v2 -> {
                mockConsumer.schedulePollTask(() -> {
                    IntStream.range(0, numTestRecords).forEach(offset -> {
                        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, PARTITION, offset, "key_" + offset, Buffer.buffer()));
                    });
                });
            }));
        });
        assertThat(receivedRecordsCtx.awaitCompletion(5, TimeUnit.SECONDS))
                .as("records received in 5s").isTrue();
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
            final Map<TopicPartition, OffsetAndMetadata> committed = mockConsumer.committed(Set.of(topicPartition));
            ctx.verify(() -> {
                final OffsetAndMetadata offsetAndMetadata = committed.get(topicPartition);
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
        // 100ms commit interval - keep the value not too low,
        // otherwise the frequent commit task on the event loop thread will prevent the test main thread from getting things done
        consumerConfig.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "100");

        consumer = new AsyncHandlingAutoCommitKafkaConsumer(vertx, Set.of(TOPIC), handler, consumerConfig) {
            @Override
            KafkaConsumer<String, Buffer> createKafkaConsumer() {
                return KafkaConsumer.create(vertx, mockConsumer);
            }
        };
        mockConsumer.updateEndOffsets(Map.of(topicPartition, ((long) 0)));
        mockConsumer.updatePartitions(TOPIC, List.of(getPartitionInfo(TOPIC, PARTITION)));
        mockConsumer.setRebalancePartitionAssignmentOnSubscribe(List.of(topicPartition));

        context.runOnContext(v -> {
            consumer.start().onComplete(ctx.succeeding(v2 -> {
                mockConsumer.schedulePollTask(() -> {
                    mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, PARTITION, 0, "key_0", Buffer.buffer()));
                });
            }));
        });
        testRecordsReceived.future().onComplete(v -> {
            // we have no hook to integrate into for the commit check
            // therefore do the check multiple times with some delay in between
            final AtomicInteger checkCount = new AtomicInteger(0);
            vertx.setPeriodic(30, tid -> {
                checkCount.incrementAndGet();
                // check offsets
                final Map<TopicPartition, OffsetAndMetadata> committed = mockConsumer.committed(Set.of(topicPartition));
                if (!committed.isEmpty()) {
                    ctx.verify(() -> {
                        final OffsetAndMetadata offsetAndMetadata = committed.get(topicPartition);
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
        consumerConfig.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG,
                "300000"); // periodic commit shall not play a role here

        consumer = new AsyncHandlingAutoCommitKafkaConsumer(vertx, Set.of(TOPIC), handler, consumerConfig) {
            @Override
            KafkaConsumer<String, Buffer> createKafkaConsumer() {
                return KafkaConsumer.create(vertx, mockConsumer);
            }
        };
        mockConsumer.updateEndOffsets(Map.of(topicPartition, ((long) 0)));
        mockConsumer.updatePartitions(TOPIC, List.of(getPartitionInfo(TOPIC, PARTITION)));
        mockConsumer.setRebalancePartitionAssignmentOnSubscribe(List.of(topicPartition));
        context.runOnContext(v -> {
            consumer.start().onComplete(ctx.succeeding(v2 -> {
                mockConsumer.schedulePollTask(() -> {
                    IntStream.range(0, numTestRecords).forEach(offset -> {
                        mockConsumer.addRecord(
                                new ConsumerRecord<>(TOPIC, PARTITION, offset, "key_" + offset, Buffer.buffer()));
                    });
                });
            }));
        });
        assertThat(receivedRecordsCtx.awaitCompletion(5, TimeUnit.SECONDS))
                .as("records received in 5s").isTrue();
        if (receivedRecordsCtx.failed()) {
            ctx.failNow(receivedRecordsCtx.causeOfFailure());
            return;
        }
        // records received, but their handling isn't completed yet
        // do a rebalance with the currently assigned partition not being assigned anymore after it
        mockConsumer.updateEndOffsets(Map.of(topic2Partition, ((long) 0)));
        mockConsumer.rebalance(List.of(topic2Partition));
        // mark the handling of some records as completed
        recordsHandlingPromiseMap.get(0L).complete();
        recordsHandlingPromiseMap.get(1L).complete();
        recordsHandlingPromiseMap.get(2L).complete();

        final Checkpoint commitCheckDone = ctx.checkpoint(1);
        consumer.setOnPartitionsAssignedHandler(partitions -> {
            final Map<TopicPartition, OffsetAndMetadata> committed = mockConsumer.committed(Set.of(topicPartition));
            ctx.verify(() -> {
                // the rebalance after having completed the records of the now unassigned partition
                // should have triggered no offset commits
                assertThat(committed).isEmpty();
            });
            commitCheckDone.flag();
        });
        // now force a rebalance which should trigger the above onPartitionsAssignedHandler
        mockConsumer.rebalance(List.of(topicPartition));
    }

    @NotNull
    private PartitionInfo getPartitionInfo(final String topic, final int partition) {
        final Node[] replicas = new Node[]{LEADER};
        return new PartitionInfo(topic, partition, LEADER, replicas, replicas);
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
