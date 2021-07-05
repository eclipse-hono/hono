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
package org.eclipse.hono.tests.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.eclipse.hono.client.kafka.consumer.HonoKafkaConsumer;
import org.eclipse.hono.tests.AssumeMessagingSystem;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.MessagingType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
 * Test cases verifying the behavior of {@link HonoKafkaConsumer}.
 * <p>
 * To run this on a specific Kafka cluster instance, set the
 * {@value IntegrationTestSupport#PROPERTY_DOWNSTREAM_BOOTSTRAP_SERVERS} system property,
 * e.g. <code>-Ddownstream.bootstrap.servers="PLAINTEXT://localhost:9092"</code>.
 */
@ExtendWith(VertxExtension.class)
@AssumeMessagingSystem(type = MessagingType.kafka)
public class HonoKafkaConsumerIT {

    private static final Logger LOG = LoggerFactory.getLogger(HonoKafkaConsumerIT.class);

    private static final short REPLICATION_FACTOR = 1;

    private static Vertx vertx;
    private static KafkaAdminClient adminClient;
    private static KafkaProducer<String, Buffer> kafkaProducer;
    private static List<String> topicsToDeleteAfterTests = new ArrayList<>();

    private HonoKafkaConsumer kafkaConsumer;

    /**
     * Sets up fixture.
     */
    @BeforeAll
    public static void init() {
        vertx = Vertx.vertx();

        final Map<String, String> adminClientConfig = IntegrationTestSupport.getKafkaAdminClientConfig()
                .getAdminClientConfig("test");
        adminClient = KafkaAdminClient.create(vertx, adminClientConfig);
        final Map<String, String> producerConfig = IntegrationTestSupport.getKafkaProducerConfig()
                .getProducerConfig("test");
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
                .onComplete(ctx.completing());
    }

    /**
     * Closes a Kafka consumer created during the test.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    void closeConsumer(final VertxTestContext ctx) {
        if (kafkaConsumer != null) {
            kafkaConsumer.stop().onComplete(ctx.completing());
        }
    }

    /**
     * Verifies that a HonoKafkaConsumer configured with "latest" as offset reset strategy only receives
     * records published after the consumer <em>start()</em> method has completed.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testConsumerReadsLatestRecordsPublishedAfterStart(final VertxTestContext ctx) throws InterruptedException {
        final int numTopics = 2;
        final int numPartitions = 5;
        final int numTestRecordsPerTopic = 20;

        final Set<String> topics = IntStream.range(0, numTopics)
                .mapToObj(i -> "test_" + i + "_" + UUID.randomUUID())
                .collect(Collectors.toSet());

        final VertxTestContext setup = new VertxTestContext();
        createTopics(topics, numPartitions)
                .compose(v -> publishRecords(numTestRecordsPerTopic, "key_", topics))
                .onComplete(setup.completing());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }
        LOG.debug("topics created and (to be ignored) test records published");

        // prepare consumer
        final Map<String, String> consumerConfig = IntegrationTestSupport.getKafkaConsumerConfig().getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        final AtomicReference<Promise<Void>> nextRecordReceivedPromiseRef = new AtomicReference<>();
        final List<KafkaConsumerRecord<String, Buffer>> receivedRecords = new ArrayList<>();
        final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler = record -> {
            receivedRecords.add(record);
            Optional.ofNullable(nextRecordReceivedPromiseRef.get())
                    .ifPresent(Promise::complete);
        };
        kafkaConsumer = new HonoKafkaConsumer(vertx, topics, recordHandler, consumerConfig);
        // start consumer
        kafkaConsumer.start().onComplete(ctx.succeeding(v -> {
            ctx.verify(() -> {
                assertThat(receivedRecords.size()).isEqualTo(0);
            });
            final Promise<Void> nextRecordReceivedPromise = Promise.promise();
            nextRecordReceivedPromiseRef.set(nextRecordReceivedPromise);

            LOG.debug("consumer started, publish record to be received by the consumer");
            final String recordKey = "addedAfterStartKey";
            publish(topics.iterator().next(), recordKey, Buffer.buffer("testPayload"));

            nextRecordReceivedPromise.future().onComplete(ar -> {
                ctx.verify(() -> {
                    assertThat(receivedRecords.size()).isEqualTo(1);
                    assertThat(receivedRecords.get(0).key()).isEqualTo(recordKey);
                });
                ctx.completeNow();
            });
        }));
    }

    /**
     * Verifies that a HonoKafkaConsumer configured with "latest" as offset reset strategy and a topic pattern
     * subscription only receives records published after the consumer <em>start()</em> method has completed.
     * <p>
     * Also verifies that all records published after the consumer <em>ensureTopicIsAmongSubscribedTopicPatternTopics()</em>
     * method has completed are received by the consumer, also if the topic was only created after the consumer
     * <em>start</em> method has completed.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testConsumerReadsLatestRecordsPublishedAfterTopicSubscriptionConfirmed(final VertxTestContext ctx) throws InterruptedException {
        final String patternPrefix = "test_" + UUID.randomUUID() + "_";
        final int numTopics = 2;
        final Pattern topicPattern = Pattern.compile(Pattern.quote(patternPrefix) + ".*");
        final int numPartitions = 5;
        final int numTestRecordsPerTopic = 20;

        final Set<String> topics = IntStream.range(0, numTopics)
                .mapToObj(i -> patternPrefix + i)
                .collect(Collectors.toSet());

        final VertxTestContext setup = new VertxTestContext();
        createTopics(topics, numPartitions)
                .compose(v -> publishRecords(numTestRecordsPerTopic, "key_", topics))
                .onComplete(setup.completing());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }
        LOG.debug("topics created and (to be ignored) test records published");

        // prepare consumer
        final Map<String, String> consumerConfig = IntegrationTestSupport.getKafkaConsumerConfig().getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        final AtomicReference<Promise<Void>> nextRecordReceivedPromiseRef = new AtomicReference<>();
        final List<KafkaConsumerRecord<String, Buffer>> receivedRecords = new ArrayList<>();
        final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler = record -> {
            receivedRecords.add(record);
            Optional.ofNullable(nextRecordReceivedPromiseRef.get())
                    .ifPresent(Promise::complete);
        };
        kafkaConsumer = new HonoKafkaConsumer(vertx, topicPattern, recordHandler, consumerConfig);
        // start consumer
        kafkaConsumer.start().onComplete(ctx.succeeding(v -> {
            ctx.verify(() -> {
                assertThat(receivedRecords.size()).isEqualTo(0);
            });
            final Promise<Void> nextRecordReceivedPromise = Promise.promise();
            nextRecordReceivedPromiseRef.set(nextRecordReceivedPromise);

            LOG.debug("consumer started, create new topic implicitly by invoking ensureTopicIsAmongSubscribedTopicPatternTopics()");
            final String newTopic = patternPrefix + "new";
            final String recordKey = "addedAfterStartKey";
            kafkaConsumer.ensureTopicIsAmongSubscribedTopicPatternTopics(newTopic)
                    .onComplete(ctx.succeeding(v2 -> {
                        LOG.debug("publish record to be received by the consumer");
                        publish(newTopic, recordKey, Buffer.buffer("testPayload"));
                    }));

            nextRecordReceivedPromise.future().onComplete(ar -> {
                ctx.verify(() -> {
                    assertThat(receivedRecords.size()).isEqualTo(1);
                    assertThat(receivedRecords.get(0).key()).isEqualTo(recordKey);
                });
                ctx.completeNow();
            });
        }));
    }

    /**
     * Verifies that a HonoKafkaConsumer configured with "earliest" as offset reset strategy receives all
     * current records after the consumer <em>start()</em> method has completed.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testConsumerReadsAllRecordsAfterStart(final VertxTestContext ctx) throws InterruptedException {
        final int numTopics = 2;
        final int numPartitions = 5;
        final int numTestRecordsPerTopic = 20;

        final Set<String> topics = IntStream.range(0, numTopics)
                .mapToObj(i -> "test_" + i + "_" + UUID.randomUUID())
                .collect(Collectors.toSet());

        final VertxTestContext setup = new VertxTestContext();
        createTopics(topics, numPartitions)
                .compose(v -> publishRecords(numTestRecordsPerTopic, "key_", topics))
                .onComplete(setup.completing());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }
        LOG.debug("topics created and test records published");

        // prepare consumer
        final Map<String, String> consumerConfig = IntegrationTestSupport.getKafkaConsumerConfig().getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        final Promise<Void> allRecordsReceivedPromise = Promise.promise();
        final List<KafkaConsumerRecord<String, Buffer>> receivedRecords = new ArrayList<>();
        final int totalExpectedMessages = numTopics * numTestRecordsPerTopic;
        final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler = record -> {
            receivedRecords.add(record);
            if (receivedRecords.size() == totalExpectedMessages) {
                allRecordsReceivedPromise.complete();
            }
        };
        kafkaConsumer = new HonoKafkaConsumer(vertx, topics, recordHandler, consumerConfig);
        // start consumer
        kafkaConsumer.start().onComplete(ctx.succeeding(v -> {
            ctx.verify(() -> {
                assertThat(receivedRecords.size()).isEqualTo(0);
            });
            allRecordsReceivedPromise.future().onComplete(ar -> {
                ctx.verify(() -> {
                    assertThat(receivedRecords.size()).isEqualTo(totalExpectedMessages);
                });
                ctx.completeNow();
            });
        }));
    }

    /**
     * Verifies that a HonoKafkaConsumer that is using a not yet existing topic and that is configured with
     * "latest" as offset reset strategy, only receives records on the auto-created topic published after the consumer
     * <em>start()</em> method has completed.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testConsumerAutoCreatesTopicAndReadsLatestRecordsPublishedAfterStart(final VertxTestContext ctx) {

        // prepare consumer
        final Map<String, String> consumerConfig = IntegrationTestSupport.getKafkaConsumerConfig().getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        final AtomicReference<Promise<Void>> nextRecordReceivedPromiseRef = new AtomicReference<>();
        final List<KafkaConsumerRecord<String, Buffer>> receivedRecords = new ArrayList<>();
        final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler = record -> {
            receivedRecords.add(record);
            Optional.ofNullable(nextRecordReceivedPromiseRef.get())
                    .ifPresent(Promise::complete);
        };
        final String topic = "test_" + UUID.randomUUID();
        topicsToDeleteAfterTests.add(topic);
        kafkaConsumer = new HonoKafkaConsumer(vertx, Set.of(topic), recordHandler, consumerConfig);
        // start consumer
        kafkaConsumer.start().onComplete(ctx.succeeding(v -> {
            ctx.verify(() -> {
                assertThat(receivedRecords.size()).isEqualTo(0);
            });
            final Promise<Void> nextRecordReceivedPromise = Promise.promise();
            nextRecordReceivedPromiseRef.set(nextRecordReceivedPromise);

            LOG.debug("consumer started, publish record to be received by the consumer");
            final String recordKey = "addedAfterStartKey";
            publish(topic, recordKey, Buffer.buffer("testPayload"));

            nextRecordReceivedPromise.future().onComplete(ar -> {
                ctx.verify(() -> {
                    assertThat(receivedRecords.size()).isEqualTo(1);
                    assertThat(receivedRecords.get(0).key()).isEqualTo(recordKey);
                });
                ctx.completeNow();
            });
        }));
    }

    private static Future<Void> createTopics(final Collection<String> topicNames, final int numPartitions) {
        topicsToDeleteAfterTests.addAll(topicNames);
        final Promise<Void> resultPromise = Promise.promise();
        final List<NewTopic> topics = topicNames.stream()
                .map(t -> new NewTopic(t, numPartitions, REPLICATION_FACTOR))
                .collect(Collectors.toList());
        adminClient.createTopics(topics, resultPromise.future());
        return resultPromise.future();
    }

    private Future<Void> publishRecords(final int numTestRecordsPerTopic, final String keyPrefix, final Set<String> topics) {
        @SuppressWarnings("rawtypes")
        final List<Future> resultFutures = new ArrayList<>();
        topics.forEach(topic -> {
            resultFutures.add(publishRecords(numTestRecordsPerTopic, keyPrefix, topic));
        });
        return CompositeFuture.all(resultFutures).map((Void) null);
    }

    private Future<Void> publishRecords(final int numRecords, final String keyPrefix, final String topic) {
        @SuppressWarnings("rawtypes")
        final List<Future> resultFutures = new ArrayList<>();
        IntStream.range(0, numRecords).forEach(i -> {
            resultFutures.add(publish(topic, keyPrefix + i, Buffer.buffer("testPayload")).mapEmpty());
        });
        return CompositeFuture.all(resultFutures).map((Void) null);
    }

    private static Future<RecordMetadata> publish(final String topic, final String recordKey, final Buffer recordPayload) {
        final Promise<RecordMetadata> resultPromise = Promise.promise();
        final KafkaProducerRecord<String, Buffer> record = KafkaProducerRecord.create(topic, recordKey, recordPayload);
        kafkaProducer.send(record, resultPromise);
        return resultPromise.future();
    }

}

