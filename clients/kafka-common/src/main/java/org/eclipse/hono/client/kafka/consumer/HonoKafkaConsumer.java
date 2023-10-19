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
package org.eclipse.hono.client.kafka.consumer;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.common.metrics.Metrics;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.kafka.KafkaClientFactory;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.kafka.metrics.KafkaClientMetricsSupport;
import org.eclipse.hono.client.util.ServiceClient;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.LifecycleStatus;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.common.impl.Helper;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.consumer.KafkaConsumerRecords;
import io.vertx.kafka.client.consumer.impl.KafkaReadStreamImpl;

/**
 * A consumer for receiving records from a Kafka broker.
 * <p>
 * Wraps a vert.x Kafka consumer that is created during startup.
 * Includes adapted partition assignment handling concerning partition position resets.
 *
 * @param <V> The type of record payload this consumer supports.
 */
@RegisterForReflection(targets = io.vertx.kafka.client.consumer.impl.KafkaReadStreamImpl.class)
public class HonoKafkaConsumer<V> implements Lifecycle, ServiceClient {

    /**
     * The threshold value for the consumer's {@value ConsumerConfig#METADATA_MAX_AGE_CONFIG} configuration property
     * which determines the consumer's strategy for getting assigned partitions of dynamically created topics.
     * <p>
     * The consumer enforces a rebalance to get assigned partitions if the property is set and its value is greater
     * than the threshold. Otherwise, the consumer will wait for the metadata to become stale and be refreshed.
     */
    public static final int THRESHOLD_METADATA_MAX_AGE_MS = 500;

    /**
     * The default timeout to use when polling the broker for messages.
     */
    public static final long DEFAULT_POLL_TIMEOUT_MILLIS = 250;

    private static final long OBSOLETE_METRICS_REMOVAL_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final String MSG_CONSUMER_NOT_INITIALIZED_STARTED = "consumer not initialized/started";
    private static final Logger LOG = LoggerFactory.getLogger(HonoKafkaConsumer.class);

    /**
     * The Vert.x instance used by this consumer.
     */
    protected final Vertx vertx;
    /**
     * The Kafka configuration properties of this consumer.
     */
    protected final Map<String, String> consumerConfig;
    /**
     * The topics that this consumer subscribes to.
     */
    protected final Set<String> topics;
    /**
     * The pattern of topic names that this consumer subscribes to.
     */
    protected final Pattern topicPattern;
    /**
     * This component's current life cycle state.
     */
    protected final LifecycleStatus lifecycleStatus = new LifecycleStatus();

    private final AtomicReference<Promise<Void>> initialPartitionAssignmentDonePromiseRef = new AtomicReference<>();

    private final AtomicBoolean subscriptionUpdateTriggered = new AtomicBoolean();
    /**
     * Map with the topics, used in {@link #ensureTopicIsAmongSubscribedTopicPatternTopics(String)} method invocations,
     * that are not in the list of subscribed topics yet. The map value is the tracker to use for completing that method's
     * result future when the subscription got updated.
     */
    private final Map<String, SubscriptionUpdateTracker> subscriptionUpdateTrackersForToBeAddedTopics = new HashMap<>();
    private final Optional<Integer> metadataMaxAge;

    private final AtomicBoolean pollingPaused = new AtomicBoolean();
    private final AtomicBoolean recordFetchingPaused = new AtomicBoolean();

    private Handler<KafkaConsumerRecord<String, V>> recordHandler;
    private KafkaConsumer<String, V> kafkaConsumer;

    /**
     * The vert.x context used by the KafkaConsumer.
     */
    private Context context;
    /**
     * The executor service to run tasks on the Kafka polling thread.
     */
    private ExecutorService kafkaConsumerWorker;
    /**
     * Currently subscribed topics matching the topic pattern (if set).
     * Note that these are not (necessarily) the topics that this particular kafkaConsumer
     * here will receive messages for.
     */
    private volatile Set<String> subscribedTopicPatternTopics = new HashSet<>();

    private ConsumerRebalanceListener rebalanceListener;
    private Handler<Set<TopicPartition>> onPartitionsAssignedHandler;
    private Handler<Set<TopicPartition>> onRebalanceDoneHandler;
    private Handler<Set<TopicPartition>> onPartitionsRevokedHandler;
    private Handler<Set<TopicPartition>> onPartitionsLostHandler;
    private boolean respectTtl = true;
    private Duration pollTimeout = Duration.ofMillis(DEFAULT_POLL_TIMEOUT_MILLIS);
    private Supplier<Consumer<String, V>> kafkaConsumerSupplier;
    private KafkaClientMetricsSupport metricsSupport;
    private Long pollPauseTimeoutTimerId;
    private Duration consumerCreationRetriesTimeout = Duration.ZERO; // consumer creation retries disabled by default

    /**
     * Creates a consumer to receive records on the given topics.
     *
     * @param vertx The Vert.x instance to use.
     * @param topics The Kafka topic to consume records from.
     * @param recordHandler The handler to be invoked for each received record.
     * @param consumerConfig The Kafka consumer configuration.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the consumerConfig contains an
     *             {@value ConsumerConfig#ENABLE_AUTO_COMMIT_CONFIG} property with value {@code true} but no
     *             {@value ConsumerConfig#GROUP_ID_CONFIG} property.
     */
    public HonoKafkaConsumer(
            final Vertx vertx,
            final Set<String> topics,
            final Handler<KafkaConsumerRecord<String, V>> recordHandler,
            final Map<String, String> consumerConfig) {
        this(vertx, Objects.requireNonNull(topics), (Pattern) null, consumerConfig);
        setRecordHandler(Objects.requireNonNull(recordHandler));
    }

    /**
     * Creates a consumer to receive records on topics that match the given pattern.
     *
     * @param vertx The Vert.x instance to use.
     * @param topicPattern The pattern of Kafka topic names to consume records from.
     * @param recordHandler The handler to be invoked for each received record.
     * @param consumerConfig The Kafka consumer configuration.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the consumerConfig contains an
     *             {@value ConsumerConfig#ENABLE_AUTO_COMMIT_CONFIG} property with value {@code true} but no
     *             {@value ConsumerConfig#GROUP_ID_CONFIG} property.
     */
    public HonoKafkaConsumer(
            final Vertx vertx,
            final Pattern topicPattern,
            final Handler<KafkaConsumerRecord<String, V>> recordHandler,
            final Map<String, String> consumerConfig) {
        this(vertx, null, Objects.requireNonNull(topicPattern), consumerConfig);
        setRecordHandler(Objects.requireNonNull(recordHandler));
    }

