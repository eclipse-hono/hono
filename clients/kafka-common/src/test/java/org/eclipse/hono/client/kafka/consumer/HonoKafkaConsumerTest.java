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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.hono.kafka.test.KafkaMockConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * Verifies the behavior of {@link HonoKafkaConsumerTest}.
 */
@Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
@ExtendWith(VertxExtension.class)
public class HonoKafkaConsumerTest {

    private static final Logger LOG = LoggerFactory.getLogger(HonoKafkaConsumerTest.class);

    private static final String TOPIC = "test.topic";
    private static final String TOPIC2 = "test.topic2";
    private static final String TOPIC3 = "test.topic3";
    private static final Pattern TOPIC_PATTERN = Pattern.compile(Pattern.quote("test.") + ".*");
    private static final int PARTITION = 0;
    private static final TopicPartition topicPartition = new TopicPartition(TOPIC, PARTITION);
    private static final TopicPartition topic2Partition = new TopicPartition(TOPIC2, PARTITION);
    private static final TopicPartition topic3Partition = new TopicPartition(TOPIC3, PARTITION);

    private KafkaConsumerConfigProperties consumerConfigProperties;
    private Vertx vertx;
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

        mockConsumer = new KafkaMockConsumer(OffsetResetStrategy.LATEST);

        final Map<String, String> commonProperties = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "servers");
        consumerConfigProperties = new KafkaConsumerConfigProperties();
        consumerConfigProperties.setCommonClientConfig(commonProperties);
    }

    /**
     * Verifies that trying to create a HonoKafkaConsumer with a topic list succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConsumerCreationWithTopicListSucceeds(final VertxTestContext ctx) {
        final Handler<KafkaConsumerRecord<String, Buffer>> handler = record -> LOG.debug("{}", record);
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());

        consumer = new HonoKafkaConsumer(vertx, Set.of(TOPIC), handler, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        mockConsumer.updateEndOffsets(Map.of(topicPartition, ((long) 0)));
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition));
        consumer.start().onComplete(ctx.completing());
    }

    /**
     * Verifies that trying to create a HonoKafkaConsumer with a topic pattern succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConsumerCreationWithTopicPatternSucceeds(final VertxTestContext ctx) {
        final Handler<KafkaConsumerRecord<String, Buffer>> handler = record -> LOG.debug("{}", record);
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());

        consumer = new HonoKafkaConsumer(vertx, TOPIC_PATTERN, handler, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        mockConsumer.updateEndOffsets(Map.of(topicPartition, (long) 0, topic2Partition, (long) 0));
        mockConsumer.updatePartitions(topicPartition, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.updatePartitions(topic2Partition, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition, topic2Partition));
        consumer.start().onComplete(ctx.succeeding(v2 -> {
            ctx.verify(() -> {
                assertThat(consumer.getSubscribedTopicPatternTopics()).isEqualTo(Set.of(TOPIC, TOPIC2));
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
     * @param ctx The vert.x test context.
     */
    @Test
    public void testEnsureTopicIsAmongSubscribedTopicsSucceedsForAddedTopic(final VertxTestContext ctx) {
        final Handler<KafkaConsumerRecord<String, Buffer>> handler = record -> LOG.debug("{}", record);
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());

        consumer = new HonoKafkaConsumer(vertx, TOPIC_PATTERN, handler, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        mockConsumer.updateEndOffsets(Map.of(topicPartition, (long) 0, topic2Partition, (long) 0));
        mockConsumer.updatePartitions(topicPartition, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.updatePartitions(topic2Partition, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition, topic2Partition));
        consumer.start().onComplete(ctx.succeeding(v2 -> {
            ctx.verify(() -> {
                assertThat(consumer.getSubscribedTopicPatternTopics()).isEqualTo(Set.of(TOPIC, TOPIC2));
            });
            // now update partitions with the one for topic3
            mockConsumer.updatePartitions(topic3Partition, KafkaMockConsumer.DEFAULT_NODE);
            mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition, topic2Partition, topic3Partition));
            mockConsumer.updateEndOffsets(Map.of(topic3Partition, (long) 0));

            consumer.ensureTopicIsAmongSubscribedTopicPatternTopics(TOPIC3).onComplete(ctx.succeeding(v3 -> {
                ctx.verify(() -> {
                    assertThat(consumer.getSubscribedTopicPatternTopics()).isEqualTo(Set.of(TOPIC, TOPIC2, TOPIC3));
                });
                ctx.completeNow();
            }));
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
        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());

        consumer = new HonoKafkaConsumer(vertx, Set.of(TOPIC), handler, consumerConfig);
        consumer.setKafkaConsumerSupplier(() -> mockConsumer);
        mockConsumer.updateEndOffsets(Map.of(topicPartition, ((long) 0)));
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition));
        consumer.start().onComplete(ctx.succeeding(v2 -> {
            mockConsumer.schedulePollTask(() -> {
                IntStream.range(0, numTestRecords).forEach(offset -> {
                    mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, PARTITION, offset, "key_" + offset, Buffer.buffer()));
                });
            });
        }));
    }

}
