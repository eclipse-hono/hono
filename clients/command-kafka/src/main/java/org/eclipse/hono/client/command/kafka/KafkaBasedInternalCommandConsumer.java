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

package org.eclipse.hono.client.command.kafka;

import java.net.HttpURLConnection;
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
import org.eclipse.hono.client.kafka.consumer.MessagingKafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.metrics.KafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.tracing.KafkaTracingHelper;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.registry.TenantDisabledOrNotRegisteredException;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.KafkaConsumer;
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
    private final Supplier<Future<KafkaConsumer<String, Buffer>>> consumerCreator;
    private final Supplier<Future<Admin>> kafkaAdminClientCreator;
    private final String adapterInstanceId;
    private final String clientId;
    private final CommandHandlers commandHandlers;
    private final Tracer tracer;
    private final CommandResponseSender commandResponseSender;
    private final TenantClient tenantClient;
    private final AtomicBoolean isTopicCreated = new AtomicBoolean(false);
    private final AtomicBoolean retryCreateTopic = new AtomicBoolean(true);
    /**
     * Key is the tenant id, value is a Map with partition index as key and offset as value.
     */
    private final Map<String, Map<Integer, Long>> lastHandledPartitionOffsetsPerTenant = new HashMap<>();

    private KafkaConsumer<String, Buffer> consumer;
    private Admin adminClient;
    private Context context;
    private KafkaClientMetricsSupport metricsSupport;
    private long retryCreateTopicTimerId;

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

        final Map<String, String> adminClientConfig = adminClientConfigProperties.getAdminClientConfig("internal-cmd-admin");
        // Vert.x KafkaAdminClient doesn't support creating topics using the broker default replication factor,
        // therefore use Kafka Admin client directly here
        final String bootstrapServersConfig = adminClientConfig.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG);
        final KafkaClientFactory kafkaClientFactory = new KafkaClientFactory(vertx);
        kafkaAdminClientCreator = () -> kafkaClientFactory.createClientWithRetries(
                () -> Admin.create(new HashMap<>(adminClientConfig)),
                bootstrapServersConfig,
                KafkaClientFactory.UNLIMITED_RETRIES_DURATION);

        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig("internal-cmd");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, adapterInstanceId);
        // enable auto-commits of partition offsets; this is needed in case the consumer partition assignment gets lost (e.g. because the group coordinator gets restarted)
        consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        // use "earliest" "auto.offset.reset" setting to include records published before/during consumer start
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        this.clientId = consumerConfig.get(ConsumerConfig.CLIENT_ID_CONFIG);
        consumerCreator = () -> kafkaClientFactory.createKafkaConsumerWithRetries(consumerConfig, String.class,
                Buffer.class, KafkaClientFactory.UNLIMITED_RETRIES_DURATION);
    }

    /**
     * Creates a consumer.
     * <p>
     * To be used for unit tests.
     *
     * @param context The vert.x context to run on.
     * @param kafkaAdminClient The Kafka admin client to use.
     * @param kafkaConsumer The Kafka consumer to use.
     * @param clientId The consumer client identifier.
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
            final KafkaConsumer<String, Buffer> kafkaConsumer,
            final String clientId,
            final TenantClient tenantClient,
            final CommandResponseSender commandResponseSender,
            final String adapterInstanceId,
            final CommandHandlers commandHandlers,
            final Tracer tracer) {

        this.context = Objects.requireNonNull(context);
        Objects.requireNonNull(kafkaAdminClient);
        this.consumer = Objects.requireNonNull(kafkaConsumer);
        this.clientId = Objects.requireNonNull(clientId);
        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.commandResponseSender = Objects.requireNonNull(commandResponseSender);
        this.adapterInstanceId = Objects.requireNonNull(adapterInstanceId);
        this.commandHandlers = Objects.requireNonNull(commandHandlers);
        this.tracer = Objects.requireNonNull(tracer);
        this.vertx = context.owner();
        consumerCreator = () -> Future.succeededFuture(kafkaConsumer);
        kafkaAdminClientCreator = () -> Future.succeededFuture(kafkaAdminClient);
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

    @Override
    public Future<Void> start() {
        if (context == null) {
            context = Vertx.currentContext();
            if (context == null) {
                return Future.failedFuture(new IllegalStateException("Consumer must be started in a Vert.x context"));
            }
        }
        // trigger creation of admin client, adapter specific topic and consumer
        return kafkaAdminClientCreator.get()
                .onFailure(thr -> LOG.error("admin client creation failed", thr))
                .compose(client -> {
                    adminClient = client;
                    return createTopic();
                })
                .recover(e -> retryCreateTopic())
                .compose(v -> {
                    isTopicCreated.set(true);
                    // create consumer
                    return consumerCreator.get()
                            .onFailure(thr -> LOG.error("consumer creation failed", thr));
                })
                .compose(client -> {
                    consumer = client;
                    Optional.ofNullable(metricsSupport).ifPresent(ms -> ms.registerKafkaConsumer(consumer.unwrap()));
                    return subscribeToTopic();
                });
    }

    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        LOG.trace("registering readiness check using kafka based internal command consumer [adapter instance id: {}]",
                adapterInstanceId);
        readinessHandler.register(String.format("internal-command-consumer[%s]-readiness", adapterInstanceId),
                status -> {
                    if (isTopicCreated.get()) {
                        status.tryComplete(Status.OK());
                    } else {
                        LOG.debug("readiness check failed [internal command topic is not created]");
                        status.tryComplete(Status.KO());
                    }
                });
    }

    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        // no liveness checks to be added
    }

    private Future<Void> createTopic() {
        final Promise<Void> promise = Promise.promise();
        final String topicName = getTopicName();

        // create topic with unspecified replication factor - broker "default.replication.factor" should be used
        final NewTopic newTopic = new NewTopic(topicName, Optional.of(NUM_PARTITIONS), Optional.empty());
        adminClient.createTopics(List.of(newTopic))
                .all()
                .whenComplete((v, ex) -> {
                    context.runOnContext(v1 -> Optional.ofNullable(ex)
                            .filter(e -> !(e instanceof TopicExistsException))
                            .ifPresentOrElse(promise::fail, promise::complete));
                });
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
                            vertx.cancelTimer(id);
                            createTopicRetryPromise.complete();
                        })
                        .onFailure(e -> retryCreateTopic.set(true));
            }
        });
        return createTopicRetryPromise.future();
    }

    private Future<Void> subscribeToTopic() {
        consumer.handler(this::handleCommandMessage);
        consumer.exceptionHandler(thr -> {
            LOG.error("consumer error occurred [adapterInstanceId: {}, clientId: {}]", adapterInstanceId, clientId, thr);
        });
        consumer.partitionsRevokedHandler(this::onPartitionsRevoked);
        final Promise<Void> partitionAssignedPromise = Promise.promise();
        consumer.partitionsAssignedHandler(partitionsSet -> {
            LOG.debug("partitions assigned: {}", partitionsSet);
            partitionAssignedPromise.tryComplete();
        });
        final String topicName = getTopicName();
        final Promise<Void> subscribedPromise = Promise.promise();
        consumer.subscribe(topicName, subscribedPromise);

        return CompositeFuture.all(subscribedPromise.future(), partitionAssignedPromise.future())
                .map((Void) null)
                .onComplete(ar -> consumer.partitionsAssignedHandler(this::onPartitionsAssigned))
                .onSuccess(v -> LOG.debug("subscribed and got partition assignment for topic [{}]", topicName))
                .onFailure(thr -> LOG.error("error subscribing to topic [{}]", topicName, thr));
    }

    private void onPartitionsAssigned(final Set<TopicPartition> partitionsSet) {
        LOG.debug("partitions assigned: {}", partitionsSet);
    }

    private void onPartitionsRevoked(final Set<TopicPartition> partitionsSet) {
        LOG.debug("partitions revoked: {}", partitionsSet);
    }

    private String getTopicName() {
        return new HonoTopic(HonoTopic.Type.COMMAND_INTERNAL, adapterInstanceId).toString();
    }

    @Override
    public Future<Void> stop() {
        retryCreateTopic.set(false);
        vertx.cancelTimer(retryCreateTopicTimerId);

        if (consumer == null) {
            return Future.failedFuture("not started");
        }

        return CompositeFuture.all(closeAdminClient(), closeConsumer())
                .mapEmpty();
    }

    private Future<Void> closeAdminClient() {
        final Promise<Void> adminClientClosePromise = Promise.promise();
        LOG.debug("stop: close admin client");
        context.executeBlocking(future -> {
            adminClient.close();
            LOG.debug("admin client closed");
            future.complete();
        }, adminClientClosePromise);
        return adminClientClosePromise.future();
    }

    private Future<Void> closeConsumer() {
        final Promise<Void> consumerClosePromise = Promise.promise();
        LOG.debug("stop: close consumer");
        consumer.close(consumerClosePromise);
        consumerClosePromise.future().onComplete(ar -> {
            LOG.debug("consumer closed");
            Optional.ofNullable(metricsSupport).ifPresent(ms -> ms.unregisterKafkaConsumer(consumer.unwrap()));
        });
        return consumerClosePromise.future();
    }

    void handleCommandMessage(final KafkaConsumerRecord<String, Buffer> record) {

        // get partition/offset of the command record - related to the tenant-based topic the command was originally received in
        final Integer commandPartition = KafkaRecordHelper.getOriginalPartitionHeader(record.headers())
                .orElse(null);
        final Long commandOffset = KafkaRecordHelper.getOriginalOffsetHeader(record.headers())
                .orElse(null);
        if (commandPartition == null || commandOffset == null) {
            LOG.warn("command record is invalid - missing required original partition/offset headers");
            return;
        }

        final KafkaBasedCommand command;
        try {
            command = KafkaBasedCommand.fromRoutedCommandRecord(record);
        } catch (final IllegalArgumentException e) {
            LOG.warn("command record is invalid [tenant-id: {}, device-id: {}]",
                    KafkaRecordHelper.getTenantId(record.headers()).orElse(null),
                    KafkaRecordHelper.getDeviceId(record.headers()).orElse(null),
                    e);
            return;
        }
        // check whether command has already been received and handled;
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
        } else {
            lastHandledPartitionOffsets.put(commandPartition, commandOffset);

            final CommandHandlerWrapper commandHandler = commandHandlers.getCommandHandler(command.getTenant(),
                    command.getGatewayOrDeviceId());
            if (commandHandler != null && commandHandler.getGatewayId() != null) {
                // Gateway information set in command handler means a gateway has subscribed for commands for a specific device.
                // This information isn't getting set in the record (by the Command Router) and therefore has to be adopted manually here.
                command.setGatewayId(commandHandler.getGatewayId());
            }

            final SpanContext spanContext = KafkaTracingHelper.extractSpanContext(tracer, record);
            final SpanContext followsFromSpanContext = commandHandler != null
                    ? commandHandler.getConsumerCreationSpanContext()
                    : null;
            final Span currentSpan = CommandContext.createSpan(tracer, command, spanContext, followsFromSpanContext,
                    getClass().getSimpleName());
            currentSpan.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
            KafkaTracingHelper.TAG_OFFSET.set(currentSpan, record.offset());

            final var commandContext = new KafkaBasedCommandContext(command, commandResponseSender, currentSpan);

            tenantClient.get(command.getTenant(), spanContext)
                    .onFailure(t -> {
                        if (ServiceInvocationException.extractStatusCode(t) == HttpURLConnection.HTTP_NOT_FOUND) {
                            commandContext.reject(new TenantDisabledOrNotRegisteredException(
                                    command.getTenant(),
                                    HttpURLConnection.HTTP_NOT_FOUND));
                        } else {
                            commandContext.release(new ServerErrorException(
                                    command.getTenant(),
                                    HttpURLConnection.HTTP_UNAVAILABLE,
                                    "error retrieving tenant configuration",
                                    t));
                        }
                    })
                    .onSuccess(tenantConfig -> {
                        commandContext.put(CommandContext.KEY_TENANT_CONFIG, tenantConfig);
                        if (commandHandler != null) {
                            LOG.trace("using [{}] for received command [{}]", commandHandler, command);
                            // command.isValid() check not done here - it is to be done in the command handler
                            commandHandler.handleCommand(commandContext);
                        } else {
                            LOG.info("no command handler found for command [{}]", command);
                            commandContext.release(new NoConsumerException("no command handler found for command"));
                        }
                    });
        }
    }
}
