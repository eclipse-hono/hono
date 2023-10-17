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
package org.eclipse.hono.commandrouter.impl.kafka;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommandResponseSender;
import org.eclipse.hono.client.command.kafka.KafkaBasedInternalCommandSender;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaClientFactory;
import org.eclipse.hono.client.kafka.consumer.AsyncHandlingAutoCommitKafkaConsumer;
import org.eclipse.hono.client.kafka.consumer.MessagingKafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.metrics.KafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.util.ServiceClient;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.commandrouter.CommandRouterMetrics;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.LifecycleStatus;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.kafka.client.common.impl.Helper;

/**
 * A factory for creating clients for the <em>Kafka messaging infrastructure</em> to receive commands.
 * <p>
 * This factory uses a wild-card based topic pattern to subscribe for commands, which receives the command
 * messages irrespective of the tenants.
 * <p>
 * Command messages are first received by the Kafka consumer on the tenant-specific topic. It is then determined
 * which protocol adapter instance can handle the command. The command is then forwarded to the Kafka cluster on
 * a topic containing that adapter instance id.
 */
public class KafkaBasedCommandConsumerFactoryImpl implements CommandConsumerFactory, ServiceClient {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaBasedCommandConsumerFactoryImpl.class);

    private static final Pattern COMMANDS_TOPIC_PATTERN = Pattern
            .compile(Pattern.quote(HonoTopic.Type.COMMAND.prefix) + ".*");
    private static final String DEFAULT_GROUP_ID = "cmd-router-group";

    private final Vertx vertx;
    private final TenantClient tenantClient;
    private final CommandTargetMapper commandTargetMapper;
    private final MessagingKafkaConsumerConfigProperties kafkaConsumerConfig;
    private final Tracer tracer;
    private final CommandRouterMetrics metrics;
    private final KafkaBasedInternalCommandSender internalCommandSender;
    private final KafkaBasedCommandResponseSender kafkaBasedCommandResponseSender;
    private final KafkaClientMetricsSupport kafkaClientMetricsSupport;
    private final LifecycleStatus lifecycleStatus = new LifecycleStatus();
    private KafkaBasedMappingAndDelegatingCommandHandler commandHandler;
    private AsyncHandlingAutoCommitKafkaConsumer<Buffer> kafkaConsumer;

    /**
     * Creates a new factory to process commands via the Kafka cluster.
     *
     * @param vertx The Vert.x instance to use.
     * @param tenantClient The Tenant service client.
     * @param commandTargetMapper The component for mapping an incoming command to the gateway (if applicable) and
     *            protocol adapter instance that can handle it. Note that no initialization of this factory will be done
     *            here, that is supposed to be done by the calling method.
     * @param kafkaProducerFactory The producer factory for creating Kafka producers for sending messages.
     * @param internalCommandProducerConfig The configuration for producing messages on the command-internal topic.
     * @param commandResponseProducerConfig The configuration for producing command-response messages.
     * @param kafkaConsumerConfig The Kafka consumer configuration.
     * @param metrics The component to use for reporting metrics.
     * @param kafkaClientMetricsSupport The Kafka metrics support.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedCommandConsumerFactoryImpl(
            final Vertx vertx,
            final TenantClient tenantClient,
            final CommandTargetMapper commandTargetMapper,
            final KafkaProducerFactory<String, Buffer> kafkaProducerFactory,
            final MessagingKafkaProducerConfigProperties internalCommandProducerConfig,
            final MessagingKafkaProducerConfigProperties commandResponseProducerConfig,
            final MessagingKafkaConsumerConfigProperties kafkaConsumerConfig,
            final CommandRouterMetrics metrics,
            final KafkaClientMetricsSupport kafkaClientMetricsSupport,
            final Tracer tracer) {

        this.vertx = Objects.requireNonNull(vertx);
        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.commandTargetMapper = Objects.requireNonNull(commandTargetMapper);
        Objects.requireNonNull(kafkaProducerFactory);
        Objects.requireNonNull(internalCommandProducerConfig);
        Objects.requireNonNull(commandResponseProducerConfig);
        this.kafkaConsumerConfig = Objects.requireNonNull(kafkaConsumerConfig);
        this.metrics = Objects.requireNonNull(metrics);
        this.kafkaClientMetricsSupport = Objects.requireNonNull(kafkaClientMetricsSupport);
        this.tracer = Objects.requireNonNull(tracer);

        internalCommandSender = new KafkaBasedInternalCommandSender(
                kafkaProducerFactory,
                internalCommandProducerConfig,
                tracer);
        kafkaBasedCommandResponseSender = new KafkaBasedCommandResponseSender(
                vertx,
                kafkaProducerFactory,
                commandResponseProducerConfig,
                tracer);
    }

    /**
     * Adds a handler to be invoked with a succeeded future once this factory is ready to be used.
     *
     * @param handler The handler to invoke. The handler will never be invoked with a failed future.
     */
    public final void addOnFactoryReadyHandler(final Handler<AsyncResult<Void>> handler) {
        if (handler != null) {
            lifecycleStatus.addOnStartedHandler(handler);
        }
    }

    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        internalCommandSender.registerLivenessChecks(livenessHandler);
        kafkaBasedCommandResponseSender.registerLivenessChecks(livenessHandler);
    }

    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        internalCommandSender.registerReadinessChecks(readinessHandler);
        kafkaBasedCommandResponseSender.registerReadinessChecks(readinessHandler);
        readinessHandler.register(
                "command-consumer-factory-kafka-consumer-%s".formatted(UUID.randomUUID()),
                status -> {
                    if (kafkaConsumer == null) {
                        // this factory has not been started yet
                        status.tryComplete(Status.KO());
                    } else {
                        final var response = kafkaConsumer.checkReadiness();
                        status.tryComplete(new Status().setOk(response.getStatus() == HealthCheckResponse.Status.UP));
                    }
                });
    }

    @Override
    public MessagingType getMessagingType() {
        return MessagingType.kafka;
    }

    /**
     * {@inheritDoc}
     *
     * @return The combined outcome of starting the Kafka consumer and the command handler.
     */
    @Override
    public Future<Void> start() {
        if (lifecycleStatus.isStarting()) {
            return Future.succeededFuture();
        } else if (!lifecycleStatus.setStarting()) {
            return Future.failedFuture(new IllegalStateException("factory is already started/stopping"));
        }
        if (Vertx.currentContext() == null) {
            return Future.failedFuture(new IllegalStateException("factory must be started in a Vert.x context"));
        }
        final var commandQueue = new KafkaCommandProcessingQueue(vertx);
        final Promise<Void> internalCommandSenderTracker = Promise.promise();
        internalCommandSender.addOnKafkaProducerReadyHandler(internalCommandSenderTracker);
        final Promise<Void> commandResponseSenderTracker = Promise.promise();
        kafkaBasedCommandResponseSender.addOnKafkaProducerReadyHandler(commandResponseSenderTracker);
        commandHandler = new KafkaBasedMappingAndDelegatingCommandHandler(
                vertx,
                tenantClient,
                commandQueue,
                commandTargetMapper,
                internalCommandSender,
                kafkaBasedCommandResponseSender,
                metrics,
                tracer);

        final Map<String, String> consumerConfig = kafkaConsumerConfig.getConsumerConfig("command");
        Optional.ofNullable(consumerConfig.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, DEFAULT_GROUP_ID))
                .ifPresentOrElse(
                        groupId -> LOG.info("using explicitly configured consumer group id [{}]", groupId),
                        () -> LOG.debug("using default consumer group id [{}]", DEFAULT_GROUP_ID));
        consumerConfig.putIfAbsent(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, CooperativeStickyAssignor.class.getName());
        consumerConfig.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        final Promise<Void> consumerTracker = Promise.promise();
        consumerTracker.future().onSuccess(v -> {
            registerTenantCreationListener();
        });
        kafkaConsumer = new AsyncHandlingAutoCommitKafkaConsumer<>(
                vertx,
                COMMANDS_TOPIC_PATTERN,
                commandHandler::mapAndDelegateIncomingCommandMessage,
                consumerConfig);
        kafkaConsumer.addOnKafkaConsumerReadyHandler(consumerTracker);
        kafkaConsumer.setPollTimeout(Duration.ofMillis(kafkaConsumerConfig.getPollTimeout()));
        kafkaConsumer.setConsumerCreationRetriesTimeout(KafkaClientFactory.UNLIMITED_RETRIES_DURATION);
        kafkaConsumer.setMetricsSupport(kafkaClientMetricsSupport);
        kafkaConsumer.setOnRebalanceDoneHandler(
                partitions -> commandQueue.setCurrentlyHandledPartitions(Helper.to(partitions)));
        kafkaConsumer.setOnPartitionsLostHandler(
                partitions -> commandQueue.setRevokedPartitions(Helper.to(partitions)));

        Future.all(
                internalCommandSenderTracker.future(),
                commandResponseSenderTracker.future(),
                consumerTracker.future())
            .onSuccess(ok -> lifecycleStatus.setStarted());

        return Future.all(commandHandler.start(), kafkaConsumer.start()).mapEmpty();
    }

    private void registerTenantCreationListener() {
        NotificationEventBusSupport.registerConsumer(vertx, TenantChangeNotification.TYPE,
                notification -> {
                    if (lifecycleStatus.isStarted() && LifecycleChange.CREATE == notification.getChange()
                            && notification.isTenantEnabled()) {
                        // Optional optimization:
                        // Prepare Kafka consumer to receive commands for devices of a newly created tenant.
                        // If not done here, this preparation is done in the "createCommandConsumer" method below.
                        // Doing it here will speed up the first invocation of "createCommandConsumer" for this tenant.
                        final String tenantId = notification.getTenantId();
                        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, tenantId).toString();
                        kafkaConsumer.ensureTopicIsAmongSubscribedTopicPatternTopics(topic);
                    }
                });
    }

    /**
     * {@inheritDoc}
     *
     * @return A future indicating the combined outcome of stopping the Kafka consumer and the command handler.
     *         The future will be succeeded once this component is stopped.
     */
    @Override
    public Future<Void> stop() {

        return lifecycleStatus.runStopAttempt(() -> Future.join(kafkaConsumer.stop(), commandHandler.stop())
                .mapEmpty());
    }

    @Override
    public Future<Void> createCommandConsumer(final String tenantId, final SpanContext context) {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, tenantId).toString();
        if (kafkaConsumer.isAmongKnownSubscribedTopics(topic)) {
            LOG.debug("createCommandConsumer: topic is already subscribed [{}]", topic);
            return Future.succeededFuture();
        }
        LOG.debug("""
                createCommandConsumer: topic not subscribed; check for its existence, triggering auto-creation \
                if enabled [{}]\
                """, topic);
        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                context,
                "wait for topic subscription update",
                CommandConsumerFactory.class.getSimpleName())
            .start();
        TracingHelper.TAG_TENANT_ID.set(span, tenantId);
        Tags.MESSAGE_BUS_DESTINATION.set(span, topic);
        return kafkaConsumer.ensureTopicIsAmongSubscribedTopicPatternTopics(topic)
                .onComplete(ar -> {
                    if (ar.failed()) {
                        TracingHelper.logError(span, ar.cause());
                    }
                    span.finish();
                });
    }
}
