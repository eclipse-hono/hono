/*******************************************************************************
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.tests.commandrouter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.security.auth.x500.X500Principal;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.kafka.consumer.AsyncHandlingAutoCommitKafkaConsumer;
import org.eclipse.hono.client.kafka.consumer.HonoKafkaConsumer;
import org.eclipse.hono.client.kafka.consumer.MessagingKafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.metrics.NoopKafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.commandrouter.CommandRouterMetrics;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.commandrouter.impl.kafka.KafkaBasedCommandConsumerFactoryImpl;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.tests.EnabledIfMessagingSystemConfigured;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Timer;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

/**
 * Test cases verifying the behavior of {@link KafkaBasedCommandConsumerFactoryImpl}.
 * <p>
 * To run this on a specific Kafka cluster instance, set the
 * {@value IntegrationTestSupport#PROPERTY_DOWNSTREAM_BOOTSTRAP_SERVERS} system property,
 * e.g. <code>-Ddownstream.bootstrap.servers="PLAINTEXT://localhost:9092"</code>.
 */
@ExtendWith(VertxExtension.class)
@EnabledIfMessagingSystemConfigured(type = MessagingType.kafka)
public class KafkaBasedCommandConsumerFactoryImplIT {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBasedCommandConsumerFactoryImplIT.class);

    private static Vertx vertx;
    private static KafkaAdminClient adminClient;
    private static KafkaProducer<String, Buffer> kafkaProducer;
    private static final Set<String> topicsToDeleteAfterTest = new HashSet<>();

    private final List<Lifecycle> componentsToStopAfterTest = new ArrayList<>();
    private final String adapterInstanceId = "myAdapterInstanceId_" + UUID.randomUUID();
    private final String commandRouterGroupId = "cmdRouter_" + UUID.randomUUID();

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
     * Logs the current test's display name.
     *
     * @param testInfo The test meta data.
     */
    @BeforeEach
    public void logTestName(final TestInfo testInfo) {
        LOG.info("running test {}", testInfo.getDisplayName());
    }

    /**
     * Closes and removes resources created during the test.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    void cleanupAfterTest(final VertxTestContext ctx) {
        final List<Future<Void>> stopFutures = componentsToStopAfterTest.stream()
            .map(component -> component.stop()
                    .onSuccess(ok -> LOG.info("stopped component of type {}", component.getClass().getName()))
                    .onFailure(t -> LOG.info("failed to stop component of type {}", component.getClass().getName(), t)))
            .collect(Collectors.toList());
        componentsToStopAfterTest.clear();
        Future.all(stopFutures)
                .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Cleans up fixture.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutDown(final VertxTestContext ctx) {

        final Promise<Void> topicsDeletedPromise = Promise.promise();
        // delete topics with a delay - overall test run might include command router tests,
        // in which case the command router kafka consumer will try to commit offsets for these topics once,
        // and the topic must still exist then, otherwise there will be errors and delays. So wait until the
        // commits have happened.
        vertx.setTimer(AsyncHandlingAutoCommitKafkaConsumer.DEFAULT_COMMIT_INTERVAL.toMillis(), tid -> {
            final Set<String> topicsToDelete = new HashSet<>(topicsToDeleteAfterTest);
            topicsToDeleteAfterTest.clear();
            adminClient.deleteTopics(new ArrayList<>(topicsToDelete))
                .onFailure(thr -> LOG.info("error deleting test topics", thr))
                .onSuccess(v -> LOG.debug("done deleting test topics (with {}s delay)",
                        AsyncHandlingAutoCommitKafkaConsumer.DEFAULT_COMMIT_INTERVAL.toSeconds()))
                .onComplete(topicsDeletedPromise);
        });

        topicsDeletedPromise.future()
            .compose(ok -> Future.all(adminClient.close(), kafkaProducer.close()))
            .compose(ok -> vertx.close())
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that records, published on the tenant-specific Kafka command topic, get received by
     * the consumer created by the factory and get forwarded on the internal command topic in the
     * same order they were published.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testCommandsGetForwardedInIncomingOrder(final VertxTestContext ctx) throws InterruptedException {

        final String tenantId = "tenant_" + UUID.randomUUID();
        final VertxTestContext setup = new VertxTestContext();

        final int numTestCommands = 10;
        final List<KafkaConsumerRecord<String, Buffer>> receivedRecords = new ArrayList<>();
        final Promise<Void> allRecordsReceivedPromise = Promise.promise();
        final List<String> receivedCommandSubjects = new ArrayList<>();
        final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler = record -> {
            receivedRecords.add(record);
            LOG.trace("received {}", record);
            receivedCommandSubjects.add(KafkaRecordHelper.getSubject(record.headers()).orElse(""));
            if (receivedRecords.size() == numTestCommands) {
                allRecordsReceivedPromise.tryComplete();
            }
        };
        final Deque<Promise<Void>> completionPromisesQueue = new LinkedList<>();
        // don't let getting the target adapter instance finish immediately
        // - let the futures complete in the reverse order
        final Supplier<Future<Void>> targetAdapterInstanceGetterCompletionFutureSupplier = () -> {
            final Promise<Void> resultPromise = Promise.promise();
            completionPromisesQueue.addFirst(resultPromise);
            // complete all promises in reverse order when processing the last command
            if (completionPromisesQueue.size() == numTestCommands) {
                completionPromisesQueue.forEach(Promise::complete);
            }
            return resultPromise.future();
        };

        final Context vertxContext = vertx.getOrCreateContext();
        vertxContext.runOnContext(v0 -> {
            final Promise<Void> consumerTracker = Promise.promise();
            final var internalConsumer = getInternalCommandConsumer(recordHandler);
            internalConsumer.addOnKafkaConsumerReadyHandler(consumerTracker);
            final Promise<Void> factoryTracker = Promise.promise();
            final var consumerFactory = getKafkaBasedCommandConsumerFactory(
                    targetAdapterInstanceGetterCompletionFutureSupplier, tenantId);
            consumerFactory.addOnFactoryReadyHandler(factoryTracker);
            Future.all(
                    internalConsumer.start().compose(ok -> consumerTracker.future()),
                    consumerFactory.start().compose(ok -> factoryTracker.future()))
                .compose(f -> createCommandConsumer(tenantId, consumerFactory))
                .onComplete(setup.succeedingThenComplete());
        });

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }
        LOG.debug("command consumer started");

        final List<String> sentCommandSubjects = new ArrayList<>();
        IntStream.range(0, numTestCommands).forEach(i -> {
            final String subject = "cmd_" + i;
            sentCommandSubjects.add(subject);
            sendOneWayCommand(tenantId, "myDeviceId", subject);
        });

        final long timerId = vertx.setTimer(8000, tid -> {
            LOG.info("received records:{}{}", System.lineSeparator(),
                    receivedRecords.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("," + System.lineSeparator())));
            allRecordsReceivedPromise.tryFail(String.format("only received %d out of %d expected messages after 8s",
                    receivedRecords.size(), numTestCommands));
        });
        allRecordsReceivedPromise.future().onComplete(ctx.succeeding(v -> {
            vertx.cancelTimer(timerId);
            ctx.verify(() -> {
                assertThat(receivedCommandSubjects).isEqualTo(sentCommandSubjects);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that records, published on the tenant-specific Kafka command topic, get received
     * and forwarded by consumers created by factory instances even if one factory and its contained
     * consumer gets closed in the middle of processing some of the commands.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testCommandsGetForwardedIfOneConsumerInstanceGetsClosed(final VertxTestContext ctx) throws InterruptedException {

        final String tenantId = "tenant_" + UUID.randomUUID();
        final VertxTestContext setup = new VertxTestContext();

        // Scenario to test:
        // - first command gets sent, forwarded and received without any imposed delay
        // - second command gets sent, received by the factory consumer instance; processing gets blocked
        //   while trying to get the target adapter instance
        // - for the rest of the commands, retrieval of the target adapter instance is successful, but they won't
        //   get forwarded until processing of the second command is finished
        // - now the factory consumer gets closed and a new factory/consumer gets started; at that point
        //   also the processing of the second command gets finished
        //
        // Expected outcome:
        // - processing of the second command and all following commands by the first consumer gets aborted, so that
        //   these commands don't get forwarded on the internal command topic
        // - instead, the second consumer takes over at the offset of the first command (position must have been committed
        //   when closing the first consumer) and processes and forwards all commands starting with the second command

        final int numTestCommands = 10;
        final List<KafkaConsumerRecord<String, Buffer>> receivedRecords = new ArrayList<>();
        final Promise<Void> firstRecordReceivedPromise = Promise.promise();
        final Promise<Void> allRecordsReceivedPromise = Promise.promise();
        final List<String> receivedCommandSubjects = new ArrayList<>();
        final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler = record -> {
            receivedRecords.add(record);
            LOG.trace("received {}", record);
            receivedCommandSubjects.add(KafkaRecordHelper.getSubject(record.headers()).orElse(""));
            if (receivedRecords.size() == 1) {
                firstRecordReceivedPromise.complete();
            }
            if (receivedRecords.size() == numTestCommands) {
                allRecordsReceivedPromise.tryComplete();
            }
        };
        final Promise<Void> firstConsumerAllGetAdapterInstanceInvocationsDone = Promise.promise();
        final Deque<Promise<Void>> firstConsumerGetAdapterInstancePromisesQueue = new LinkedList<>();
        // don't let getting the target adapter instance finish immediately
        final Supplier<Future<Void>> firstConsumerGetAdapterInstanceSupplier = () -> {
            final Promise<Void> resultPromise = Promise.promise();
            firstConsumerGetAdapterInstancePromisesQueue.addFirst(resultPromise);
            // don't complete the future for the second command here yet
            if (firstConsumerGetAdapterInstancePromisesQueue.size() != 2) {
                resultPromise.complete();
            }
            if (firstConsumerGetAdapterInstancePromisesQueue.size() == numTestCommands) {
                firstConsumerAllGetAdapterInstanceInvocationsDone.complete();
            }
            return resultPromise.future();
        };

        final AtomicReference<KafkaBasedCommandConsumerFactoryImpl> consumerFactory1Ref = new AtomicReference<>();
        final Context vertxContext = vertx.getOrCreateContext();
        vertxContext.runOnContext(v0 -> {
            final Promise<Void> consumerTracker = Promise.promise();
            final var internalConsumer = getInternalCommandConsumer(recordHandler);
            internalConsumer.addOnKafkaConsumerReadyHandler(consumerTracker);
            final Promise<Void> factoryTracker = Promise.promise();
            final var consumerFactory1 = getKafkaBasedCommandConsumerFactory(
                    firstConsumerGetAdapterInstanceSupplier, tenantId);
            consumerFactory1.addOnFactoryReadyHandler(factoryTracker);
            consumerFactory1Ref.set(consumerFactory1);
            Future.all(
                    internalConsumer.start().compose(ok -> consumerTracker.future()),
                    consumerFactory1.start().compose(ok -> factoryTracker.future()))
                .compose(f -> createCommandConsumer(tenantId, consumerFactory1))
                .onComplete(setup.succeedingThenComplete());
        });

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }
        LOG.debug("command consumer started");

        final List<String> sentCommandSubjects = new ArrayList<>();
        IntStream.range(0, numTestCommands).forEach(i -> {
            final String subject = "cmd_" + i;
            sentCommandSubjects.add(subject);
            sendOneWayCommand(tenantId, "myDeviceId", subject);
        });

        final AtomicInteger secondConsumerGetAdapterInstanceInvocations = new AtomicInteger();
        // wait for first record on internal topic to have been received ...
        Future.join(
                firstConsumerAllGetAdapterInstanceInvocationsDone.future(),
                firstRecordReceivedPromise.future())
            .compose(v -> {
                // ... and wait some more, making sure that the offset of the first record has been committed
                final Promise<Void> delayPromise = Promise.promise();
                vertx.setTimer(500, tid -> delayPromise.complete());
                return delayPromise.future();
            })
            .compose(v -> {
                LOG.info("stopping first consumer factory");
                return consumerFactory1Ref.get().stop();
            })
            .compose(stopped -> {
                LOG.info("factory stopped");
                // no delay on getting the target adapter instance added here
                final Promise<Void> readyTracker = Promise.promise();
                final var consumerFactory2 = getKafkaBasedCommandConsumerFactory(() -> {
                    secondConsumerGetAdapterInstanceInvocations.incrementAndGet();
                    return Future.succeededFuture();
                }, tenantId);
                consumerFactory2.addOnFactoryReadyHandler(readyTracker);
                return consumerFactory2.start()
                        .compose(started -> readyTracker.future())
                        .map(consumerFactory2);
            })
            .compose(consumerFactory2 -> {
                LOG.info("creating command consumer in new consumer factory");
                return createCommandConsumer(tenantId, consumerFactory2);
            })
            .onComplete(ctx.succeeding(ok -> {
                LOG.debug("consumer created");
                firstConsumerGetAdapterInstancePromisesQueue.forEach(Promise::tryComplete);
            }));

        final long timerId = vertx.setTimer(8000, tid -> {
            LOG.info("received records:\n{}",
                    receivedRecords.stream().map(Object::toString).collect(Collectors.joining(",\n")));
            allRecordsReceivedPromise.tryFail(String.format("only received %d out of %d expected messages after 8s",
                    receivedRecords.size(), numTestCommands));
        });
        allRecordsReceivedPromise.future().onComplete(ctx.succeeding(v -> {
            vertx.cancelTimer(timerId);
            ctx.verify(() -> {
                assertThat(receivedCommandSubjects).isEqualTo(sentCommandSubjects);
                // all but the first command should have been processed by the second consumer
                assertThat(secondConsumerGetAdapterInstanceInvocations.get()).isEqualTo(numTestCommands - 1);
            });
            LOG.info("all records received");
            ctx.completeNow();
        }));
    }

    private Future<Void> createCommandConsumer(
            final String tenantId,
            final CommandConsumerFactory consumerFactory) {

        topicsToDeleteAfterTest.add(new HonoTopic(HonoTopic.Type.COMMAND, tenantId).toString());
        return consumerFactory.createCommandConsumer(tenantId, null);
    }

    private HonoKafkaConsumer<Buffer> getInternalCommandConsumer(
            final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler) {

        final Map<String, String> consumerConfig = IntegrationTestSupport.getKafkaConsumerConfig()
                .getConsumerConfig("internal_cmd_consumer_test");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());

        final String topic = new HonoTopic(HonoTopic.Type.COMMAND_INTERNAL, adapterInstanceId).toString();
        final var consumer = new HonoKafkaConsumer<Buffer>(vertx, Set.of(topic), recordHandler, consumerConfig);
        componentsToStopAfterTest.add(consumer);
        topicsToDeleteAfterTest.add(topic);
        return consumer;
    }

    private KafkaBasedCommandConsumerFactoryImpl getKafkaBasedCommandConsumerFactory(
            final Supplier<Future<Void>> targetAdapterInstanceGetterCompletionFutureSupplier,
            final String tenantToHandleCommandsFor) {

        final KafkaProducerFactory<String, Buffer> producerFactory = CachingKafkaProducerFactory.sharedFactory(vertx);
        final TenantClient tenantClient = getTenantClient();
        final CommandTargetMapper commandTargetMapper = new CommandTargetMapper() {
            @Override
            public Future<JsonObject> getTargetGatewayAndAdapterInstance(
                    final String tenantId,
                    final String deviceId,
                    final SpanContext context) {

                final JsonObject jsonObject = new JsonObject();
                jsonObject.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID, adapterInstanceId);
                jsonObject.put(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
                if (!tenantId.equals(tenantToHandleCommandsFor)) {
                    return Future.failedFuture("ignoring command for other tenant " + tenantId);
                }
                if (targetAdapterInstanceGetterCompletionFutureSupplier == null) {
                    return Future.succeededFuture(jsonObject);
                }
                return targetAdapterInstanceGetterCompletionFutureSupplier.get().map(jsonObject);
            }
        };
        final Span span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);

        final var kafkaConsumerConfig = new MessagingKafkaConsumerConfigProperties();
        kafkaConsumerConfig.setConsumerConfig(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, IntegrationTestSupport.DOWNSTREAM_BOOTSTRAP_SERVERS,
                ConsumerConfig.GROUP_ID_CONFIG, commandRouterGroupId));
        final CommandRouterMetrics metrics = mock(CommandRouterMetrics.class);
        when(metrics.startTimer()).thenReturn(Timer.start());

        final var kafkaBasedCommandConsumerFactoryImpl = new KafkaBasedCommandConsumerFactoryImpl(
                vertx,
                tenantClient,
                commandTargetMapper,
                producerFactory,
                IntegrationTestSupport.getKafkaProducerConfig(),
                IntegrationTestSupport.getKafkaProducerConfig(),
                kafkaConsumerConfig,
                metrics,
                NoopKafkaClientMetricsSupport.INSTANCE,
                tracer);
        componentsToStopAfterTest.add(kafkaBasedCommandConsumerFactoryImpl);
        return kafkaBasedCommandConsumerFactoryImpl;
    }

    private static void sendOneWayCommand(final String tenantId, final String deviceId, final String subject) {
        // only sending one-way commands here since commands are possibly also received by the Command Router instance
        // running as part of the integration test environment. For a request/response command, an error command response
        // message would always be published there ("tenant/device not found"), interfering with any command response retrieval here
        kafkaProducer.send(getOneWayCommandRecord(tenantId, deviceId, subject), ar -> {
            if (ar.succeeded()) {
                LOG.debug("sent command {}; metadata {}", subject, ar.result().toJson());
            } else {
                LOG.error("error sending command {}", subject, ar.cause());
            }
        });
    }

    private static KafkaProducerRecord<String, Buffer> getOneWayCommandRecord(final String tenantId,
            final String deviceId, final String subject) {
        final List<KafkaHeader> headers = List.of(
                KafkaRecordHelper.createDeviceIdHeader(deviceId),
                KafkaRecordHelper.createSubjectHeader(subject)
        );
        final String commandTopic = new HonoTopic(HonoTopic.Type.COMMAND, tenantId).toString();
        final KafkaProducerRecord<String, Buffer> record = KafkaProducerRecord.create(
                commandTopic, deviceId, Buffer.buffer(subject + "_payload"));
        record.addHeaders(headers);
        return record;
    }

    private TenantClient getTenantClient() {
        return new TenantClient() {
            @Override
            public Future<TenantObject> get(final String tenantId, final SpanContext context) {
                return Future.succeededFuture(TenantObject.from(tenantId));
            }

            @Override
            public Future<TenantObject> get(final X500Principal subjectDn, final SpanContext context) {
                return Future.failedFuture("unsupported");
            }

            @Override
            public Future<Void> start() {
                return Future.succeededFuture();
            }

            @Override
            public Future<Void> stop() {
                return Future.succeededFuture();
            }
        };
    }
}

