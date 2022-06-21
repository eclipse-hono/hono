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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.Map;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
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

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
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
    private static final boolean INVALIDATE_CACHE_ON_UPDATE = false;

    private final NotificationKafkaProducerConfigProperties config = new NotificationKafkaProducerConfigProperties();
    private final Vertx vertxMock = mock(Vertx.class);

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
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testLifecycle(final VertxTestContext ctx) {
        final var mockProducer = newMockProducer();
        final var factory = newProducerFactory(mockProducer);
        final var sender = new KafkaBasedNotificationSender(factory, config);
        final Promise<Void> readyTracker = Promise.promise();
        sender.addOnKafkaProducerReadyHandler(readyTracker);

        assertThat(factory.getProducer(KafkaBasedNotificationSender.PRODUCER_NAME).isPresent()).isFalse();
        sender.start()
            .compose(ok -> readyTracker.future())
            .compose(ok -> {
                ctx.verify(() -> assertThat(factory.getProducer(KafkaBasedNotificationSender.PRODUCER_NAME).isPresent())
                        .isTrue());
                return sender.stop();
            })
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> assertThat(factory.getProducer(KafkaBasedNotificationSender.PRODUCER_NAME).isPresent()).isFalse());
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the expected Kafka record is created when publishing a {@link TenantChangeNotification}.
     */
    @Test
    public void testProducerRecordForTenantNotification() {

        final var notification = new TenantChangeNotification(CHANGE, TENANT_ID, CREATION_TIME, ENABLED,
                INVALIDATE_CACHE_ON_UPDATE);
        testProducerRecordForNotification(notification, TENANT_ID);
    }

    /**
     * Verifies that the expected Kafka record is created when publishing a {@link DeviceChangeNotification}.
     */
    @Test
    public void testProducerRecordForDeviceNotification() {

        final var notification = new DeviceChangeNotification(CHANGE, TENANT_ID, DEVICE_ID, CREATION_TIME, ENABLED);
        testProducerRecordForNotification(notification, DEVICE_ID);
    }

    /**
     * Verifies that the expected Kafka record is created when publishing a {@link CredentialsChangeNotification}.
     */
    @Test
    public void testProducerRecordForCredentialsNotification() {

        final var notification = new CredentialsChangeNotification(TENANT_ID, DEVICE_ID, CREATION_TIME);
        testProducerRecordForNotification(notification, DEVICE_ID);
    }

    /**
     * Verifies that the expected Kafka record is created when publishing a {@link AllDevicesOfTenantDeletedNotification}.
     */
    @Test
    public void testProducerRecordForAllDevicesOfTenantDeletedNotification() {

        final var notification = new AllDevicesOfTenantDeletedNotification(TENANT_ID, CREATION_TIME);
        testProducerRecordForNotification(notification, TENANT_ID);
    }

    private void testProducerRecordForNotification(
            final AbstractNotification notificationToSend,
            final String expectedRecordKey) {

        final VertxTestContext ctx = new VertxTestContext();

        // GIVEN a sender
        final var mockProducer = newMockProducer();
        final var sender = newSender(mockProducer);

        // WHEN publishing the notification
        sender.publish(notificationToSend)
            .onComplete(ctx.succeeding(v -> {
                // THEN the producer record is created from the given values
                ctx.verify(() -> {
                    final var record = mockProducer.history().get(0);

                    assertThat(record.topic())
                            .isEqualTo(NotificationTopicHelper.getTopicName(notificationToSend.getType()));
                    assertThat(record.key()).isEqualTo(expectedRecordKey);
                    assertThat(record.value().getString(NotificationConstants.JSON_FIELD_TYPE))
                            .isEqualTo(notificationToSend.getType().getTypeName());
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
    public void testPublishFailsWithTheExpectedError(final VertxTestContext ctx) {

        // GIVEN a sender sending a message
        final var mockProducer = newMockProducer(false);
        final Promise<Void> readyTracker = Promise.promise();
        final var sender = newSender(mockProducer);
        sender.addOnKafkaProducerReadyHandler(readyTracker);

        final var result = sender.start()
                .compose(ok -> readyTracker.future())
                .compose(ok -> sender.publish(new TenantChangeNotification(CHANGE, TENANT_ID, CREATION_TIME, ENABLED,
                        INVALIDATE_CACHE_ON_UPDATE)));

        // WHEN the send operation fails
        final RuntimeException expectedError = new RuntimeException("boom");
        mockProducer.errorNext(expectedError);

        result.onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN it fails with the expected error
                    assertThat(t).isInstanceOf(ServerErrorException.class);
                    assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(503);
                    assertThat(t.getCause()).isEqualTo(expectedError);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the constructor throws an NPE if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {
        final var mockProducer = newMockProducer();
        final var factory = newProducerFactory(mockProducer);

        assertThrows(NullPointerException.class, () -> new KafkaBasedNotificationSender(null, config));
        assertThrows(NullPointerException.class, () -> new KafkaBasedNotificationSender(factory, null));

    }

    /**
     * Verifies that {@link KafkaBasedNotificationSender#publish(AbstractNotification)} throws a nullpointer exception
     * if a mandatory parameter is {@code null}.
     */
    @Test
    public void testThatSendThrowsOnMissingMandatoryParameter() {
        final var mockProducer = newMockProducer();
        final var sender = newSender(mockProducer);

        assertThrows(NullPointerException.class, () -> sender.publish(null));

    }

    private MockProducer<String, JsonObject> newMockProducer() {
        return newMockProducer(true);
    }

    private MockProducer<String, JsonObject> newMockProducer(final boolean autoComplete) {
        return new MockProducer<>(autoComplete, new StringSerializer(), new JsonObjectSerializer());
    }

    private CachingKafkaProducerFactory<String, JsonObject> newProducerFactory(
            final MockProducer<String, JsonObject> mockProducer) {
        return CachingKafkaProducerFactory.testFactory(
                vertxMock,
                (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
    }

    private KafkaBasedNotificationSender newSender(final MockProducer<String, JsonObject> mockProducer) {
        final var factory = newProducerFactory(mockProducer);
        return new KafkaBasedNotificationSender(factory, config);
    }

}
