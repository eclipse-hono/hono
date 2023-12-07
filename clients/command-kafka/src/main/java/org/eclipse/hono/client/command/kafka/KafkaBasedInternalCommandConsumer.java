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

package org.eclipse.hono.client.command.kafka;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.eclipse.hono.client.NoConsumerException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandHandlerWrapper;
import org.eclipse.hono.client.command.CommandHandlers;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.command.InternalCommandConsumer;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaAdminClientConfigProperties;
import org.eclipse.hono.client.kafka.KafkaClientFactory;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.kafka.consumer.AsyncHandlingAutoCommitKafkaConsumer;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.consumer.MessagingKafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.metrics.KafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.tracing.KafkaTracingHelper;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.registry.TenantDisabledOrNotRegisteredException;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.LifecycleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A Kafka based consumer to receive commands forwarded by the Command Router on the internal command topic.
 *
 */
public class KafkaBasedInternalCommandConsumer implements InternalCommandConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBasedInternalCommandConsumer.class);

    private static final int NUM_PARTITIONS = 1;
    private static final long CREATE_TOPIC_RETRY_INTERVAL = 1000L;

    private final Vertx vertx;
    private final Supplier<Future<AsyncHandlingAutoCommitKafkaConsumer<Buffer>>> consumerCreator;
    private final Supplier<Future<Admin>> kafkaAdminClientCreator;
    private final String adapterInstanceId;
    private final Duration pollTimeout;
    private final CommandHandlers commandHandlers;
    private final Tracer tracer;
    private final CommandResponseSender commandResponseSender;
    private final TenantClient tenantClient;
    private final AtomicBoolean retryCreateTopic = new AtomicBoolean(true);
    /**
     * Key is the tenant id, value is a Map with partition index as key and offset as value.
     */
    private final Map<String, Map<Integer, Long>> lastHandledPartitionOffsetsPerTenant = new HashMap<>();
    private final LifecycleStatus lifecycleStatus = new LifecycleStatus();

    private AsyncHandlingAutoCommitKafkaConsumer<Buffer> consumer;
    private Admin adminClient;
    private Context context;
    private KafkaClientMetricsSupport metricsSupport;
    private Long retryCreateTopicTimerId;

    /**
     * Creates a consumer.
     *
     * @param vertx The Vert.x instance to use.
     * @param adminClientConfigProperties The Kafka admin client config properties.
     * @param consumerConfigProperties The Kafka consumer config properties.
     * @param tenantClient The client to use for retrieving tenant configuration data.
     * @param commandResponseSender The sender used to send command responses.
     * @param adapterInstanceId The adapter instance id.
     * @param commandHandlers The command handlers to choose from for handling a received command.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public KafkaBasedInternalCommandConsumer(
            final Vertx vertx,
            final KafkaAdminClientConfigProperties adminClientConfigProperties,
            final MessagingKafkaConsumerConfigProperties consumerConfigProperties,
            final TenantClient tenantClient,
            final CommandResponseSender commandResponseSender,
            final String adapterInstanceId,
            final CommandHandlers commandHandlers,
            final Tracer tracer) {

        this.vertx = Objects.requireNonNull(vertx);
        Objects.requireNonNull(adminClientConfigProperties);
        Objects.requireNonNull(consumerConfigProperties);
        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.commandResponseSender = Objects.requireNonNull(commandResponseSender);
        this.adapterInstanceId = Objects.requireNonNull(adapterInstanceId);
        this.commandHandlers = Objects.requireNonNull(commandHandlers);
        this.tracer = Objects.requireNonNull(tracer);
        this.pollTimeout = Duration.ofMillis(consumerConfigProperties.getPollTimeout());

        final Map<String, String> adminClientConfig = adminClientConfigProperties.getAdminClientConfig("internal-cmd-admin");
        // Vert.x KafkaAdminClient doesn't support creating topics using the broker default replication factor,
        // therefore use Kafka Admin client directly here
        final String bootstrapServersConfig = adminClientConfig.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG);
        final KafkaClientFactory kafkaClientFactory = new KafkaClientFactory(vertx);
        kafkaAdminClientCreator = () -> kafkaClientFactory.createClientWithRetries(
                () -> Admin.create(new HashMap<>(adminClientConfig)),
                lifecycleStatus::isStarting,
                bootstrapServersConfig,
                KafkaClientFactory.UNLIMITED_RETRIES_DURATION);

        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("internal-cmd");
        setFixedConsumerConfigValues(consumerConfig, adapterInstanceId);
        consumerCreator = () -> kafkaClientFactory.createClientWithRetries(
                () -> new AsyncHandlingAutoCommitKafkaConsumer<>(
                        vertx,
                        Set.of(getTopicName()),
                        this::handleCommandMessage,
                        consumerConfig),
                lifecycleStatus::isStarting,
                consumerConfig.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG),
                KafkaClientFactory.UNLIMITED_RETRIES_DURATION);
    }

    /**
     * Creates a consumer.
     * <p>
     * To be used for unit tests.
     *
     * @param context The vert.x context to run on.
     * @param kafkaAdminClient The Kafka admin client to use.
     * @param kafkaConsumer The Kafka consumer to use.
     * @param tenantClient The client to use for retrieving tenant configuration data.
     * @param commandResponseSender The sender used to send command responses.
     * @param adapterInstanceId The adapter instance id.
     * @param commandHandlers The command handlers to choose from for handling a received command.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    KafkaBasedInternalCommandConsumer(
            final Context context,
            final Admin kafkaAdminClient,
            final Consumer<String, Buffer> kafkaConsumer,
            final TenantClient tenantClient,
            final CommandResponseSender commandResponseSender,
            final String adapterInstanceId,
            final CommandHandlers commandHandlers,
            final Tracer tracer) {

        this.context = Objects.requireNonNull(context);
        Objects.requireNonNull(kafkaAdminClient);
        Objects.requireNonNull(kafkaConsumer);
        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.commandResponseSender = Objects.requireNonNull(commandResponseSender);
        this.adapterInstanceId = Objects.requireNonNull(adapterInstanceId);
        this.commandHandlers = Objects.requireNonNull(commandHandlers);
        this.tracer = Objects.requireNonNull(tracer);
        this.vertx = context.owner();
        this.pollTimeout = Duration.ofMillis(KafkaConsumerConfigProperties.DEFAULT_POLL_TIMEOUT_MILLIS);

        final var consumerConfig = new HashMap<String, String>();
        setFixedConsumerConfigValues(consumerConfig, adapterInstanceId);
        consumerCreator = () -> {
            final var result = new AsyncHandlingAutoCommitKafkaConsumer<>(
                    vertx,
                    Set.of(getTopicName()),
                    this::handleCommandMessage,
                    consumerConfig);
            result.setKafkaConsumerSupplier(() -> kafkaConsumer);
            return Future.succeededFuture(result);
        };
        kafkaAdminClientCreator = () -> Future.succeededFuture(kafkaAdminClient);
    }

    private void setFixedConsumerConfigValues(final Map<String, String> consumerConfig, final String adapterInstanceId) {
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, adapterInstanceId);
        // enable auto-commits of partition offsets; this is needed in case the consumer partition assignment gets lost (e.g. because the group coordinator gets restarted)
        consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        // use "earliest" "auto.offset.reset" setting to include records published before/during consumer start
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    }

    /**
     * Sets Kafka metrics support with which this consumer will be registered.
     *
     * @param metricsSupport The metrics support to set.
     * @return This object for command chaining.
     */
    public final KafkaBasedInternalCommandConsumer setMetricsSupport(final KafkaClientMetricsSupport metricsSupport) {
        this.metricsSupport = metricsSupport;
        return this;
    }

    /**
     * Adds a handler to be invoked with a succeeded future once the Kafka consumer is ready to be used.
     *
     * @param handler The handler to invoke. The handler will never be invoked with a failed future.
     */
    public final void addOnKafkaConsumerReadyHandler(final Handler<AsyncResult<Void>> handler) {
        if (handler != null) {
            lifecycleStatus.addOnStartedHandler(handler);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This methods triggers the creation of the internal command topic and a corresponding Kafka consumer in the
     * background. A new attempt to create the topic and the consumer is made periodically until creation succeeds
     * or the {@link #stop()} method has been invoked.
     * <p>
     * Client code may {@linkplain #addOnKafkaConsumerReadyHandler(Handler) register a dedicated handler}
     * to be notified once the consumer is up and running.
     *
     * @return A succeeded future. Note that the successful completion of the returned future does not
     *         mean that the consumer will be ready to receive messages from the broker.
     */
    @Override
    public Future<Void> start() {

        if (lifecycleStatus.isStarting()) {
            return Future.succeededFuture();
        } else if (!lifecycleStatus.setStarting()) {
            return Future.failedFuture(new IllegalStateException("consumer is already started/stopping"));
        }

        if (context == null) {
            context = Vertx.currentContext();
            if (context == null) {
                return Future.failedFuture(new IllegalStateException("Consumer must be started in a Vert.x context"));
            }
        }
        // trigger creation of admin client, adapter specific topic and consumer
        kafkaAdminClientCreator.get()
            .onFailure(thr -> LOG.error("admin client creation failed", thr))
            .compose(client -> {
                adminClient = client;
                return createTopic();
            })
            .recover(e -> retryCreateTopic())
            .compose(v -> consumerCreator.get()
                    .onFailure(thr -> LOG.error("consumer creation failed", thr)))
            .compose(createdConsumer -> {
                consumer = createdConsumer;
                consumer.addOnKafkaConsumerReadyHandler(ar -> lifecycleStatus.setStarted());
                consumer.setPollTimeout(pollTimeout);
                consumer.setConsumerCreationRetriesTimeout(KafkaClientFactory.UNLIMITED_RETRIES_DURATION);
                consumer.setMetricsSupport(metricsSupport);
                return consumer.start();
            });

        return Future.succeededFuture();
    }

    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        LOG.trace("registering readiness check using kafka based internal command consumer [adapter instance id: {}]",
                adapterInstanceId);
        readinessHandler.register("internal-command-consumer[%s]-readiness".formatted(adapterInstanceId),
                status -> {
                    if (lifecycleStatus.isStarted()) {
                        status.tryComplete(Status.OK());
                    } else {
                        final JsonObject data = new JsonObject();
                        if (lifecycleStatus.isStarting()) {
                            if (adminClient == null) {
                                LOG.debug("readiness check failed, admin client not created yet (Kafka server URL possibly not resolvable (yet))");
                                data.put("status", "admin client not created yet (Kafka server URL possibly not resolvable (yet))");
                            } else if (retryCreateTopicTimerId != null) {
                                LOG.debug("readiness check failed, internal command topic not created yet");
                                data.put("status", "internal command topic not created yet");
                            } else if (consumer != null) {
                                LOG.debug("readiness check failed, consumer not ready yet");
                                data.put("status", "consumer not ready yet");
                            } else {
                                LOG.debug("readiness check failed");
                            }
                        }
                        status.tryComplete(Status.KO(data));
                    }
                });
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private Future<Void> createTopic() {
        final Promise<Void> promise = Promise.promise();
        final String topicName = getTopicName();

        // create topic with unspecified replication factor - broker "default.replication.factor" should be used
        final NewTopic newTopic = new NewTopic(topicName, Optional.of(NUM_PARTITIONS), Optional.empty());
        adminClient.createTopics(List.of(newTopic))
                .all()
                .whenComplete((v, ex) -> context.runOnContext(v1 -> Optional.ofNullable(ex)
                        .filter(e -> !(e instanceof TopicExistsException))
                        .ifPresentOrElse(promise::fail, promise::complete)));
        return promise.future()
                .onSuccess(v -> LOG.debug("created topic [{}]", topicName))
                .onFailure(thr -> LOG.error("error creating topic [{}]", topicName, thr));
    }

    private Future<Void> retryCreateTopic() {
        final Promise<Void> createTopicRetryPromise = Promise.promise();
        // Retry at specified interval until the internal command topic is successfully created
        retryCreateTopicTimerId = vertx.setPeriodic(CREATE_TOPIC_RETRY_INTERVAL, id -> {
            if (retryCreateTopic.compareAndSet(true, false)) {
                createTopic()
                        .onSuccess(ok -> {
                            retryCreateTopicTimerId = null;
                            vertx.cancelTimer(id);
                            createTopicRetryPromise.complete();
                        })
                        .onFailure(e -> retryCreateTopic.set(true));
            }
        });
        return createTopicRetryPromise.future();
    }

    private String getTopicName() {
        return new HonoTopic(HonoTopic.Type.COMMAND_INTERNAL, adapterInstanceId).toString();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Closes the Kafka admin client and consumer.
     *
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded once this component is stopped.
     */
    @Override
    public Future<Void> stop() {

        return lifecycleStatus.runStopAttempt(() -> {
            retryCreateTopic.set(false);
            Optional.ofNullable(retryCreateTopicTimerId)
                    .ifPresent(vertx::cancelTimer);

            return Future.all(closeAdminClient(), stopConsumer())
                .mapEmpty();
        });
    }

    private Future<Void> closeAdminClient() {
        if (adminClient == null) {
            return Future.succeededFuture();
        }
        final Promise<Void> adminClientClosePromise = Promise.promise();
        LOG.debug("stop: close admin client");
        context.executeBlocking(future -> {
            adminClient.close();
            LOG.debug("admin client closed");
            future.complete();
        }, adminClientClosePromise);
        return adminClientClosePromise.future();
    }

    private Future<Void> stopConsumer() {
        return Optional.ofNullable(consumer)
                .map(AsyncHandlingAutoCommitKafkaConsumer::stop)
                .orElseGet(Future::succeededFuture);
    }

    Future<Void> handleCommandMessage(final KafkaConsumerRecord<String, Buffer> receivedRecord) {

        // get partition/offset of the command record - related to the tenant-based topic the command was originally received in
        final Integer commandPartition = KafkaRecordHelper.getOriginalPartitionHeader(receivedRecord.headers())
                .orElse(null);
        final Long commandOffset = KafkaRecordHelper.getOriginalOffsetHeader(receivedRecord.headers())
                .orElse(null);
        if (commandPartition == null || commandOffset == null) {
            LOG.warn("command record is invalid - missing required original partition/offset headers");
            return Future.failedFuture("command record is invalid");
        }

        final KafkaBasedCommand command;
        try {
            command = KafkaBasedCommand.fromRoutedCommandRecord(receivedRecord);
        } catch (final IllegalArgumentException e) {
            LOG.warn("command record is invalid [tenant-id: {}, device-id: {}]",
                    KafkaRecordHelper.getTenantId(receivedRecord.headers()).orElse(null),
                    KafkaRecordHelper.getDeviceId(receivedRecord.headers()).orElse(null),
                    e);
            return Future.failedFuture("command record is invalid");
        }
        // check whether command has already been received and handled
        // partition index and offset here are related to the *tenant-based* topic the command was originally received in
        // therefore they are stored in a map with the tenant as key
        final Map<Integer, Long> lastHandledPartitionOffsets = lastHandledPartitionOffsetsPerTenant
                .computeIfAbsent(command.getTenant(), k -> new HashMap<>());
        final Long lastHandledOffset = lastHandledPartitionOffsets.get(commandPartition);
        if (lastHandledOffset != null && commandOffset <= lastHandledOffset) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("ignoring command - record partition offset {} <= last handled offset {} [{}]", commandOffset,
                        lastHandledOffset, command);
            }
            return Future.succeededFuture();
        } else {
            lastHandledPartitionOffsets.put(commandPartition, commandOffset);

            final CommandHandlerWrapper commandHandler = commandHandlers.getCommandHandler(command.getTenant(),
                    command.getGatewayOrDeviceId());
            if (commandHandler != null && commandHandler.getGatewayId() != null) {
                // Gateway information set in command handler means a gateway has subscribed for commands for a specific device.
                // This information isn't getting set in the record (by the Command Router) and therefore has to be adopted manually here.
                command.setGatewayId(commandHandler.getGatewayId());
            }

            final SpanContext spanContext = KafkaTracingHelper.extractSpanContext(tracer, receivedRecord);
            final SpanContext followsFromSpanContext = commandHandler != null
                    ? commandHandler.getConsumerCreationSpanContext()
                    : null;
            final Span currentSpan = CommandContext.createSpan(tracer, command, spanContext, followsFromSpanContext,
                    getClass().getSimpleName());
            TracingHelper.TAG_ADAPTER_INSTANCE_ID.set(currentSpan, adapterInstanceId);
            KafkaTracingHelper.TAG_OFFSET.set(currentSpan, receivedRecord.offset());

            final var commandContext = new KafkaBasedCommandContext(command, commandResponseSender, currentSpan);

            return tenantClient.get(command.getTenant(), spanContext)
                    .recover(t -> {
                        final Throwable mappedException;
                        if (ServiceInvocationException.extractStatusCode(t) == HttpURLConnection.HTTP_NOT_FOUND) {
                            mappedException = new TenantDisabledOrNotRegisteredException(
                                    command.getTenant(),
                                    HttpURLConnection.HTTP_NOT_FOUND);
                            commandContext.reject(mappedException);
                        } else {
                            mappedException = new ServerErrorException(
                                    command.getTenant(),
                                    HttpURLConnection.HTTP_UNAVAILABLE,
                                    "error retrieving tenant configuration",
                                    t);
                            commandContext.release(mappedException);
                        }
                        return Future.failedFuture(mappedException);
                    })
                    .compose(tenantConfig -> {
                        commandContext.put(CommandContext.KEY_TENANT_CONFIG, tenantConfig);
                        if (commandHandler != null) {
                            LOG.trace("using [{}] for received command [{}]", commandHandler, command);
                            // command.isValid() check not done here - it is to be done in the command handler
                            return commandHandler.handleCommand(commandContext);
                        } else {
                            LOG.info("no command handler found for command [{}]", command);
                            final var exception = new NoConsumerException("no command handler found for command");
                            commandContext.release(exception);
                            return Future.failedFuture(exception);
                        }
                    });
        }
    }
}
