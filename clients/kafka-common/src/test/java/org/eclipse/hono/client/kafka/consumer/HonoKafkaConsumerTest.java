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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.eclipse.hono.client.kafka.KafkaClientFactory;
import org.eclipse.hono.kafka.test.KafkaMockConsumer;
import org.eclipse.hono.test.JUnitTests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Verifies the behavior of {@link HonoKafkaConsumerTest}.
 */
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
@ExtendWith(VertxExtension.class)
public class HonoKafkaConsumerTest {

    private static final Logger LOG = LoggerFactory.getLogger(HonoKafkaConsumerTest.class);

    private static final String TOPIC = "test.topic";
    private static final String TOPIC2 = "test.topic2";
    private static final String TOPIC3 = "test.topic3";
    private static final Pattern TOPIC_PATTERN = Pattern.compile("test\\..*");
    private static final int PARTITION = 0;
    private static final TopicPartition topicPartition = new TopicPartition(TOPIC, PARTITION);
    private static final TopicPartition topic2Partition = new TopicPartition(TOPIC2, PARTITION);
    private static final TopicPartition topic3Partition = new TopicPartition(TOPIC3, PARTITION);

    private MessagingKafkaConsumerConfigProperties consumerConfigProperties;
    private Vertx vertx;
    private HonoKafkaConsumer<Buffer> consumer;
    private KafkaMockConsumer<String, Buffer> mockConsumer;

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
        consumerConfigProperties.setConsumerConfig(Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "server:9092"));
    }

    /**
     * Stops the created consumer.
     *
     * @param vertx The vert.x instance.
     * @param context The vert.x test context.
     */
    @AfterEach
    public void stopConsumer(final Vertx vertx, final VertxTestContext context) {
        if (consumer != null) {
            vertx.runOnContext(v -> consumer.stop().onComplete(r -> context.completeNow()));
        } else {
            context.completeNow();
        }
    }

    /**
     * Verifies that trying to create a consumer without a group.id in the config but with auto-commit explicitly
     * enabled fails.
     */
    @Test
    public void testConsumerCreationFailsForMissingGroupId() {
        final var consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        assertThrows(IllegalArgumentException.class,
                () -> new HonoKafkaConsumer<>(vertx, Set.of("test"), r -> {}, consumerConfig));
    }

    @Test
    void testStartSucceedsEvenIfConsumerIsNotReadyYet(final VertxTestContext ctx) {
        final var consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(topicPartition, 0L));
        mockConsumer.updateEndOffsets(Map.of(topicPartition, 0L));
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition));

        final var error = new KafkaException(new ConfigException(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG));
        @SuppressWarnings("unchecked")
        final Supplier<Consumer<String, Buffer>> consumerSupplier = mock(Supplier.class);
        when(consumerSupplier.get())
            // first attempt to create Kafka client fails because broker names cannot be resolved
            .thenThrow(error)
            // second attempt succeeds
            .thenReturn(mockConsumer);

        consumer = new HonoKafkaConsumer<>(vertx, Set.of(TOPIC), r -> {}, consumerConfig);
        consumer.setKafkaConsumerSupplier(consumerSupplier);
        consumer.setConsumerCreationRetriesTimeout(KafkaClientFactory.UNLIMITED_RETRIES_DURATION);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        consumer.start()
            .onFailure(ctx::failNow)
            .compose(ok -> {
                ctx.verify(() -> assertThat(readyTracker.future().isComplete()).isFalse());
                return readyTracker.future();
            })
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    assertThat(consumer.getKafkaConsumer()).isNotNull();
                    verify(consumerSupplier, times(2)).get();
                });
                ctx.completeNow();
            }));
    }

    @Test
    void testStartFailsIfRecordHandlerIsNotSet() {
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());

        mockConsumer.updateBeginningOffsets(Map.of(topicPartition, 0L));
        mockConsumer.updateEndOffsets(Map.of(topicPartition, 0L));
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition));

        consumer = new HonoKafkaConsumer<>(vertx, Set.of(TOPIC), (Pattern) null, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        assertThrows(IllegalStateException.class, () -> consumer.start());
    }

    private void testMethodInvocationFailsIfConsumerIsStarted(
            final Class<? extends Exception> expectedExceptionType,
            final Executable methodInvocation) {

        final VertxTestContext ctx = new VertxTestContext();
        final Handler<KafkaConsumerRecord<String, Buffer>> handler = record -> {};
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());

        mockConsumer.updateBeginningOffsets(Map.of(topicPartition, 0L));
        mockConsumer.updateEndOffsets(Map.of(topicPartition, 0L));
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition));
        consumer = new HonoKafkaConsumer<>(vertx, Set.of(TOPIC), handler, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.start()
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> assertThrows(expectedExceptionType, methodInvocation));
                ctx.completeNow();
            }));
    }

    @Test
    void testAddTopicFailsIfConsumerIsStarted() {
        testMethodInvocationFailsIfConsumerIsStarted(IllegalStateException.class,
                () -> consumer.addTopic("SHOULD_FAIL"));
    }

    @Test
    void testSetRecordHandlerFailsIfConsumerIsStarted() {
        testMethodInvocationFailsIfConsumerIsStarted(IllegalStateException.class,
                () -> consumer.setRecordHandler(record -> {}));
    }

    /**
     * Verifies that trying to create a HonoKafkaConsumer with a topic list succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConsumerCreationWithTopicListSucceeds(final VertxTestContext ctx) {
        final var consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(topicPartition, 0L));
        mockConsumer.updateEndOffsets(Map.of(topicPartition, 0L));
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition));

        consumer = new HonoKafkaConsumer<>(vertx, Set.of(TOPIC), r -> {}, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        consumer.start()
            .compose(ok -> readyTracker.future())
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    assertThat(consumer.getKafkaConsumer()).isNotNull();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that trying to create a HonoKafkaConsumer with a topic pattern succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConsumerCreationWithTopicPatternSucceeds(final VertxTestContext ctx) {
        final var consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(topicPartition, 0L, topic2Partition, 0L));
        mockConsumer.updateEndOffsets(Map.of(topicPartition, 0L, topic2Partition, 0L));
        mockConsumer.updatePartitions(topicPartition, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.updatePartitions(topic2Partition, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition, topic2Partition));

        consumer = new HonoKafkaConsumer<>(vertx, TOPIC_PATTERN, r -> {}, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        consumer.start()
            .compose(ok -> readyTracker.future())
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    assertThat(consumer.getKafkaConsumer()).isNotNull();
                    assertThat(consumer.getSubscribedTopicPatternTopics()).containsExactly(TOPIC, TOPIC2);
                    assertThat(consumer.isAmongKnownSubscribedTopics(TOPIC)).isTrue();
                    assertThat(consumer.isAmongKnownSubscribedTopics(TOPIC2)).isTrue();
                    assertThat(consumer.ensureTopicIsAmongSubscribedTopicPatternTopics(TOPIC).succeeded()).isTrue();
                    assertThat(consumer.ensureTopicIsAmongSubscribedTopicPatternTopics(TOPIC2).succeeded()).isTrue();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that invoking <em>ensureTopicIsAmongSubscribedTopicPatternTopics</em> succeeds for
     * a topic that matches the topic pattern but has been created after the consumer started.
     *
     * @param metadataMaxAgeMs The {@value ConsumerConfig#METADATA_MAX_AGE_CONFIG} value to set on the consumer.
     * @param requiresExplicitRebalancing {@code true} if the max age value should result in the consumer enforcing a rebalance.
     * @param ctx The vert.x test context.
     */
    @ParameterizedTest(name = JUnitTests.PARAMETERIZED_TEST_NAME_PATTERN)
    @CsvSource(value = { ",true", "700,true", "300,false" })
    public void testEnsureTopicIsAmongSubscribedTopicsSucceedsForAddedTopic(
            final String metadataMaxAgeMs,
            final boolean requiresExplicitRebalancing,
            final VertxTestContext ctx) {
        final var consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        Optional.ofNullable(metadataMaxAgeMs)
            .ifPresent(s -> consumerConfig.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, s));
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(topicPartition, 0L, topic2Partition, 0L));
        mockConsumer.updateEndOffsets(Map.of(topicPartition, 0L, topic2Partition, 0L));
        mockConsumer.updatePartitions(topicPartition, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.updatePartitions(topic2Partition, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition, topic2Partition));

        consumer = new HonoKafkaConsumer<>(vertx, TOPIC_PATTERN, r -> {}, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        consumer.start()
            .compose(ok -> readyTracker.future())
            .compose(ok -> {
                ctx.verify(() -> {
                    assertThat(consumer.getKafkaConsumer()).isNotNull();
                    assertThat(consumer.getSubscribedTopicPatternTopics()).containsExactly(TOPIC, TOPIC2);
                });
                // now update partitions with the one for topic3
                mockConsumer.updatePartitions(topic3Partition, KafkaMockConsumer.DEFAULT_NODE);
                mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition, topic2Partition, topic3Partition));
                mockConsumer.updateBeginningOffsets(Map.of(topic3Partition, 0L));
                mockConsumer.updateEndOffsets(Map.of(topic3Partition, 0L));
                return consumer.ensureTopicIsAmongSubscribedTopicPatternTopics(TOPIC3);
            })
            .onComplete(ctx.succeeding(ok -> {
                    ctx.verify(() -> {
                        assertThat(mockConsumer.shouldRebalance()).isEqualTo(requiresExplicitRebalancing);
                        assertThat(consumer.getSubscribedTopicPatternTopics()).containsExactly(TOPIC, TOPIC2, TOPIC3);
                    });
                    ctx.completeNow();
            }));
    }

    /**
     * Verifies that the HonoKafkaConsumer invokes the provided handler on received records.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConsumerInvokesHandlerOnReceivedRecords(final VertxTestContext ctx) {
        final int numTestRecords = 5;
        final Checkpoint receivedRecordsCheckpoint = ctx.checkpoint(numTestRecords);
        final Handler<KafkaConsumerRecord<String, Buffer>> handler = record -> {
            receivedRecordsCheckpoint.flag();
        };
        final var consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(topicPartition, 0L));
        mockConsumer.updateEndOffsets(Map.of(topicPartition, 0L));
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition));
        consumer = new HonoKafkaConsumer<>(vertx, Set.of(TOPIC), handler, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        consumer.start()
            .compose(ok -> readyTracker.future())
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> assertThat(consumer.getKafkaConsumer()).isNotNull());
                mockConsumer.schedulePollTask(() -> {
                    IntStream.range(0, numTestRecords).forEach(offset -> {
                        mockConsumer.addRecord(new ConsumerRecord<>(
                                TOPIC,
                                PARTITION,
                                offset,
                                "key_" + offset,
                                Buffer.buffer("payload " + offset)));
                    });
                });
            }));
    }

    /**
     * Verifies that the HonoKafkaConsumer doesn't invoke the provided handler on received records whose ttl has expired.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConsumerSkipsHandlerInvocationOnReceivingExpiredRecords(final VertxTestContext ctx) {
        final int numNonExpiredTestRecords = 5;
        final Checkpoint receivedRecordsCheckpoint = ctx.checkpoint(numNonExpiredTestRecords);
        final Handler<KafkaConsumerRecord<String, Buffer>> handler = record -> {
            receivedRecordsCheckpoint.flag();
        };
        final Checkpoint expiredRecordCheckpoint = ctx.checkpoint(1);
        final var consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        final Promise<Void> readyTracker = Promise.promise();

        mockConsumer.updateBeginningOffsets(Map.of(topicPartition, 0L));
        mockConsumer.updateEndOffsets(Map.of(topicPartition, 0L));
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition));
        consumer = new HonoKafkaConsumer<>(vertx, Set.of(TOPIC), handler, consumerConfig) {
            @Override
            protected void onRecordHandlerSkippedForExpiredRecord(final KafkaConsumerRecord<String, Buffer> record) {
                expiredRecordCheckpoint.flag();
            }
        };
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        consumer.start()
            .compose(ok -> readyTracker.future())
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> assertThat(consumer.getKafkaConsumer()).isNotNull());
                mockConsumer.schedulePollTask(() -> {
                    // add record with elapsed ttl
                    mockConsumer.addRecord(createRecordWithElapsedTtl());
                    IntStream.range(1, numNonExpiredTestRecords + 1).forEach(offset -> {
                        mockConsumer.addRecord(new ConsumerRecord<>(
                                TOPIC,
                                PARTITION,
                                offset,
                                "key_" + offset,
                                Buffer.buffer("payload " + offset)));
                    });
                });
            }));
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
}
