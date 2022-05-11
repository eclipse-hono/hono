/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.tests.registry;

import static org.junit.jupiter.api.Assertions.fail;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.notification.amqp.ProtonBasedNotificationReceiver;
import org.eclipse.hono.client.notification.kafka.KafkaBasedNotificationReceiver;
import org.eclipse.hono.client.notification.kafka.NotificationKafkaConsumerConfigProperties;
import org.eclipse.hono.notification.NotificationReceiver;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.AssumeMessagingSystem;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessagingType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Test cases verifying that the device registry emits tenant/device change notifications
 * by retrieving them via a {@link ProtonBasedNotificationReceiver} or {@link KafkaBasedNotificationReceiver}.
 */
@ExtendWith(VertxExtension.class)
public class DeviceRegistryNotificationsIT {

    private static final String NOTIFICATION_TEST_USER = "notification-test";
    private static final String NOTIFICATION_TEST_PWD = "pw";

    private static Vertx vertx;
    private static IntegrationTestSupport helper;

    private NotificationReceiver receiver;

    /**
     * Sets up vert.x.
     */
    @BeforeAll
    public static void init() {
        vertx = Vertx.vertx();
    }

    /**
     * Creates a HTTP client for accessing the device registry (for registering tenants and devices).
     *
     * @param ctx The Vert.x test context.
     */
    @BeforeEach
    public void setUp(final VertxTestContext ctx) {

        helper = new IntegrationTestSupport(vertx);
        helper.init().onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Shuts down the client connected to the messaging network and stops the receiver.
     *
     * @param ctx The Vert.x test context.
     */
    @AfterEach
    public void shutdown(final VertxTestContext ctx) {
        final Future<Void> receiverStopFuture = Optional.ofNullable(receiver)
                .map(Lifecycle::stop)
                .orElseGet(Future::succeededFuture);
        CompositeFuture.join(receiverStopFuture, helper.disconnect())
                        .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Deletes all temporary objects from the Device Registry which
     * have been created during the last test execution.
     *
     * @param ctx The Vert.x test context.
     */
    @AfterEach
    public void deleteObjects(final VertxTestContext ctx) {
        helper.deleteObjects(ctx);
    }

    /**
     * Verifies that AMQP-based notifications for adding tenant/device/credentials resources are received by a local
     * client.
     *
     * @throws InterruptedException if test is interrupted while running.
     */
    @Test
    @AssumeMessagingSystem(type = MessagingType.amqp)
    public void testReceiveNotificationViaAmqp() throws InterruptedException {

        final ClientConfigProperties messagingNetworkProperties = IntegrationTestSupport.getMessagingNetworkProperties();
        // use user that may open receiver links on "notification/*" addresses
        messagingNetworkProperties.setUsername(NOTIFICATION_TEST_USER);
        messagingNetworkProperties.setPassword(NOTIFICATION_TEST_PWD);
        receiver = new ProtonBasedNotificationReceiver(
                HonoConnection.newConnection(vertx, messagingNetworkProperties));
        testReceiveNotification(() -> receiver.start());
    }

    /**
     * Verifies that Kafka-based notifications for adding tenant/device/credentials resources are received by a local
     * client.
     *
     * @throws InterruptedException if test is interrupted while running.
     */
    @Test
    @AssumeMessagingSystem(type = MessagingType.kafka)
    public void testReceiveNotificationViaKafka() throws InterruptedException {

        final var notificationConsumerConfig = new NotificationKafkaConsumerConfigProperties();
        notificationConsumerConfig.setConsumerConfig(IntegrationTestSupport.getKafkaConsumerConfig()
                .getConsumerConfig("notification-receiver"));
        final var kafkaReceiver = new KafkaBasedNotificationReceiver(vertx, notificationConsumerConfig);
        final Promise<Void> readyTracker = Promise.promise();
        kafkaReceiver.addOnKafkaConsumerReadyHandler(readyTracker);
        receiver = kafkaReceiver;
        testReceiveNotification(() -> kafkaReceiver.start().compose(ok -> readyTracker.future()));
    }

    private void testReceiveNotification(final Supplier<Future<Void>> startUpAction) throws InterruptedException {

        final VertxTestContext ctx = new VertxTestContext();
        final Checkpoint notificationsReceivedCheckpoint = ctx.checkpoint(3);

        final VertxTestContext setup = new VertxTestContext();
        receiver.registerConsumer(TenantChangeNotification.TYPE,
                notification -> {
                    ctx.verify(() -> {
                        assertThat(notification).isInstanceOf(TenantChangeNotification.class);
                    });
                    notificationsReceivedCheckpoint.flag();
                });
        receiver.registerConsumer(DeviceChangeNotification.TYPE,
                notification -> {
                    ctx.verify(() -> {
                        assertThat(notification).isInstanceOf(DeviceChangeNotification.class);
                    });
                    notificationsReceivedCheckpoint.flag();
                });
        receiver.registerConsumer(CredentialsChangeNotification.TYPE,
                notification -> {
                    ctx.verify(() -> {
                        assertThat(notification).isInstanceOf(CredentialsChangeNotification.class);
                    });
                    notificationsReceivedCheckpoint.flag();
                });
        startUpAction.get().onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            fail(setup.causeOfFailure());
            return;
        }

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";

        helper.registry
                .addDeviceForTenant(tenantId, new Tenant(), deviceId, password)
                .onFailure(ctx::failNow);

        assertWithMessage("notifications received in 5s")
                .that(ctx.awaitCompletion(5, TimeUnit.SECONDS))
                .isTrue();
        if (ctx.failed()) {
            fail(ctx.causeOfFailure());
        }
    }
}