    /**
     * Creates a consumer to receive records on topics defined either via a topic list or a topic pattern.
     *
     * @param vertx The Vert.x instance to use.
     * @param topics The Kafka topic to consume records from ({@code null} if topicPattern is set).
     * @param topicPattern The pattern of Kafka topic names to consume records from ({@code null} if topics is set).
     * @param consumerConfig The Kafka consumer configuration.
     * @throws NullPointerException if vertx, recordHandler, consumerConfig or either topics or topicPattern is
     *                              {@code null}.
     * @throws IllegalArgumentException if the consumerConfig contains an
     *             {@value ConsumerConfig#ENABLE_AUTO_COMMIT_CONFIG} property with value {@code true} but no
     *             {@value ConsumerConfig#GROUP_ID_CONFIG} property.
     */
    protected HonoKafkaConsumer(
            final Vertx vertx,
            final Set<String> topics,
            final Pattern topicPattern,
            final Map<String, String> consumerConfig) {

        this.vertx = Objects.requireNonNull(vertx);
        if ((topics == null) == (topicPattern == null)) {
            throw new NullPointerException("exactly one of topics or topicPattern has to be set");
        }
        this.topicPattern = topicPattern;
        this.topics = Optional.ofNullable(topics).map(HashSet::new).orElse(null);
        this.consumerConfig = Objects.requireNonNull(consumerConfig);
        this.metadataMaxAge = Optional.ofNullable(consumerConfig.get(ConsumerConfig.METADATA_MAX_AGE_CONFIG))
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                });

        if (!consumerConfig.containsKey(ConsumerConfig.GROUP_ID_CONFIG)) {
            if ("true".equals(consumerConfig.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG))) {
                throw new IllegalArgumentException("%s config entry has to be set if auto-commit is enabled"
                        .formatted(ConsumerConfig.GROUP_ID_CONFIG));
            }
            LOG.trace("no group.id set, using a random UUID as default and disabling auto-commit");
            consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
            consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        }
    }

    /**
     * Sets the handler to invoke for each record that is received from the broker.
     *
     * @param handler The handler.
     * @throws NullPointerException if handler is {@code null}.
     * @throws IllegalStateException if this consumer is already started.
     */
    public final void setRecordHandler(final Handler<KafkaConsumerRecord<String, V>> handler) {
        Objects.requireNonNull(handler);
        if (!lifecycleStatus.isStopped()) {
            throw new IllegalStateException("Record handler can only be set if consumer has not been started yet");
        }
        this.recordHandler = handler;
    }

    /**
     * Adds a topic to consume records from.
     *
     * @param topicName The name of the topic.
     * @throws NullPointerException if topicName is {@code null}.
     * @throws IllegalStateException if this consumer is already started.
     */
    protected final void addTopic(final String topicName) {
        Objects.requireNonNull(topicName);
        if (!lifecycleStatus.isStopped()) {
            throw new IllegalStateException("Topics can only be set if consumer has not been started yet");
        } else if (topics == null) {
            throw new IllegalStateException("Cannot add topic on consumer which has been created with a topic pattern");
        }
        this.topics.add(topicName);
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
     * Sets a handler to be invoked on the vert.x event loop thread when partitions have been assigned
     * as part of a rebalance.
     * <p>
     * NOTE: the partitions with which the handler is invoked are the <i>newly</i> assigned partitions,
     * i.e. previously owned partitions are not included.
     *
     * @param onPartitionsAssignedHandler The handler to be invoked with the newly added partitions (without the
     *                                    previously owned partitions).
     */
    public final void setOnPartitionsAssignedHandler(final Handler<Set<TopicPartition>> onPartitionsAssignedHandler) {
        this.onPartitionsAssignedHandler = Objects.requireNonNull(onPartitionsAssignedHandler);
    }

    /**
     * Sets a handler to be invoked on the vert.x event loop thread when a rebalance is done.
     * <p>
     * The handler is invoked with all currently assigned partitions at the end of the rebalance.
     *
     * @param handler The handler to be invoked with all currently assigned partitions.
     */
    public final void setOnRebalanceDoneHandler(final Handler<Set<TopicPartition>> handler) {
        this.onRebalanceDoneHandler = Objects.requireNonNull(handler);
    }

    /**
     * Sets a handler to be invoked on the vert.x event loop thread
     * when partitions are about to be revoked as part of a rebalance.
     *
     * @param onPartitionsRevokedHandler The handler to be invoked.
     */
    public final void setOnPartitionsRevokedHandler(final Handler<Set<TopicPartition>> onPartitionsRevokedHandler) {
        this.onPartitionsRevokedHandler = Objects.requireNonNull(onPartitionsRevokedHandler);
    }

    /**
     * Sets a handler to be invoked on the vert.x event loop thread
     * when the consumer realized that it does not own the given partitions any longer.
     *
     * @param onPartitionsLostHandler The handler to be invoked.
     */
    public final void setOnPartitionsLostHandler(final Handler<Set<TopicPartition>> onPartitionsLostHandler) {
        this.onPartitionsLostHandler = Objects.requireNonNull(onPartitionsLostHandler);
    }

    /**
     * Sets Kafka metrics support with which this consumer will be registered.
     *
     * @param metricsSupport The metrics support to set.
     */
    public final void setMetricsSupport(final KafkaClientMetricsSupport metricsSupport) {
        this.metricsSupport = metricsSupport;
    }

    /**
     * Defines the duration for which to retry creating the Kafka consumer instance as part of the {@link #start()}
     * invocation. A retry for a creation attempt is done if creation fails because the
     * <em>bootstrap.servers</em> config property contains a (non-empty) list of URLs that are not (yet) resolvable.
     * <p>
     * The default is to not do any retries (corresponds to using {@link Duration#ZERO}).
     *
     * @param consumerCreationRetriesTimeout The maximum time for which retries are done. Using a negative duration or
     *            {@code null} here is interpreted as an unlimited timeout value
     *            ({@link KafkaClientFactory#UNLIMITED_RETRIES_DURATION} may be used for that case).
     */
    public final void setConsumerCreationRetriesTimeout(final Duration consumerCreationRetriesTimeout) {
        this.consumerCreationRetriesTimeout = consumerCreationRetriesTimeout;
    }

    /**
     * Defines whether records that contain a <em>ttl</em> header where the time-to-live has elapsed should be ignored.
     * <p>
     * The default is true.
     *
     * @param respectTtl The intended behavior: if true, messages with elapsed ttl are silently dropped and the message
     *            handler is not invoked.
     */
    public final void setRespectTtl(final boolean respectTtl) {
        this.respectTtl = respectTtl;
    }

    /**
     * Sets the poll timeout for the underlying native Kafka Consumer.
     * Defaults to {@value #DEFAULT_POLL_TIMEOUT_MILLIS} ms.
     * <p>
     * Setting the timeout to a lower value results in a more 'responsive' client because it will block for a shorter
     * period if no data is available  in the assigned partition and therefore allows subsequent actions to be executed
     * with a shorter delay. At the same time, the client will poll more frequently and thus will potentially create a
     * higher load on the Kafka Broker.
     *
     * @param pollTimeout The poll timeout.
     * @throws NullPointerException if pollTimeout is {@code null}.
     */
    public final void setPollTimeout(final Duration pollTimeout) {
        this.pollTimeout = Objects.requireNonNull(pollTimeout);
        if (kafkaConsumer != null) {
            kafkaConsumer.asStream().pollTimeout(pollTimeout);
        }
    }

    /**
     * Only to be used for unit tests.
     *
     * @param supplier Supplier creating the internal Kafka consumer.
     */
    public void setKafkaConsumerSupplier(final Supplier<Consumer<String, V>> supplier) {
        kafkaConsumerSupplier = supplier;
    }

    /**
     * Suspends fetching records from all partitions assigned to this consumer (if not already suspended).
     * That means the next poll invocations won't return any records.
     * For already fetched records, the record handler will still be invoked.
     *
     * @return {@code true} if the record fetching was active before and is now getting paused.
     */
    public final boolean pauseRecordFetching() {
        if (!recordFetchingPaused.compareAndSet(false, true)) {
            return false;
        }
        runOnKafkaWorkerThread(v -> {
            // note that some partitions could already have been paused here if a rebalance happened just after the paused field was updated above
            final var partitions = getUnderlyingConsumer().assignment();
            if (!partitions.isEmpty()) {
                getUnderlyingConsumer().pause(partitions);
            }
        });
        return true;
    }

    /**
     * Resumes the fetching of records for this consumer (if paused).
     *
     * @return {@code true} if record fetching was paused and is now getting resumed.
     */
    public final boolean resumeRecordFetching() {
        if (!recordFetchingPaused.compareAndSet(true, false)) {
            return false;
        }
        runOnKafkaWorkerThread(v -> {
            final var partitions = getUnderlyingConsumer().assignment();
            if (!partitions.isEmpty()) {
                getUnderlyingConsumer().resume(partitions);
            }
        });
        return true;
    }

    /**
     * Checks if fetching of records for this consumer is currently paused.
     *
     * @return {@code true} if record fetching is paused.
     */
    public final boolean isRecordFetchingPaused() {
        return recordFetchingPaused.get();
    }

    /**
     * Pauses the consumer polling operation (if not already paused).
     * <p>
     * Note that the polling operation mustn't be paused for too long (longer than <em>max.poll.interval.ms</em>),
     * otherwise the consumer will leave the active consumer group.
     *
     * @param timeout The timeout after which to resume polling.
     * @return {@code true} if the consumer polling operation was active before and is now paused.
     */
    public final boolean pauseRecordHandlingAndPolling(final Duration timeout) {
        if (!pollingPaused.compareAndSet(false, true)) {
            return false;
        }
        pollPauseTimeoutTimerId = vertx.setTimer(timeout.toMillis(), tid -> {
            pollPauseTimeoutTimerId = null;
            if (resumeRecordHandlingAndPolling()) {
                LOG.debug("resumed consumer record polling - timeout of {}ms was reached [client-id: {}]", timeout.toMillis(), getClientId());
            }
        });
        getKafkaConsumer().pause();
        return true;
    }

    /**
     * Resumes the consumer polling operation (if paused).
     *
     * @return {@code true} if the consumer polling operation was paused and is now resumed.
     */
    public final boolean resumeRecordHandlingAndPolling() {
        if (!pollingPaused.compareAndSet(true, false)) {
            return false;
        }
        if (pollPauseTimeoutTimerId != null) {
            vertx.cancelTimer(pollPauseTimeoutTimerId);
            pollPauseTimeoutTimerId = null;
        }
        getKafkaConsumer().resume();
        return true;
    }

    /**
     * Checks if the consumer polling operation currently is paused.
     *
     * @return {@code true} if the consumer polling operation is paused.
     */
    public final boolean isRecordHandlingAndPollingPaused() {
        return pollingPaused.get();
    }

    /**
     * Gets the used vert.x KafkaConsumer.
     *
     * @return The Kafka consumer.
     * @throws IllegalStateException if invoked before the KafkaConsumer is set via the {@link #start()} method.
     */
    protected final KafkaConsumer<String, V> getKafkaConsumer() {
        if (kafkaConsumer == null) {
            throw new IllegalStateException(MSG_CONSUMER_NOT_INITIALIZED_STARTED);
        }
        return kafkaConsumer;
    }

    /**
     * Gets the underlying (non-vert.x) Kafka Consumer.
     * <p>
     * Note: That consumer must only be used from the Kafka polling thread.
     *
     * @return The Kafka Consumer.
     * @throws IllegalStateException if invoked before the KafkaConsumer is set via the {@link #start()} method.
     */
    protected final Consumer<String, V> getUnderlyingConsumer() {
        if (kafkaConsumer == null) {
            throw new IllegalStateException(MSG_CONSUMER_NOT_INITIALIZED_STARTED);
        }
        return kafkaConsumer.asStream().unwrap();
    }

    /**
     * Gets the client identifier of this consumer.
     *
     * @return The client identifier.
     */
    protected final String getClientId() {
        return consumerConfig.get(ConsumerConfig.CLIENT_ID_CONFIG);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Registers a check for the Kafka consumer being ready to be used.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        readinessHandler.register(
                "kafka-consumer[%s]-creation".formatted(getClientId()),
                status -> {
                    if (lifecycleStatus.isStarted()) {
                        status.tryComplete(Status.OK());
                    } else {
                        final JsonObject data = new JsonObject();
                        if (lifecycleStatus.isStarting()) {
                            if (kafkaConsumer == null) {
                                LOG.debug("readiness check failed, consumer not created yet (Kafka server URL possibly not resolvable (yet)) [client-id: {}]",
                                        getClientId());
                                data.put("status", "consumer not created yet (Kafka server URL possibly not resolvable (yet))");
                            } else {
                                LOG.debug("readiness check failed, consumer initialization not finished yet [client-id: {}]",
                                        getClientId());
                                data.put("status", "consumer initialization not finished yet");
                            }
                        }
                        status.tryComplete(Status.KO(data));
                    }
                });
    }

    /**
     * Checks if this consumer is ready to be used.
     *
     * @return A response indicating if this consumer is ready to be used (UP) or not (DOWN).
     */
    public final HealthCheckResponse checkReadiness() {
        return HealthCheckResponse.builder()
                .name("kafka-consumer-status")
                .status(lifecycleStatus.isStarted())
                .build();
    }

    private Future<KafkaConsumer<String, V>> initConsumer(final KafkaConsumer<String, V> consumer) {

        final Promise<KafkaConsumer<String, V>> initResult = Promise.promise();

        Optional.ofNullable(metricsSupport).ifPresent(ms -> ms.registerKafkaConsumer(consumer.unwrap()));
        consumer.handler(receivedRecord -> {
            if (!initResult.future().isComplete() && LOG.isDebugEnabled()) {
                LOG.debug("""
                        postponing record handling until consumer has been initialized \
                        [topic: {}, partition: {}, offset: {}]\
                        """,
                        receivedRecord.topic(), receivedRecord.partition(), receivedRecord.offset());
            }
            initResult.future().onSuccess(ok -> {
                if (respectTtl && KafkaRecordHelper.isTtlElapsed(receivedRecord.headers())) {
                    onRecordHandlerSkippedForExpiredRecord(receivedRecord);
                } else {
                    try {
                        recordHandler.handle(receivedRecord);
                    } catch (final Exception e) {
                        LOG.warn("error handling record [topic: {}, partition: {}, offset: {}, headers: {}]",
                                receivedRecord.topic(), receivedRecord.partition(), receivedRecord.offset(), receivedRecord.headers(), e);
                    }
                }
            });
        });
        consumer.batchHandler(this::onBatchOfRecordsReceived);
        consumer.exceptionHandler(error -> LOG.error("consumer error occurred [client-id: {}]", getClientId(), error));
        installRebalanceListeners();
        // let polls finish quickly until initConsumer() is completed
        consumer.asStream().pollTimeout(Duration.ofMillis(10));
        // subscribe and wait for re-balance to make sure that when initConsumer() completes,
        // the consumer is actually ready to receive records already
        initSubscriptionAndWaitForRebalance()
                .onSuccess(ok -> {
                    consumer.asStream().pollTimeout(pollTimeout);
                    logSubscribedTopicsWhenConsumerIsReady();
                    initResult.complete(consumer);
                })
                .onFailure(initResult::fail);
        return initResult.future();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This methods triggers the creation of a Kafka consumer in the background. A new attempt to create the
     * consumer is made periodically until creation succeeds or the {@link #stop()} method has been invoked.
     * <p>
     * Client code may {@linkplain #addOnKafkaConsumerReadyHandler(Handler) register a dedicated handler}
     * to be notified once the consumer is up and running.
     *
     * @return A future indicating the outcome of the operation.
     *         The future will be failed with an {@link IllegalStateException} if the record handler is not set
     *         or if this component is already started or is in the process of being stopped.
     *         Note that the successful completion of the returned future does not mean that the consumer will be
     *         ready to receive messages from the broker.
     */
    @Override
    public Future<Void> start() {

        if (recordHandler == null) {
            throw new IllegalStateException("Record handler must be set");
        }
        if (lifecycleStatus.isStarting()) {
            LOG.debug("already starting consumer");
            return Future.succeededFuture();
        } else if (!lifecycleStatus.setStarting()) {
            return Future.failedFuture(new IllegalStateException("consumer is already started/stopping"));
        }

        context = vertx.getOrCreateContext();
        final Supplier<KafkaConsumer<String, V>> consumerSupplier = () -> Optional.ofNullable(kafkaConsumerSupplier)
                .map(s -> KafkaConsumer.create(vertx, s.get()))
                .orElseGet(() -> KafkaConsumer.create(vertx, consumerConfig));

        runOnContext(v -> {
            // create KafkaConsumer here so that it is created in the Vert.x context of the start() method
            // (KafkaConsumer uses vertx.getOrCreateContext())
            final KafkaClientFactory kafkaClientFactory = new KafkaClientFactory(vertx);
            kafkaClientFactory.createClientWithRetries(
                    consumerSupplier,
                    lifecycleStatus::isStarting,
                    consumerConfig.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG),
                    consumerCreationRetriesTimeout)
                .onFailure(t -> LOG.error("error creating consumer [client-id: {}]", getClientId(), t))
                .onSuccess(consumer -> kafkaConsumer = consumer)
                .compose(this::initConsumer)
                .onSuccess(c -> lifecycleStatus.setStarted());
        });
        return Future.succeededFuture();
    }

    private void logSubscribedTopicsWhenConsumerIsReady() {
        if (topicPattern != null) {
            if (subscribedTopicPatternTopics.size() <= 5) {
                LOG.debug("consumer started, subscribed to topic pattern [{}], matching topics: {}", topicPattern,
                        subscribedTopicPatternTopics);
            } else {
                LOG.debug("consumer started, subscribed to topic pattern [{}], matching {} topics", topicPattern,
                        subscribedTopicPatternTopics.size());
            }
        } else {
            LOG.debug("consumer started, subscribed to topics {}", topics);
        }
    }

    /**
     * Invoked when a new batch of records has been fetched as part of a poll() invocation.
     * <p>
     * This default implementation does nothing. Subclasses may override this method to implement specific handling.
     * <p>
     * Note that the usual record handling shouldn't be done here, but instead via the record handler
     * given in the constructor.
     *
     * @param records The fetched records.
     */
    protected void onBatchOfRecordsReceived(final KafkaConsumerRecords<String, V> records) {
        // do nothing by default
    }

    /**
     * Invoked when <em>respectTtl</em> is {@code true} and an expired record was received (meaning the
     * <em>recordHandler</em> isn't getting invoked for the record).
     * <p>
     * This default implementation does nothing. Subclasses may override this method to implement specific handling.
     *
     * @param record The received record which has expired.
     */
    protected void onRecordHandlerSkippedForExpiredRecord(final KafkaConsumerRecord<String, V> record) {
        // do nothing by default
    }

    private void installRebalanceListeners() {

        this.rebalanceListener = new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsAssigned(final Collection<org.apache.kafka.common.TopicPartition> partitions) {
                // invoked on the Kafka polling thread, not the event loop thread!
                final Set<TopicPartition> partitionsSet = Helper.from(partitions);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("partitions assigned: [{}]", HonoKafkaConsumerHelper.getPartitionsDebugString(partitions));
                }
                ensurePositionsHaveBeenSetIfNeeded(partitionsSet);
                updateSubscribedTopicPatternTopicsAndRemoveMetrics();
                if (recordFetchingPaused.get()) {
                    getUnderlyingConsumer().pause(partitions);
                }
                onPartitionsAssignedBlocking(partitionsSet);
                final Set<TopicPartition> allAssignedPartitions = Optional.ofNullable(onRebalanceDoneHandler)
                        .map(h -> Helper.from(getUnderlyingConsumer().assignment()))
                        .orElse(null);
                context.runOnContext(v -> {
                    HonoKafkaConsumer.this.onPartitionsAssigned(partitionsSet);
                    if (onRebalanceDoneHandler != null) {
                        onRebalanceDoneHandler.handle(allAssignedPartitions);
                    }
                });
            }

            @Override
            public void onPartitionsRevoked(final Collection<org.apache.kafka.common.TopicPartition> partitions) {
                // invoked on the Kafka polling thread, not the event loop thread!
                final Set<TopicPartition> partitionsSet = Helper.from(partitions);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("partitions revoked: [{}]", HonoKafkaConsumerHelper.getPartitionsDebugString(partitions));
                }
                onPartitionsRevokedBlocking(partitionsSet);
                context.runOnContext(v -> HonoKafkaConsumer.this.onPartitionsRevoked(partitionsSet));
            }

            @Override
            public void onPartitionsLost(final Collection<org.apache.kafka.common.TopicPartition> partitions) {
                // invoked on the Kafka polling thread, not the event loop thread!
                final Set<TopicPartition> partitionsSet = Helper.from(partitions);
                if (LOG.isInfoEnabled()) {
                    LOG.info("partitions lost: [{}] [client-id: {}]",
                            HonoKafkaConsumerHelper.getPartitionsDebugString(partitions), getClientId());
                }
                failAllSubscriptionUpdateTrackers(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                        "consumer error occurred"));
                onPartitionsLostBlocking(partitionsSet);
                context.runOnContext(v -> HonoKafkaConsumer.this.onPartitionsLost(partitionsSet));
            }
        };
        // apply workaround to set listeners working on the kafka polling thread
        replaceRebalanceListener(kafkaConsumer, rebalanceListener);
    }

    /**
     * Ensures that partition offset positions have been fetched and set internally according to either the last
     * committed state or the auto offset reset config ("latest" being relevant here) if no offset is committed yet.
     * <p>
     * This makes sure records published after this method returns are actually received by the consumer.
     * <p>
     * If auto offset reset config is set to "latest" and there already is a committed offset, but the record
     * corresponding to that committed offset has already been deleted, the partition offset position is reset to the
     * <em>beginning</em> offset here. The standard Kafka consumer behaviour would be resetting it to the <em>latest</em>
     * offset, but that would mean that rebalance operations (when this method is invoked) may cause records to be
     * skipped.
     * <p>
     * To be invoked on the Kafka polling thread on partition assignment.
     */
    private void ensurePositionsHaveBeenSetIfNeeded(final Set<TopicPartition> assignedPartitions) {
        // not needed if offset reset config set to "earliest", no need to wait for the retrieval of fetch positions in that case since consumer will receive all records anyway
        if (!assignedPartitions.isEmpty() && isAutoOffsetResetConfigLatest()) {
            LOG.trace("checking positions for {} newly assigned partitions...", assignedPartitions.size());
            final var partitions = Helper.to(assignedPartitions);
            // handle an exception across all position() invocations - the underlying server fetch that may trigger an exception is done for multiple partitions anyway
            try {
                final List<org.apache.kafka.common.TopicPartition> outOfRangeOffsetPartitions = new ArrayList<>();
                final var beginningOffsets = getUnderlyingConsumer().beginningOffsets(partitions);
                partitions.forEach(partition -> {
                    final long position = getUnderlyingConsumer().position(partition);
                    final Long beginningOffset = beginningOffsets.get(partition);
                    // check if position is valid
                    // (a check if position is larger than endOffset isn't done here, skipping the extra endOffset() invocation for this uncommon scenario and letting the KafkaConsumer consumer itself apply the latest offset later on)
                    if (beginningOffset != null && position < beginningOffset) {
                        LOG.debug("committed offset {} for [{}] is smaller than beginning offset, resetting it to the beginning offset {}",
                                position, partition, beginningOffset);
                        getUnderlyingConsumer().seek(partition, beginningOffset);
                        outOfRangeOffsetPartitions.add(partition);
                    }
                });
                if (!outOfRangeOffsetPartitions.isEmpty() && LOG.isInfoEnabled()) {
                    LOG.info("""
                            found out-of-range committed offsets, corresponding records having already been deleted; \
                            positions were reset to beginning offsets; partitions: [{}] [client-id: {}]\
                            """,
                            HonoKafkaConsumerHelper.getPartitionsDebugString(outOfRangeOffsetPartitions), getClientId());
                }
            } catch (final Exception e) {
                LOG.error("error checking positions for {} newly assigned partitions [client-id: {}]",
                        assignedPartitions.size(), getClientId(), e);
            }
            LOG.trace("done checking positions for {} newly assigned partitions", assignedPartitions.size());
        }
    }

    /**
     * Checks if a partition assignment strategy using cooperative rebalancing is used.
     *
     * @return {@code true} if cooperative rebalancing is used.
     */
    protected final boolean isCooperativeRebalancingConfigured() {
        // check if CooperativeStickyAssignor is configured (custom cooperative assignor not supported for now)
        return Optional.ofNullable(consumerConfig.get(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG))
                .map(value -> value.equals(CooperativeStickyAssignor.class.getName()))
                .orElse(false);
    }

    /**
     * Checks if the auto offset reset policy is set to "latest".
     *
     * @return {@code true} if "latest" offset reset policy is used.
     */
    protected final boolean isAutoOffsetResetConfigLatest() {
        return Optional.ofNullable(consumerConfig.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG))
                .map(value -> value.equals("latest"))
                .orElse(true);
    }

    /**
     * To be invoked on the Kafka polling thread on partition assignment.
     */
    private void updateSubscribedTopicPatternTopicsAndRemoveMetrics() {
        if (topicPattern != null) {
            final Set<String> oldSubscribedTopicPatternTopics = subscribedTopicPatternTopics;
            try {
                // Determine the set of subscribed topics as seen by this consumer. This set
                // might have changed due to the latest rebalancing.
                subscribedTopicPatternTopics = new HashSet<>(getUnderlyingConsumer().subscription());
                if (LOG.isTraceEnabled()) {
                    LOG.trace("subscribed topics: {}", String.join(", ", subscribedTopicPatternTopics));
                }
            } catch (final Exception e) {
                LOG.warn("error getting subscription", e);
            }
            synchronized (subscriptionUpdateTrackersForToBeAddedTopics) {
                if (!subscriptionUpdateTrackersForToBeAddedTopics.isEmpty()) {
                    final List<Handler<Void>> trackerCompletionHandlers = new ArrayList<>();
                    // determine all pending futures for topic names which this consumer now has a
                    // subscription for
                    for (final var iter = subscriptionUpdateTrackersForToBeAddedTopics.values().iterator(); iter.hasNext();) {
                        final var tracker = iter.next();
                        if (tracker.isContainedInSubscribedTopicsAfterRebalance(subscribedTopicPatternTopics)) {
                            LOG.trace("topic [{}] is now in subscribed topics list", tracker.getTopicName());
                            trackerCompletionHandlers.add(v -> tracker.complete());
                            iter.remove();
                        } else if (tracker.hasRebalancesLeft()) {
                            LOG.debug("topic [{}] is not in subscribed topics list, will wait for another rebalance", tracker.getTopicName());
                        } else {
                            LOG.info("topic [{}] is still not in subscribed topics list, not waiting for additional rebalance anymore", tracker.getTopicName());
                            trackerCompletionHandlers.add(v -> tracker.fail());
                            iter.remove();
                        }
                    }
                    if (!trackerCompletionHandlers.isEmpty()) {
                        // complete the futures on the Vert.x event loop so that client code does
                        // not block the Kafka Consumer's worker thread
                        trackerCompletionHandlers.forEach(handler -> runOnContext(v -> handler.handle(null)));
                    }
                    if (!subscriptionUpdateTrackersForToBeAddedTopics.isEmpty()) {
                        triggerTopicPatternSubscriptionUpdate();
                    }
                }
            }
            // check for deleted topics in order to remove corresponding metrics
            final Set<String> deletedTopics = oldSubscribedTopicPatternTopics.stream()
                    .filter(t -> !subscribedTopicPatternTopics.contains(t))
                    .collect(Collectors.toSet());
            if (!deletedTopics.isEmpty()) {
                // actual removal to be done with a delay, as there might still be unprocessed fetch response data
                // regarding these topics, in which case metrics would get re-created after they were removed
                runOnContext(v -> vertx.setTimer(OBSOLETE_METRICS_REMOVAL_DELAY_MILLIS, tid -> {
                    runOnKafkaWorkerThread(v2 -> {
                        removeMetricsForDeletedTopics(deletedTopics.stream()
                                .filter(t -> !subscribedTopicPatternTopics.contains(t)));
                    });
                }));
            }
        }
    }

    private Future<Void> initSubscriptionAndWaitForRebalance() {
        if (lifecycleStatus.isStopping() || lifecycleStatus.isStopped()) {
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "already stopped"));
        }
        final Promise<Void> partitionAssignmentDone = Promise.promise();
        initialPartitionAssignmentDonePromiseRef.set(partitionAssignmentDone);

        final Promise<Void> subscribeDonePromise = Promise.promise();
        if (topicPattern != null) {
            LOG.debug("subscribing to topics matching pattern: {}", topicPattern.pattern());
            kafkaConsumer.subscribe(topicPattern, subscribeDonePromise);
        } else {
            // Trigger retrieval of metadata for each of the subscription topics if not already available locally;
            // this will also trigger topic auto-creation if the topic doesn't exist yet.
            // Doing so before the "subscribe" invocation shall ensure that these partitions are considered for
            // partition assignment.
            topics.forEach(topic -> kafkaConsumer.partitionsFor(topic)
                    .onSuccess(partitions -> {
                        if (partitions.isEmpty()) {
                            // either auto-creation of topics is disabled or the (manually) created topic isn't reflected in the result yet
                            LOG.info("subscription topic doesn't exist as of now: {} [client-id: {}]", topic, getClientId());
                        }
                    }));
            kafkaConsumer.subscribe(topics, subscribeDonePromise);
        }
        // init kafkaConsumerWorker; it has to be retrieved after the first "subscribe" invocation
        kafkaConsumerWorker = getKafkaConsumerWorker(kafkaConsumer);
        return Future.all(subscribeDonePromise.future(), partitionAssignmentDone.future()).mapEmpty();
    }

    /**
     * A callback method that will be invoked after the partition re-assignment completes and before the consumer
     * starts fetching data.
     * <p>
     * NOTE: that this method will be invoked on the Kafka polling thread, not the vert.x event
     * loop thread!
     * <p>
     * This default implementation does nothing. Subclasses may override this method to query offsets of the assigned
     * partitions for example.
     *
     * @param partitionsSet The list of partitions that are now newly assigned to the consumer (previously owned
     *                      partitions will NOT be included, i.e. this list will only include newly added partitions)
     */
    protected void onPartitionsAssignedBlocking(final Set<TopicPartition> partitionsSet) {
        // do nothing by default
    }

    private void onPartitionsAssigned(final Set<TopicPartition> partitionsSet) {
        Optional.ofNullable(initialPartitionAssignmentDonePromiseRef.getAndSet(null))
                .ifPresent(Promise::tryComplete);
        if (onPartitionsAssignedHandler != null) {
            onPartitionsAssignedHandler.handle(partitionsSet);
        }
    }

    /**
     * A callback method that will be invoked during a rebalance operation when the consumer has to give up some
     * partitions.
     * <p>
     * NOTE: this method will be invoked on the Kafka polling thread, not the vert.x event loop thread!
     * <p>
     * This default implementation does nothing. Subclasses may override this method to commit partition offsets
     * synchronously for example.
     *
     * @param partitionsSet The list of partitions that previously were assigned to the consumer and now need to be
     *                      revoked.
     */
    protected void onPartitionsRevokedBlocking(final Set<TopicPartition> partitionsSet) {
        // do nothing by default
    }

    /**
     * A callback method that can be used to clean up resources for partitions that have already been reassigned to other
     * consumers. This method is only called in exceptional scenarios when the consumer realized that it does not own these
     * partitions any longer.
     * <p>
     * NOTE: this method will be invoked on the Kafka polling thread, not the vert.x event loop thread!
     * <p>
     * This default implementation does nothing. Subclasses may override this method.
     *
     * @param partitionsSet The list of partitions that are not assigned to this consumer any more.
     */
    protected void onPartitionsLostBlocking(final Set<TopicPartition> partitionsSet) {
        // do nothing by default
    }

    private void onPartitionsRevoked(final Set<TopicPartition> partitionsSet) {
        if (onPartitionsRevokedHandler != null) {
            onPartitionsRevokedHandler.handle(partitionsSet);
        }
    }

    private void onPartitionsLost(final Set<TopicPartition> partitionsSet) {
        if (onPartitionsLostHandler != null) {
            onPartitionsLostHandler.handle(partitionsSet);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Closes the Kafka consumer.
     *
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded once this component is stopped.
     */
    @Override
    public Future<Void> stop() {

        return lifecycleStatus.runStopAttempt(() -> {
            if (pollPauseTimeoutTimerId != null) {
                vertx.cancelTimer(pollPauseTimeoutTimerId);
                pollPauseTimeoutTimerId = null;
            }

            return Optional.ofNullable(kafkaConsumer)
                .map(consumer -> consumer.close()
                        .onSuccess(ok -> LOG.info("Kafka consumer stopped successfully"))
                        .onComplete(ar -> {
                            Optional.ofNullable(metricsSupport)
                                .ifPresent(ms -> ms.unregisterKafkaConsumer(kafkaConsumer.unwrap()));
                        }))
                .orElseGet(Future::succeededFuture)
                .onFailure(t -> LOG.info("error stopping Kafka consumer", t));
        });
    }

    /**
     * Runs code on the vert.x context used by the Kafka consumer.
     *
     * @param codeToRun The code to execute on the context.
     * @throws NullPointerException if codeToRun is {@code null}.
     */
    protected void runOnContext(final Handler<Void> codeToRun) {
        Objects.requireNonNull(codeToRun);
        if (context != Vertx.currentContext()) {
            context.runOnContext(go -> codeToRun.handle(null));
        } else {
            codeToRun.handle(null);
        }
    }

    /**
     * Runs the given handler on the Kafka polling thread.
     * <p>
     * The invocation of the handler is skipped if the this consumer is already closed.
     *
     * @param handler The handler to invoke.
     * @throws IllegalStateException if the corresponding executor service isn't available because no subscription
     *                               has been set yet on the Kafka consumer.
     * @throws NullPointerException if handler is {@code null}.
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    protected void runOnKafkaWorkerThread(final Handler<Void> handler) {
        Objects.requireNonNull(handler);
        if (kafkaConsumerWorker == null) {
            throw new IllegalStateException(MSG_CONSUMER_NOT_INITIALIZED_STARTED);
        }
        if (lifecycleStatus.isStarted()) {
            kafkaConsumerWorker.submit(() -> {
                if (lifecycleStatus.isStarted()) {
                    try {
                        handler.handle(null);
                    } catch (final Exception ex) {
                        LOG.error("error running task on Kafka worker thread [client-id: {}]", getClientId(), ex);
                    }
                }
            });
        }
    }

    /**
     * Gets the list of actual topics that match the topic pattern of this consumer.
     * Returns an empty list if this consumer doesn't use a topic pattern.
     *
     * @return The list of topics.
     */
    public final Set<String> getSubscribedTopicPatternTopics() {
        if (topicPattern == null) {
            return Set.of();
        }
        return new HashSet<>(subscribedTopicPatternTopics);
    }

    /**
     * Checks whether the given topic is one this consumer is subscribed to.
     * <p>
     * If this consumer uses a topic pattern subscription, this method checks whether the given
     * topic is part of the list of actual topics the pattern has been evaluated to.
     * <p>
     * Note that this does not necessarily mean that this consumer will actually receive messages
     * for the topic - the topic partitions could currently be assigned to another consumer in the
     * same consumer group.
     *
     * @param topic The topic to check.
     * @return {@code true} if the topic is among the subscribed topics.
     * @throws NullPointerException if topic is {@code null}.
     */
    public final boolean isAmongKnownSubscribedTopics(final String topic) {
        Objects.requireNonNull(topic);
        if (topics != null) {
            return topics.contains(topic);
        }
        return subscribedTopicPatternTopics.contains(topic);
    }

    /**
     * Tries to ensure that the given topic is part of the list of topics this consumer is subscribed to.
     * <p>
     * The successful completion of the returned Future means that the topic exists and is among the subscribed topics.
     * It also means that if partitions of this topic have been assigned to this consumer and if the offset reset config
     * is set to "latest", the positions for these partitions have already been fetched and the consumer will receive
     * records published thereafter.
     * <p>
     * Note that this method is only applicable if a topic pattern subscription is used, otherwise an
     * {@link IllegalStateException} is thrown.
     * <p>
     * This method is needed for scenarios where the given topic either has just been created and this consumer doesn't
     * know about it yet or the topic doesn't exist yet. In the latter case, this method will try to trigger creation of
     * the topic, which may succeed if topic auto-creation is enabled, and wait for the following rebalance to check
     * if the topic is part of the subscribed topics then.
     *
     * @param topic The topic to use.
     * @return A future indicating the outcome of the operation. The Future is succeeded if the topic exists and
     *         is among the subscribed topics. The Future is failed with a {@link ServerErrorException} if the topic
     *         doesn't exist or there was an error determining whether the topic is part of the subscription.
     * @throws IllegalArgumentException If this consumer doesn't use a topic pattern or the topic doesn't match the pattern.
     * @throws IllegalStateException if the consumer hasn't been created with a set of topics to consume from.
     * @throws NullPointerException if topic is {@code null}.
     */
    public final Future<Void> ensureTopicIsAmongSubscribedTopicPatternTopics(final String topic) {
        Objects.requireNonNull(topic);
        if (topics != null) {
            throw new IllegalStateException("consumer doesn't use topic pattern");
        } else if (!topicPattern.matcher(topic).find()) {
            throw new IllegalArgumentException("topic doesn't match pattern");
        }
        if (!lifecycleStatus.isStarted()) {
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, "not started"));
        }
        // check whether topic exists and its existence has been applied to the wildcard subscription yet;
        // use previously updated topics list (less costly than invoking kafkaConsumer.subscription() here)
        if (subscribedTopicPatternTopics.contains(topic)) {
            LOG.debug("ensureTopicIsAmongSubscribedTopics: topic is already subscribed [{}]", topic);
            return Future.succeededFuture();
        }

        synchronized (subscriptionUpdateTrackersForToBeAddedTopics) {
            final var tracker = new SubscriptionUpdateTracker(topic);

            return Optional.ofNullable(subscriptionUpdateTrackersForToBeAddedTopics.putIfAbsent(topic, tracker))
                    .map(previousTopicTracker -> {
                        LOG.debug("ensureTopicIsAmongSubscribedTopics: will wait for ongoing invocation to complete [{}]", topic);
                        return previousTopicTracker.outcome();
                    }).orElseGet(() -> {
                        triggerTopicPatternSubscriptionUpdate();
                        return tracker.outcome();
                    });
        }
    }

    private void triggerTopicPatternSubscriptionUpdate() {
        if (!subscriptionUpdateTriggered.compareAndSet(false, true)) {
            LOG.debug("ensureTopicIsAmongSubscribedTopics: subscription update already triggered");
            return;
        }
        runOnKafkaWorkerThread(v -> {
            subscriptionUpdateTriggered.set(false);
            synchronized (subscriptionUpdateTrackersForToBeAddedTopics) {
                if (subscriptionUpdateTrackersForToBeAddedTopics.isEmpty()) {
                    return;
                }
                // check topics that we want to be in the subscribed-topics list after the rebalance
                for (final var iter = subscriptionUpdateTrackersForToBeAddedTopics.values().iterator(); iter.hasNext();) {
                    final var tracker = iter.next();
                    LOG.trace("triggerTopicPatternSubscriptionUpdate: check for topic [{}]", tracker.getTopicName());
                    try {
                        // check whether topic exists, if not, potentially auto-creating it here implicitly
                        // (provided that the broker's "auto.create.topics.enable" config property is true)
                        if (getUnderlyingConsumer().partitionsFor(tracker.getTopicName()).isEmpty()) {
                            LOG.debug("triggerTopicPatternSubscriptionUpdate: topic doesn't exist yet: {}", tracker.getTopicName());
                        }
                    } catch (final Exception e) {
                        LOG.warn("triggerTopicPatternSubscriptionUpdate: error getting partitions for topic [{}]", tracker.getTopicName(), e);
                        iter.remove();
                        runOnContext(v2 -> tracker.fail(e));
                    }
                }
                if (!subscriptionUpdateTrackersForToBeAddedTopics.isEmpty()) {
                    LOG.trace("triggerTopicPatternSubscriptionUpdate: subscribe");
                    try {
                        LOG.info("triggering refresh of subscribed topic list ...");
                        getUnderlyingConsumer().subscribe(topicPattern, rebalanceListener);
                        if (!metadataMaxAge.isPresent() || metadataMaxAge.get() > THRESHOLD_METADATA_MAX_AGE_MS) {
                            // Partitions of newly created topics are being assigned by means of
                            // a rebalance. We make sure the rebalancing happens during the next poll()
                            // operation in order to not having to wait for the metadata to become stale
                            // which, by default, takes 5 minutes (if not overridden by the consumer's
                            // "metadata.max.age.ms" config property)
                            LOG.info("enforcing rebalance on next poll()");
                            getUnderlyingConsumer().enforceRebalance("trigger assignment of new topic partitions");
                        }
                    } catch (final Exception e) {
                        LOG.warn("triggerTopicPatternSubscriptionUpdate: error updating subscription", e);
                        failAllSubscriptionUpdateTrackers(e);
                    }
                }
            }
        });
    }

    private void failAllSubscriptionUpdateTrackers(final Exception failure) {
        final List<SubscriptionUpdateTracker> toBeFailedTrackers = new ArrayList<>();
        synchronized (subscriptionUpdateTrackersForToBeAddedTopics) {
            toBeFailedTrackers.addAll(subscriptionUpdateTrackersForToBeAddedTopics.values());
            subscriptionUpdateTrackersForToBeAddedTopics.clear();
        }
        toBeFailedTrackers.forEach(tracker -> {
            runOnContext(v -> tracker.fail(failure));
        });
    }

    private void removeMetricsForDeletedTopics(final Stream<String> deletedTopics) {
        final Metrics metrics = getInternalMetricsObject(kafkaConsumer.unwrap());
        if (metrics != null) {
            deletedTopics.forEach(topic -> {
                metrics.removeSensor("topic." + topic + ".bytes-fetched");
                metrics.removeSensor("topic." + topic + ".records-fetched");
            });
        }
    }

    private Metrics getInternalMetricsObject(final Consumer<String, V> consumer) {
        if (consumer instanceof org.apache.kafka.clients.consumer.KafkaConsumer) {
            try {
                final Field field = org.apache.kafka.clients.consumer.KafkaConsumer.class.getDeclaredField("metrics");
                field.setAccessible(true);
                return (Metrics) field.get(consumer);
            } catch (final Exception e) {
                LOG.warn("failed to get metrics object", e);
            }
        }
        return null;
    }

    /**
     * Replaces the internal rebalance listener of the given vert.x KafkaConsumer with the given one via reflection.
     * <p>
     * This is a workaround for enabling an <em>onPartitionsRevokedHandler</em> to be invoked in a blocking
     * fashion on the vert.x worker thread that the KafkaConsumer is using, e.g. to do a manual offset commit
     * via <em>commitSync</em> before the new partition assignment is in place. Without this modification, a handler
     * set via {@link KafkaConsumer#partitionsRevokedHandler(Handler)} will always be invoked asynchronously on the
     * vert.x event loop thread, which could mean that the new partition assignment is already done by then.
     *
     * @param consumer The consumer to replace the rebalance listener in.
     * @param listener The rebalance listener to set.
     */
    private void replaceRebalanceListener(
            final KafkaConsumer<String, V> consumer,
            final ConsumerRebalanceListener listener) {

        try {
            final Field field = KafkaReadStreamImpl.class.getDeclaredField("rebalanceListener");
            field.setAccessible(true);
            field.set(consumer.asStream(), listener);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Failed to adapt rebalance listener", e);
        }
    }

    /**
     * Gets the executor service for running tasks on the Kafka polling thread.
     * <p>
     * Using this worker thread is needed to commit partition offsets synchronously during a rebalance for example.
     *
     * @param consumer The Kafka consumer to get the worker from.
     * @return The worker thread executor service.
     * @throws IllegalStateException if the corresponding executor service isn't available because no subscription
     *                               has been set yet on the Kafka consumer.
     */
    private ExecutorService getKafkaConsumerWorker(final KafkaConsumer<String, V> consumer) {
        final ExecutorService worker;
        try {
            final Field field = KafkaReadStreamImpl.class.getDeclaredField("worker");
            field.setAccessible(true);
            worker = (ExecutorService) field.get(consumer.asStream());
        } catch (final Exception e) {
            throw new IllegalArgumentException("Failed to get worker", e);
        }
        if (worker == null) {
            throw new IllegalStateException("worker not set");
        }
        return worker;
    }

    private final class SubscriptionUpdateTracker {
        private final Promise<Void> outcome = Promise.promise();
        private final String topicName;
        private final AtomicInteger rebalancesLeft = new AtomicInteger(10);

        /**
         * Creates a new tracker for a topic name.
         */
        SubscriptionUpdateTracker(final String topicName) {
            this.topicName = Objects.requireNonNull(topicName);
        }

        Future<Void> outcome() {
            return this.outcome.future();
        }

        String getTopicName() {
            return topicName;
        }

        boolean isContainedInSubscribedTopicsAfterRebalance(final Set<String> topics) {
            rebalancesLeft.decrementAndGet();
            return topics.contains(topicName);
        }

        boolean hasRebalancesLeft() {
            return rebalancesLeft.get() > 0;
        }

        void complete() {
            this.outcome.tryComplete();
        }

        void fail() {
            this.fail(new ServerErrorException(
                    HttpURLConnection.HTTP_UNAVAILABLE,
                    "could not create topic %s".formatted(topicName)));
        }

        void fail(final Throwable cause) {
            this.outcome.tryFail(cause);
        }
    }
}
