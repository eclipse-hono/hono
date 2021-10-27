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

package org.eclipse.hono.client.notification;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.hono.client.kafka.AbstractKafkaConfigProperties;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.kafka.test.KafkaMockConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies behavior of {@link KafkaBasedNotificationReceiver}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class KafkaBasedNotificationReceiverTest {

    public static final String TOPIC = new HonoTopic(HonoTopic.Type.NOTIFICATION, TestNotification.ADDRESS).toString();
    public static final int PARTITION = 0;

    private final Map<String, String> consumerConfig = new HashMap<>();

    private Vertx vertx;
    private KafkaMockConsumer mockConsumer;

    /**
     *
     * Sets up fixture.
     *
     * @param vertx The vert.x instance to use.
     */
    @BeforeEach
    void setUp(final Vertx vertx) {
        this.vertx = vertx;

        mockConsumer = new KafkaMockConsumer(OffsetResetStrategy.EARLIEST);

        consumerConfig.put(AbstractKafkaConfigProperties.PROPERTY_BOOTSTRAP_SERVERS, "kafka");
        consumerConfig.put("client.id", "application-test-consumer");

    }

    /**
     * Verifies that the consumer is successfully created by the receiver.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateConsumer(final VertxTestContext ctx) {

        final var receiver = createReceiver();

        receiver.registerConsumer(TestNotification.class, notification -> {
                });

        receiver.start()
                .onComplete(ctx.succeeding(v -> ctx.verify(() -> {
                    final Set<String> subscription = mockConsumer.subscription();
                    assertThat(subscription).isNotNull();
                    assertThat(subscription).contains(TOPIC);
                    assertThat(mockConsumer.closed()).isFalse();
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the underlying Kafka consumer is closed when the receiver is stopped.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testStopClosesConsumer(final VertxTestContext ctx) {

        final var receiver = createReceiver();

        receiver.registerConsumer(TestNotification.class, notification -> {
        });

        receiver.start()
                .compose(v -> receiver.stop())
                .onComplete(ctx.succeeding(v -> ctx.verify(() -> {
                    assertThat(mockConsumer.closed()).isTrue();
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the receiver decodes and handles a notification it receives.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatNotificationIsHandled(final VertxTestContext ctx) {

        final var receiver = createReceiver();

        receiver.registerConsumer(TestNotification.class, notification -> {
            ctx.verify(() -> {
                assertThat(notification).isNotNull();
                assertThat(notification.getType()).isEqualTo(TestNotification.TYPE);
                assertThat(notification.getAddress()).isEqualTo(TestNotification.ADDRESS);
                assertThat(notification.getSource()).isEqualTo(TestNotification.SOURCE);
                assertThat(notification.getTimestamp()).isEqualTo(TestNotification.TIMESTAMP);
                ctx.completeNow();
            });
        });

        receiver.start()
                .onComplete(ctx.succeeding(v -> ctx.verify(() -> mockConsumer.schedulePollTask(() -> {
                    final ConsumerRecord<String, Buffer> record = new ConsumerRecord<>(TOPIC, PARTITION, 0L, null,
                            JsonObject.mapFrom(new TestNotification()).toBuffer());
                    mockConsumer.addRecord(record);
                }))));
    }

    private KafkaBasedNotificationReceiver createReceiver() {

        final TopicPartition topicPartition = new TopicPartition(TOPIC, PARTITION);
        mockConsumer.updateBeginningOffsets(Map.of(topicPartition, 0L));
        mockConsumer.updatePartitions(topicPartition, KafkaMockConsumer.DEFAULT_NODE);
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(topicPartition));

        final KafkaBasedNotificationReceiver client = new TestNotificationReceiver(vertx, consumerConfig);
        client.setKafkaConsumerFactory(() -> mockConsumer);

        return client;
    }

    static class TestNotification implements Notification {

        public static final String TYPE = "test-type";
        public static final String ADDRESS = "test-topic";
        public static final String SOURCE = "test-source";
        public static final Instant TIMESTAMP = Instant.parse("2020-08-11T11:38:00Z");

        final String source;
        final Instant timestamp;

        TestNotification() {
            this(SOURCE, TIMESTAMP);
        }

        // used for decoding from JSON
        TestNotification(
                @JsonProperty(value = NotificationConstants.JSON_FIELD_SOURCE) final String source,
                @JsonProperty(value = NotificationConstants.JSON_FIELD_TIMESTAMP) final Instant timestamp) {
            this.source = source;
            this.timestamp = timestamp;
        }

        @Override
        public String getType() {
            return TYPE;
        }

        @Override
        public String getAddress() {
            return ADDRESS;
        }

        @Override
        public String getSource() {
            return source;
        }

        @Override
        public Instant getTimestamp() {
            return timestamp;
        }
    }

    static class TestNotificationReceiver extends KafkaBasedNotificationReceiver {

        TestNotificationReceiver(final Vertx vertx, final Map<String, String> consumerConfig) {
            super(vertx, consumerConfig);
        }

        @Override
        protected <T extends Notification> String getAddressForType(final Class<T> notificationType) {
            return TestNotification.ADDRESS;
        }

        @Override
        protected Notification decodeNotification(final Buffer json) {
            return Json.decodeValue(json, TestNotification.class);
        }
    }
}
