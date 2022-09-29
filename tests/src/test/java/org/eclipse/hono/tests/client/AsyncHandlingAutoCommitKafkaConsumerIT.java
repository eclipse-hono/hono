/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.tests.client;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.eclipse.hono.client.kafka.consumer.AsyncHandlingAutoCommitKafkaConsumer;
import org.eclipse.hono.tests.EnabledIfMessagingSystemConfigured;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.MessagingType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;

/**
 * Test cases verifying the behavior of {@link AsyncHandlingAutoCommitKafkaConsumer}.
 * <p>
 * To run this on a specific Kafka cluster instance, set the
 * {@value IntegrationTestSupport#PROPERTY_DOWNSTREAM_BOOTSTRAP_SERVERS} system property,
 * e.g. <code>-Ddownstream.bootstrap.servers="PLAINTEXT://localhost:9092"</code>.
 */
@ExtendWith(VertxExtension.class)
@EnabledIfMessagingSystemConfigured(type = MessagingType.kafka)
public class AsyncHandlingAutoCommitKafkaConsumerIT {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncHandlingAutoCommitKafkaConsumerIT.class);

    private static final short REPLICATION_FACTOR = 1;
    private static final String SMALL_TOPIC_SEGMENT_SIZE_BYTES = "120";

    private static Vertx vertx;
    private static KafkaAdminClient adminClient;
    private static KafkaProducer<String, Buffer> kafkaProducer;
    private static List<String> topicsToDeleteAfterTests;

    private AsyncHandlingAutoCommitKafkaConsumer<Buffer> kafkaConsumer;

    private static Stream<String> partitionAssignmentStrategies() {
        return Stream.of(null, CooperativeStickyAssignor.class.getName());
    }

    /**
     * Sets up fixture.
     */
    @BeforeAll
    public static void init() {
        vertx = Vertx.vertx();
        topicsToDeleteAfterTests = new ArrayList<>();

        final Map<String, String> adminClientConfig = IntegrationTestSupport.getKafkaAdminClientConfig()
                .getAdminClientConfig("test");
        adminClient = KafkaAdminClient.create(vertx, adminClientConfig);
        final Map<String, String> producerConfig = IntegrationTestSupport.getKafkaProducerConfig()
                .getProducerConfig("test");
        producerConfig.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, SMALL_TOPIC_SEGMENT_SIZE_BYTES);
        producerConfig.put(ProducerConfig.BATCH_SIZE_CONFIG, SMALL_TOPIC_SEGMENT_SIZE_BYTES);
        kafkaProducer = KafkaProducer.create(vertx, producerConfig);
    }

    /**
     * Cleans up fixture.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutDown(final VertxTestContext ctx) {
        final Promise<Void> producerClosePromise = Promise.promise();
        kafkaProducer.close(producerClosePromise);

        final Promise<Void> topicsDeletedPromise = Promise.promise();
        adminClient.deleteTopics(topicsToDeleteAfterTests, topicsDeletedPromise);
        topicsDeletedPromise.future()
                .recover(thr -> {
                    LOG.info("error deleting topics", thr);
                    return Future.succeededFuture();
                })
                .compose(ar -> producerClosePromise.future())
                .onComplete(ar -> {
                    topicsToDeleteAfterTests.clear();
                    topicsToDeleteAfterTests = null;
                    adminClient.close();
                    adminClient = null;
                    kafkaProducer = null;
                    vertx.close();
                    vertx = null;
                })
                .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Closes a Kafka consumer created during the test.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    void closeConsumer(final VertxTestContext ctx) {
        if (kafkaConsumer != null) {
            kafkaConsumer.stop().onComplete(ctx.succeedingThenComplete());
        }
    }

    /**
     * Verifies that an AsyncHandlingAutoCommitKafkaConsumer configured with "latest" as offset reset strategy and a
     * topic pattern subscription receives records published after multiple <em>ensureTopicIsAmongSubscribedTopicPatternTopics()</em>
     * invocations have been completed.
     * <p>
     * Also makes sure that intermittent deletion of topics doesn't fail the test.
     *
     * @param partitionAssignmentStrategy The partition assignment strategy to use for the consumer.
     * @param ctx The vert.x test context.
     * @throws InterruptedException if test execution gets interrupted.
     */
    @ParameterizedTest
    @MethodSource("partitionAssignmentStrategies")
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testConsumerReadsAllRecordsForDynamicallyCreatedTopics(
            final String partitionAssignmentStrategy, final VertxTestContext ctx) throws InterruptedException {
        final String patternPrefix = "test_" + UUID.randomUUID() + "_";
        final int numTopicsAndRecords = 6; // has to be an even number

        // create some matching topics - these shall be deleted after consumer start;
        // this shall make sure that topic deletion doesn't influence the test result
        final Set<String> otherTopics = IntStream.range(0, numTopicsAndRecords)
                .mapToObj(i -> patternPrefix + i + "_other")
                .collect(Collectors.toSet());

        final VertxTestContext setup = new VertxTestContext();
        createTopics(otherTopics, 1)
                .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final Pattern topicPattern = Pattern.compile(Pattern.quote(patternPrefix) + ".*");

        // prepare consumer
        final Map<String, String> consumerConfig = IntegrationTestSupport.getKafkaConsumerConfig().getConsumerConfig("test");
        applyPartitionAssignmentStrategy(consumerConfig, partitionAssignmentStrategy);
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumerConfig.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "8000");

        final Promise<Void> allRecordsReceivedPromise = Promise.promise();
        final List<KafkaConsumerRecord<String, Buffer>> receivedRecords = new ArrayList<>();
        final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> recordHandler = record -> {
            receivedRecords.add(record);
            if (receivedRecords.size() == numTopicsAndRecords) {
                allRecordsReceivedPromise.complete();
            }
            return Future.succeededFuture();
        };

        kafkaConsumer = new AsyncHandlingAutoCommitKafkaConsumer<>(vertx, topicPattern, recordHandler, consumerConfig);
        // start consumer
        final Promise<Void> readyTracker = Promise.promise();
        kafkaConsumer.addOnKafkaConsumerReadyHandler(readyTracker);
        kafkaConsumer.start()
            .compose(ok -> readyTracker.future())
            .onComplete(ctx.succeeding(v -> {
                ctx.verify(() -> {
                    assertThat(receivedRecords.size()).isEqualTo(0);
                });
                LOG.debug("consumer started, create new topics implicitly by invoking ensureTopicIsAmongSubscribedTopicPatternTopics()");
                final String recordKey = "addedAfterStartKey";
                for (int i = 0; i < numTopicsAndRecords; i = i + 2) {
                    final String topic = patternPrefix + i;
                    final String topic2 = patternPrefix + (i + 1);
                    final String otherTopic = patternPrefix + i + "_other";
                    final String otherTopic2 = patternPrefix + (i + 1) + "_other";
                    // use delay between handling topics with odd and even index (waiting for the result) - there should be multiple rebalances involved here
                    deleteTopicIfPossible(otherTopic)
                            .compose(v2 -> ensureTopicIsAmongSubscribedTopicPatternTopicsAndPublish(ctx, topic, recordKey))
                            .compose(v2 -> deleteTopicIfPossible(otherTopic2))
                            .compose(v2 -> ensureTopicIsAmongSubscribedTopicPatternTopicsAndPublish(ctx, topic2, recordKey))
                            .onComplete(ctx.succeeding(v2 -> {}));
                }
                allRecordsReceivedPromise.future().onComplete(ar -> {
                    ctx.verify(() -> {
                        assertThat(receivedRecords.size()).isEqualTo(numTopicsAndRecords);
                        receivedRecords.forEach(record -> assertThat(record.key()).isEqualTo(recordKey));
                    });
                    ctx.completeNow();
                });
            }));
        if (!ctx.awaitCompletion(9, TimeUnit.SECONDS)) {
            ctx.failNow(new IllegalStateException(String.format(
                    "timeout waiting for expected number of records (%d) to be received; received records: %d",
                    numTopicsAndRecords, receivedRecords.size())));
        }
    }

    private Future<Void> ensureTopicIsAmongSubscribedTopicPatternTopicsAndPublish(final VertxTestContext ctx,
            final String topic, final String recordKey) {
        return kafkaConsumer.ensureTopicIsAmongSubscribedTopicPatternTopics(topic)
                .onComplete(ctx.succeeding(v -> {
                    LOG.debug("publish record to be received by the consumer");
                    publish(topic, recordKey, Buffer.buffer("testPayload"));
                }));
    }

    private Future<Void> deleteTopicIfPossible(final String otherTopic) {
        if (!kafkaConsumer.isOffsetsCommitNeededForTopic(otherTopic)) {
            return adminClient.deleteTopics(List.of(otherTopic));
        }
        return Future.succeededFuture();
    }

    private static Future<Void> createTopics(final Collection<String> topicNames, final int numPartitions) {
        return createTopics(topicNames, numPartitions, Map.of());
    }

    private static Future<Void> createTopics(final Collection<String> topicNames, final int numPartitions,
            final Map<String, String> topicConfig) {
        topicsToDeleteAfterTests.addAll(topicNames);
        final Promise<Void> resultPromise = Promise.promise();
        final List<NewTopic> topics = topicNames.stream()
                .map(t -> new NewTopic(t, numPartitions, REPLICATION_FACTOR).setConfig(topicConfig))
                .collect(Collectors.toList());
        adminClient.createTopics(topics, resultPromise);
        return resultPromise.future();
    }

    private static Future<RecordMetadata> publish(final String topic, final String recordKey, final Buffer recordPayload) {
        final Promise<RecordMetadata> resultPromise = Promise.promise();
        final KafkaProducerRecord<String, Buffer> record = KafkaProducerRecord.create(topic, recordKey, recordPayload);
        kafkaProducer.send(record, resultPromise);
        return resultPromise.future();
    }

    private void applyPartitionAssignmentStrategy(final Map<String, String> consumerConfig,
            final String partitionAssignmentStrategy) {
        Optional.ofNullable(partitionAssignmentStrategy)
                .ifPresent(s -> consumerConfig.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, s));
    }
}

