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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.kafka.common.MetricName;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.telemetry.kafka.KafkaBasedEventSender;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.tests.EnabledIfMessagingSystemConfigured;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;
import io.vertx.kafka.client.producer.KafkaProducer;

/**
 * Test cases verifying the behavior of {@link KafkaBasedEventSender}.
 * <p>
 * To run this on a specific Kafka cluster instance, set the
 * {@value IntegrationTestSupport#PROPERTY_DOWNSTREAM_BOOTSTRAP_SERVERS} system property,
 * e.g. <code>-Ddownstream.bootstrap.servers="PLAINTEXT://localhost:9092"</code>.
 */
@ExtendWith(VertxExtension.class)
@EnabledIfMessagingSystemConfigured(type = MessagingType.kafka)
public class KafkaBasedEventSenderIT {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBasedEventSenderIT.class);

    private static final short REPLICATION_FACTOR = 1;

    private static Vertx vertx;
    private static KafkaAdminClient adminClient;
    private static List<String> topicsToDeleteAfterTests;

    private KafkaBasedEventSender KafkaBasedEventSender;
    private CachingKafkaProducerFactory<String, Buffer> producerFactory;

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
    }

    /**
     * Creates the event sender.
     */
    @BeforeEach
    void initProducer() {
        producerFactory = CachingKafkaProducerFactory.nonSharedFactory(vertx);
        KafkaBasedEventSender = new KafkaBasedEventSender(vertx, producerFactory,
                IntegrationTestSupport.getKafkaProducerConfig(), false, NoopTracerFactory.create());
    }

    /**
     * Cleans up fixture.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutDown(final VertxTestContext ctx) {
        final Promise<Void> topicsDeletedPromise = Promise.promise();
        adminClient.deleteTopics(topicsToDeleteAfterTests, topicsDeletedPromise);
        topicsDeletedPromise.future()
                .recover(thr -> {
                    LOG.info("error deleting topics", thr);
                    return Future.succeededFuture();
                })
                .onComplete(ar -> {
                    topicsToDeleteAfterTests.clear();
                    topicsToDeleteAfterTests = null;
                    adminClient.close();
                    adminClient = null;
                    vertx.close();
                    vertx = null;
                })
                .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Stops the event sender created during the test.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    void closeProducer(final VertxTestContext ctx) {
        if (KafkaBasedEventSender != null) {
            KafkaBasedEventSender.stop().onComplete(ctx.succeedingThenComplete());
        }
    }

    /**
     * Verifies that the event sender causes topic-specific metrics in its underlying Kafka producer to be removed
     * when a tenant-deletion notification is sent via the vert.x event bus.
     *
     * @param ctx The vert.x text context.
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testProducerTopicMetricsGetRemovedOnTenantDeletion(final VertxTestContext ctx) throws InterruptedException {

        final String tenantId = "MetricsRemovalTestTenant";
        final String tenantTopicName = new HonoTopic(HonoTopic.Type.EVENT, tenantId).toString();

        final VertxTestContext setup = new VertxTestContext();
        createTopic(tenantTopicName)
                .compose(v -> KafkaBasedEventSender.start())
                .compose(v -> sendEvent(tenantId, "myDeviceId", "test"))
                .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }
        // GIVEN a started event sender that has already sent an event message
        // and the underlying Kafka producer having filled corresponding topic-specific metrics
        final var producerOptional = producerFactory.getProducer(EventConstants.EVENT_ENDPOINT);
        ctx.verify(() -> {
            assertThat(producerOptional.isPresent()).isTrue();
            assertThat(getTopicRelatedMetrics(producerOptional.get(), tenantTopicName)).isNotEmpty();
        });

        // WHEN sending a tenant-deleted notification for that tenant
        NotificationEventBusSupport.getNotificationSender(vertx)
                .handle(new TenantChangeNotification(LifecycleChange.DELETE, tenantId, Instant.now(), false, false));

        vertx.runOnContext(v -> {
            // THEN the metrics of the underlying producer don't contain any metrics regarding that topic
            ctx.verify(() -> assertThat(getTopicRelatedMetrics(producerOptional.get(), tenantTopicName)).isEmpty());
            ctx.completeNow();
        });
    }

    private List<MetricName> getTopicRelatedMetrics(final KafkaProducer<String, Buffer> kafkaProducer,
            final String topicName) {
        return kafkaProducer.unwrap().metrics().keySet().stream()
                .filter(metricName -> metricName.tags().containsValue(topicName))
                .collect(Collectors.toList());
    }

    private Future<Void> sendEvent(final String tenantId, final String deviceId, final String messagePayload) {
        return KafkaBasedEventSender.sendEvent(TenantObject.from(tenantId), new RegistrationAssertion(deviceId),
                "text/plain", Buffer.buffer(messagePayload), Map.of(), null);
    }

    private static Future<Void> createTopic(final String topicName) {
        return createTopics(List.of(topicName), 1, Map.of());
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
}

