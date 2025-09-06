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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.MetricName;
import org.eclipse.hono.client.kafka.consumer.AsyncHandlingAutoCommitKafkaConsumer;
import org.eclipse.hono.client.kafka.consumer.HonoKafkaConsumer;
import org.eclipse.hono.tests.EnabledIfMessagingSystemConfigured;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.MessagingType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
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

    @BeforeEach
    void printTestInfo(final TestInfo testInfo) {
        LOG.info("running test {}", testInfo.getDisplayName());
    }

    /**
     * Closes a Kafka consumer created during the test.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    void closeConsumer(final VertxTestContext ctx) {
        if (kafkaConsumer != null) {
            LOG.debug("AfterEach: closing consumer");
            kafkaConsumer.stop().onComplete(ctx.succeedingThenComplete());
        }
    }

    /**
     * Cleans up fixture.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutDown(final VertxTestContext ctx) {
        LOG.debug("shutting down test fixture");
        kafkaProducer.close()
            .recover(t -> {
                LOG.info("failed to close producer", t);
                return Future.succeededFuture();
            })
            .compose(ok -> adminClient.deleteTopics(topicsToDeleteAfterTests))
            .onFailure(thr -> {
                LOG.error("error deleting topics {}", topicsToDeleteAfterTests, thr);
                ctx.failNow("error deleting topics after all tests");
            })
            .onComplete(ar -> {
                topicsToDeleteAfterTests.clear();
                topicsToDeleteAfterTests = null;
                adminClient.close();
                adminClient = null;
                kafkaProducer = null;
                vertx.close();
                vertx = null;
                ctx.completeNow();
            });
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
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("partitionAssignmentStrategies")
    public void testConsumerReadsAllRecordsForDynamicallyCreatedTopics(
            final String partitionAssignmentStrategy,
            final VertxTestContext ctx) throws InterruptedException {

        final String patternPrefix = "test_%s_".formatted(UUID.randomUUID());
        final int numTopicsAndRecords = 6; // has to be an even number

        final var topicsToPublishTo = IntStream.range(0, numTopicsAndRecords)
                .mapToObj(i -> "%s_pub_%d".formatted(patternPrefix, i))
                .toList();
        final var publishedMessageTopics = new ArrayList<>();
        final var receivedMessageTopics = new ArrayList<>();

        // create some matching topics - these shall be deleted after consumer start;
        // this shall make sure that topic deletion doesn't influence the test result
        final var otherTopics = IntStream.range(0, numTopicsAndRecords)
                .mapToObj(i -> "%s_other_%d".formatted(patternPrefix, i))
                .toList();

        final var recordsReceived = ctx.checkpoint(numTopicsAndRecords);
        final String recordKey = "addedAfterStartKey";

        final VertxTestContext setup = new VertxTestContext();
        createTopics(otherTopics, 1)
            .compose(ok -> {
                final Pattern topicPattern = Pattern.compile(Pattern.quote(patternPrefix) + ".*");

                // prepare consumer
                final var consumerConfig = IntegrationTestSupport.getKafkaConsumerConfig().getConsumerConfig("test");
                applyPartitionAssignmentStrategy(consumerConfig, partitionAssignmentStrategy);
                consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

                final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> recordHandler = record -> {
                    ctx.verify(() -> {
                        assertThat(record.key()).isEqualTo(recordKey);
                    });
                    receivedMessageTopics.add(record.topic());
                    recordsReceived.flag();
                    return Future.succeededFuture();
                };

                kafkaConsumer = new AsyncHandlingAutoCommitKafkaConsumer<>(vertx, topicPattern, recordHandler, consumerConfig);
                // start consumer
                final Promise<Void> readyTracker = Promise.promise();
                kafkaConsumer.addOnKafkaConsumerReadyHandler(readyTracker);
                return kafkaConsumer.start()
                        .compose(started -> readyTracker.future())
                        // ensure consumer is subscribed to all previously created topics (this may require multiple rebalances)
                        .compose(ready -> Future.all(otherTopics.stream()
                                .map(t -> kafkaConsumer.ensureTopicIsAmongSubscribedTopicPatternTopics(t))
                                .toList()));
            })
            .onComplete(setup.succeeding(ok -> {
                LOG.debug("consumer started, create new topics implicitly by invoking ensureTopicIsAmongSubscribedTopicPatternTopics()");
                setup.completeNow();
            }));

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        for (int i = 0; i < numTopicsAndRecords; i++) {
            final String topic = topicsToPublishTo.get(i);
            final String otherTopic = otherTopics.get(i);
            // use delay between handling topics with odd and even index (waiting for the result) - there should be multiple rebalances involved here
            deleteTopicIfPossible(otherTopic)
                    .compose(v2 -> ensureTopicIsAmongSubscribedTopicPatternTopicsAndPublish(ctx, topic, recordKey))
                    .andThen(v2 -> publishedMessageTopics.add(topic))
                    .onFailure(ctx::failNow);
        }

        if (!ctx.awaitCompletion(numTopicsAndRecords * 3, TimeUnit.SECONDS)) {
            final var notPublishedMessageTopics = topicsToPublishTo.stream()
                    .filter(t -> !publishedMessageTopics.contains(t)).toList();
            final var notReceivedMessageTopics = topicsToPublishTo.stream()
                    .filter(t -> !receivedMessageTopics.contains(t)).toList();
            if (!notPublishedMessageTopics.isEmpty()) {
                LOG.error("records not published in time for topics: {}", notPublishedMessageTopics);
                ctx.failNow("timeout waiting for %d records to be published (%d are missing)"
                        .formatted(numTopicsAndRecords, notPublishedMessageTopics.size()));
            } else if (!notReceivedMessageTopics.isEmpty()) {
                LOG.error("records not received in time for topics: {}", notReceivedMessageTopics);
                ctx.failNow("timeout waiting for %d records to be received (%d are missing)"
                        .formatted(numTopicsAndRecords, notReceivedMessageTopics.size()));
            } else {
                ctx.failNow("test timeout");
            }
        }
    }

    /**
     * Verifies that a topic-pattern based AsyncHandlingAutoCommitKafkaConsumer removes topic-related metrics
     * once a topic that matches the topic-pattern gets deleted.
     *
     * NOTE: The logic for removing the metrics is located in HonoKafkaConsumer, therefore there should better be a test
     *  in HonoKafkaConsumerIT. But this proves to be difficult with the current integration test Kafka setup, where
     *  topic auto-creation is enabled in the broker config. For some reason, even when setting 'allow.auto.create.topics=false'
     *  for the consumer and having ensured that offsets got committed before topic-deletion (along with disabled standard
     *  auto-commit), the topic gets again auto-created some time after it got deleted, letting the test fail.
     *  With the manual auto-commit handling of the AsyncHandlingAutoCommitKafkaConsumer, this isn't the case.
     *
     * @param partitionAssignmentStrategy The partition assignment strategy to use for the consumer.
     * @param ctx The vert.x test context.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("partitionAssignmentStrategies")
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testPatternBasedConsumerRemovesMetricsOfDeletedTopics(
            final String partitionAssignmentStrategy,
            final VertxTestContext ctx) {

        // prepare consumer
        final var consumerConfig = IntegrationTestSupport.getKafkaConsumerConfig().getConsumerConfig("test");
        applyPartitionAssignmentStrategy(consumerConfig, partitionAssignmentStrategy);
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumerConfig.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
        consumerConfig.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, "100");
        consumerConfig.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "100");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        consumerConfig.put(HonoKafkaConsumer.CONFIG_HONO_OBSOLETE_METRICS_REMOVAL_DELAY_MILLIS, "200");

        final Promise<Void> recordReceivedPromise = Promise.promise();
        final Function<KafkaConsumerRecord<String, Buffer>, Future<Void>> recordHandler = record -> {
            LOG.debug("received record: {}", record);
            recordReceivedPromise.complete();
            return Future.succeededFuture();
        };
        final String topicPrefix = "test_" + UUID.randomUUID();
        final String topic = topicPrefix + "_toBeDeleted";
        kafkaConsumer = new AsyncHandlingAutoCommitKafkaConsumer<>(vertx, Pattern.compile(topicPrefix + ".*"),
                recordHandler, consumerConfig);
        // create topic and start consumer
        final Promise<Void> consumerReadyTracker = Promise.promise();
        kafkaConsumer.addOnKafkaConsumerReadyHandler(consumerReadyTracker);
        adminClient.createTopics(List.of(new NewTopic(topic, 1, REPLICATION_FACTOR)))
                .compose(ok -> kafkaConsumer.start())
                .compose(ok -> consumerReadyTracker.future())
                .compose(ok -> {
                    ctx.verify(() -> assertThat(recordReceivedPromise.future().isComplete()).isFalse());
                    LOG.debug("consumer started, publishing record to be received by the consumer...");
                    return Future.all(
                            publish(topic, "recordKey", Buffer.buffer("testPayload")),
                            recordReceivedPromise.future());
                })
                .compose(ok -> {
                    LOG.debug("waiting for offset to be committed");
                    final Promise<Void> offsetCommitCheckTracker = Promise.promise();
                    final AtomicInteger checkCount = new AtomicInteger(0);
                    vertx.setPeriodic(100, tid -> {
                        if (!kafkaConsumer.isOffsetsCommitNeededForTopic(topic)) {
                            vertx.cancelTimer(tid);
                            offsetCommitCheckTracker.complete();
                        } else if (checkCount.incrementAndGet() >= 10) {
                            vertx.cancelTimer(tid);
                            offsetCommitCheckTracker.fail("timeout waiting for offset commit");
                        }
                    });
                    return offsetCommitCheckTracker.future();
                })
                .compose(ok -> {
                    ctx.verify(() -> assertThat(getTopicRelatedMetricNames(topic)).isNotEmpty());
                    LOG.debug("delete topic {}", topic);
                    return adminClient.deleteTopics(List.of(topic));
                })
                .compose(ok -> {
                    LOG.debug("waiting for metrics to be removed...");
                    final Promise<Void> metricCheckTracker = Promise.promise();
                    final AtomicInteger checkCount = new AtomicInteger(0);
                    vertx.setPeriodic(200, tid -> {
                        LOG.debug("topic-related metrics: {}", getTopicRelatedMetricNames(topic));
                        if (getTopicRelatedMetricNames(topic).isEmpty()) {
                            vertx.cancelTimer(tid);
                            metricCheckTracker.complete();
                        } else if (checkCount.incrementAndGet() >= 40) {
                            vertx.cancelTimer(tid);
                            metricCheckTracker.fail("timeout waiting for metrics to be removed");
                        }
                    });
                    return metricCheckTracker.future();
                })
                .onComplete(ctx.succeeding(v -> ctx.completeNow()));
    }

    private Future<Void> ensureTopicIsAmongSubscribedTopicPatternTopicsAndPublish(
            final VertxTestContext ctx,
            final String topic,
            final String recordKey) {
        return kafkaConsumer.ensureTopicIsAmongSubscribedTopicPatternTopics(topic)
                .onFailure(ctx::failNow)
                .compose(v -> {
                    LOG.debug("publish record to be received by the consumer; topic: {}, recordKey: {}", topic, recordKey);
                    return publish(topic, recordKey, Buffer.buffer("testPayload"));
                })
                .mapEmpty();
    }

    private Future<Void> deleteTopicIfPossible(final String topicName) {
        if (!kafkaConsumer.isOffsetsCommitNeededForTopic(topicName)) {
            LOG.debug("deleteTopicIfPossible: No offsets commit needed for topic {}, deleting it", topicName);
            topicsToDeleteAfterTests.remove(topicName);
            return adminClient.deleteTopics(List.of(topicName));
        }
        return Future.succeededFuture();
    }

    private static Future<Void> createTopics(
            final Collection<String> topicNames,
            final int numPartitions) {
        return createTopics(topicNames, numPartitions, Map.of());
    }

    private static Future<Void> createTopics(
            final Collection<String> topicNames,
            final int numPartitions,
            final Map<String, String> topicConfig) {

        topicsToDeleteAfterTests.addAll(topicNames);
        final var topics = topicNames.stream()
                .map(t -> new NewTopic(t, numPartitions, REPLICATION_FACTOR).setConfig(topicConfig))
                .collect(Collectors.toList());
        return adminClient.createTopics(topics);
    }

    private static Future<RecordMetadata> publish(
            final String topic,
            final String recordKey,
            final Buffer recordPayload) {

        final var record = KafkaProducerRecord.create(topic, recordKey, recordPayload);
        return kafkaProducer.send(record);
    }

    private void applyPartitionAssignmentStrategy(final Map<String, String> consumerConfig,
            final String partitionAssignmentStrategy) {
        Optional.ofNullable(partitionAssignmentStrategy)
                .ifPresent(s -> consumerConfig.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, s));
    }

    private List<String> getTopicRelatedMetricNames(final String topicName) {
        return kafkaConsumer.metrics().keySet().stream()
                .filter(metricName -> metricName.tags().containsValue(topicName))
                .map(MetricName::name)
                .collect(Collectors.toList());
    }
}

