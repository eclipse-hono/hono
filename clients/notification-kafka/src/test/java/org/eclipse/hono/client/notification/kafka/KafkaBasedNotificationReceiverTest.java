/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.notification.kafka;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.hono.kafka.test.KafkaMockConsumer;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.deviceregistry.AllDevicesOfTenantDeletedNotification;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies behavior of {@link KafkaBasedNotificationReceiver}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class KafkaBasedNotificationReceiverTest {

    private NotificationKafkaConsumerConfigProperties consumerConfig;

    private Vertx vertx;
    private KafkaMockConsumer<String, JsonObject> mockConsumer;

    /**
     *
     * Sets up fixture.
     *
     * @param vertx The vert.x instance to use.
     */
    @BeforeEach
    void setUp(final Vertx vertx) {
        this.vertx = vertx;

        mockConsumer = new KafkaMockConsumer<>(OffsetResetStrategy.EARLIEST);

        consumerConfig = new NotificationKafkaConsumerConfigProperties();
        consumerConfig.setConsumerConfig(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "dummy"));

    }

    /**
     * Verifies that the consumer is successfully created by the receiver.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateConsumer(final VertxTestContext ctx) {

        final Promise<Void> readyTracker = Promise.promise();
        final var receiver = createReceiver();
        receiver.registerConsumer(TenantChangeNotification.TYPE, notification -> {});
        receiver.addOnKafkaConsumerReadyHandler(readyTracker);
        receiver.start()
            .compose(ok -> readyTracker.future())
            .onComplete(ctx.succeeding(v -> {
                ctx.verify(() -> {
                    final Set<String> subscription = mockConsumer.subscription();
                    assertThat(subscription).isNotNull();
                    assertThat(subscription)
                            .contains(NotificationTopicHelper.getTopicName(TenantChangeNotification.TYPE));
                    assertThat(mockConsumer.closed()).isFalse();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the underlying Kafka consumer is closed when the receiver is stopped.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testStopClosesConsumer(final VertxTestContext ctx) {

        final Promise<Void> readyTracker = Promise.promise();
        final var receiver = createReceiver();
        receiver.registerConsumer(TenantChangeNotification.TYPE, notification -> {});
        receiver.addOnKafkaConsumerReadyHandler(readyTracker);
        receiver.start()
            .compose(ok -> readyTracker.future())
            .compose(v -> receiver.stop())
            .onComplete(ctx.succeeding(v -> ctx.verify(() -> {
                assertThat(mockConsumer.closed()).isTrue();
                ctx.completeNow();
            })));
    }

    /**
     * Verifies that the receiver decodes the notifications it receives and invokes the correct handler.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatCorrectHandlerIsInvoked(final VertxTestContext ctx) {

        final String tenantId = "my-tenant";
        final String deviceId = "my-device";
        final Instant creationTime = Instant.parse("2007-12-03T10:15:30Z");

        mockConsumer.schedulePollTask(() -> {
            mockConsumer.addRecord(createKafkaRecord(
                    new TenantChangeNotification(LifecycleChange.CREATE, tenantId, creationTime, false, false), 0L));

            mockConsumer.addRecord(createKafkaRecord(
                    new DeviceChangeNotification(LifecycleChange.CREATE, tenantId, deviceId, creationTime, false), 0L));

            mockConsumer.addRecord(createKafkaRecord(
                    new CredentialsChangeNotification(tenantId, deviceId, creationTime), 1L));

            mockConsumer.addRecord(createKafkaRecord(
                    new AllDevicesOfTenantDeletedNotification(tenantId, creationTime), 2L));
        });

        final var receiver = createReceiver();

        final Checkpoint handlerInvokedCheckpoint = ctx.checkpoint(4);

        receiver.registerConsumer(TenantChangeNotification.TYPE,
                notification -> ctx.verify(() -> {
                    assertThat(notification).isInstanceOf(TenantChangeNotification.class);
                    handlerInvokedCheckpoint.flag();
                }));

        receiver.registerConsumer(DeviceChangeNotification.TYPE,
                notification -> ctx.verify(() -> {
                    assertThat(notification).isInstanceOf(DeviceChangeNotification.class);
                    handlerInvokedCheckpoint.flag();
                }));

        receiver.registerConsumer(CredentialsChangeNotification.TYPE,
                notification -> ctx.verify(() -> {
                    assertThat(notification).isInstanceOf(CredentialsChangeNotification.class);
                    handlerInvokedCheckpoint.flag();
                }));

        receiver.registerConsumer(AllDevicesOfTenantDeletedNotification.TYPE,
                notification -> ctx.verify(() -> {
                    assertThat(notification).isInstanceOf(AllDevicesOfTenantDeletedNotification.class);
                    handlerInvokedCheckpoint.flag();
                }));

        receiver.start();

    }

    private KafkaBasedNotificationReceiver createReceiver() {

        final TopicPartition tenantTopicPartition = new TopicPartition(
                NotificationTopicHelper.getTopicName(TenantChangeNotification.TYPE), 0);
        final TopicPartition deviceTopicPartition = new TopicPartition(
                NotificationTopicHelper.getTopicName(DeviceChangeNotification.TYPE), 0);

        mockConsumer.updateBeginningOffsets(Map.of(tenantTopicPartition, 0L, deviceTopicPartition, 0L));
        mockConsumer.setRebalancePartitionAssignmentAfterSubscribe(List.of(tenantTopicPartition, deviceTopicPartition));

        final KafkaBasedNotificationReceiver client = new KafkaBasedNotificationReceiver(vertx, consumerConfig);
        client.setKafkaConsumerSupplier(() -> mockConsumer);

        return client;
    }

    private ConsumerRecord<String, JsonObject> createKafkaRecord(
            final AbstractNotification notification,
            final long offset) {

        final var json = JsonObject.mapFrom(notification);
        final String topicName = NotificationTopicHelper.getTopicName(notification.getType());
        return new ConsumerRecord<>(topicName, 0, offset, null, json);
    }

}
