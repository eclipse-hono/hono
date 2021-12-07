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

package org.eclipse.hono.client.notification.kafka;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.Map;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationConstants;
import org.eclipse.hono.notification.deviceregistry.AllDevicesOfTenantDeletedNotification;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.client.serialization.JsonObjectSerializer;

/**
 * Verifies behavior of {@link KafkaBasedNotificationSender}.
 */
@ExtendWith(VertxExtension.class)
public class KafkaBasedNotificationSenderTest {

    private static final Instant CREATION_TIME = Instant.parse("2007-12-03T10:15:30Z");
    private static final LifecycleChange CHANGE = LifecycleChange.CREATE;
    private static final String TENANT_ID = "my-tenant";
    private static final String DEVICE_ID = "my-device";
    private static final boolean ENABLED = false;

    private final NotificationKafkaProducerConfigProperties config = new NotificationKafkaProducerConfigProperties();

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        config.setProducerConfig(Map.of("hono.kafka.producerConfig.bootstrap.servers", "localhost:9092"));
    }

    /**
     * Verifies that {@link KafkaBasedNotificationSender#start()} creates a producer and *
     * {@link KafkaBasedNotificationSender#stop()} closes it.
     */
    @Test
    public void testLifecycle() {
        final MockProducer<String, JsonObject> mockProducer = newMockProducer();
        final CachingKafkaProducerFactory<String, JsonObject> factory = newProducerFactory(mockProducer);
        final KafkaBasedNotificationSender sender = new KafkaBasedNotificationSender(factory, config);

        assertThat(factory.getProducer(KafkaBasedNotificationSender.PRODUCER_NAME).isPresent()).isFalse();
        sender.start();
        assertThat(factory.getProducer(KafkaBasedNotificationSender.PRODUCER_NAME).isPresent()).isTrue();
        sender.stop();
        assertThat(factory.getProducer(KafkaBasedNotificationSender.PRODUCER_NAME).isPresent()).isFalse();
    }

    /**
     * Verifies that the expected Kafka record is created when publishing a {@link TenantChangeNotification}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testProducerRecordForTenantNotification(final VertxTestContext ctx) {

        final TenantChangeNotification notification = new TenantChangeNotification(CHANGE, TENANT_ID, CREATION_TIME,
                ENABLED);
        testProducerRecordForNotification(ctx, notification, TENANT_ID);
    }

    /**
     * Verifies that the expected Kafka record is created when publishing a {@link DeviceChangeNotification}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testProducerRecordForDeviceNotification(final VertxTestContext ctx) {

        final DeviceChangeNotification notification = new DeviceChangeNotification(CHANGE, TENANT_ID, DEVICE_ID,
                CREATION_TIME, ENABLED);
        testProducerRecordForNotification(ctx, notification, DEVICE_ID);
    }

    /**
     * Verifies that the expected Kafka record is created when publishing a {@link CredentialsChangeNotification}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testProducerRecordForCredentialsNotification(final VertxTestContext ctx) {

        final CredentialsChangeNotification notification = new CredentialsChangeNotification(TENANT_ID, DEVICE_ID,
                CREATION_TIME);
        testProducerRecordForNotification(ctx, notification, DEVICE_ID);
    }

    /**
     * Verifies that the expected Kafka record is created when publishing a {@link AllDevicesOfTenantDeletedNotification}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testProducerRecordForAllDevicesOfTenantDeletedNotification(final VertxTestContext ctx) {

        final AllDevicesOfTenantDeletedNotification notification = new AllDevicesOfTenantDeletedNotification(TENANT_ID,
                CREATION_TIME);
        testProducerRecordForNotification(ctx, notification, TENANT_ID);
    }

    private void testProducerRecordForNotification(
            final VertxTestContext ctx,
            final AbstractNotification notificationToSend,
            final String expectedRecordKey) {

        // GIVEN a sender
        final MockProducer<String, JsonObject> mockProducer = newMockProducer();
        final KafkaBasedNotificationSender sender = newSender(mockProducer);

        // WHEN publishing the notification
        sender.publish(notificationToSend)
                .onComplete(ctx.succeeding(v -> {
                    // THEN the producer record is created from the given values
                    ctx.verify(() -> {
                        final ProducerRecord<String, JsonObject> record = mockProducer.history().get(0);

                        assertThat(record.topic())
                                .isEqualTo(NotificationTopicHelper.getTopicName(notificationToSend.getClass()));
                        assertThat(record.key()).isEqualTo(expectedRecordKey);
                        assertThat(record.value().getString(NotificationConstants.JSON_FIELD_TYPE))
                                .isEqualTo(notificationToSend.getType());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the send method returns the underlying error wrapped in a {@link ServerErrorException}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendFailsWithTheExpectedError(final VertxTestContext ctx) {

        // GIVEN a sender sending a message
        final RuntimeException expectedError = new RuntimeException("boom");
        final MockProducer<String, JsonObject> mockProducer = new MockProducer<>(false, new StringSerializer(),
                new JsonObjectSerializer());
        final KafkaBasedNotificationSender sender = newSender(mockProducer);

        sender.publish(new TenantChangeNotification(CHANGE, TENANT_ID, CREATION_TIME, ENABLED))
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN it fails with the expected error
                        assertThat(t).isInstanceOf(ServerErrorException.class);
                        assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(503);
                        assertThat(t.getCause()).isEqualTo(expectedError);
                    });
                    ctx.completeNow();
                }));

        // WHEN the send operation fails
        mockProducer.errorNext(expectedError);

    }

    /**
     * Verifies that the constructor throws a nullpointer exception if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {
        final MockProducer<String, JsonObject> mockProducer = newMockProducer();

        final KafkaProducerFactory<String, JsonObject> factory = newProducerFactory(mockProducer);

        assertThrows(NullPointerException.class, () -> new KafkaBasedNotificationSender(null, config));
        assertThrows(NullPointerException.class, () -> new KafkaBasedNotificationSender(factory, null));

    }

    /**
     * Verifies that {@link KafkaBasedNotificationSender#publish(AbstractNotification)} throws a nullpointer exception
     * if a mandatory parameter is {@code null}.
     */
    @Test
    public void testThatSendThrowsOnMissingMandatoryParameter() {
        final MockProducer<String, JsonObject> mockProducer = newMockProducer();
        final KafkaBasedNotificationSender sender = newSender(mockProducer);

        assertThrows(NullPointerException.class, () -> sender.publish(null));

    }

    private MockProducer<String, JsonObject> newMockProducer() {
        return new MockProducer<>(true, new StringSerializer(), new JsonObjectSerializer());
    }

    private CachingKafkaProducerFactory<String, JsonObject> newProducerFactory(
            final MockProducer<String, JsonObject> mockProducer) {
        return CachingKafkaProducerFactory
                .testFactory((n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
    }

    private KafkaBasedNotificationSender newSender(final MockProducer<String, JsonObject> mockProducer) {
        final CachingKafkaProducerFactory<String, JsonObject> factory = newProducerFactory(mockProducer);
        return new KafkaBasedNotificationSender(factory, config);
    }

}
