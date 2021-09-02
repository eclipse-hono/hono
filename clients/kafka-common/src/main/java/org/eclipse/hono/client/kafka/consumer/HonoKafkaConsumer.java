/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.client.kafka.consumer;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Collection;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.util.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.common.PartitionInfo;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.common.impl.Helper;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.consumer.impl.KafkaReadStreamImpl;

/**
 * A consumer receiving records from a Kafka cluster.
 * <p>
 * Wraps a vert.x Kafka consumer while implementing the Hono {@link Lifecycle} interface,
 * letting records be consumed by a given handler after the {@link #start()} method has been
 * called.
 */
public class HonoKafkaConsumer implements Lifecycle {

    /**
     * Timeout used waiting for a rebalance after a subscription was updated.
     */
    private static final long WAIT_FOR_REBALANCE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);
    /**
     * Default value for 'max.poll.interval.ms', i.e. the maximum delay between invocations of poll(), also
     * the time commit() will block when retrying the commit on an UNKNOWN_TOPIC_OR_PARTITION error.
     * Kafka consumer default is 5min. Since the vert.x kafka consumer continuously polls, we can set this
     * to a much lower value.
     */
    private static final String DEFAULT_MAX_POLL_INTERNAL_MS = Long.toString(TimeUnit.SECONDS.toMillis(20));

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final Vertx vertx;
    protected final AtomicBoolean stopCalled = new AtomicBoolean();

    private final Map<String, String> consumerConfig;
    private final AtomicReference<Promise<Void>> subscribeDonePromiseRef = new AtomicReference<>();
    private final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler;
    private final Set<String> topics;
    private final Pattern topicPattern;
    private final AtomicBoolean paused = new AtomicBoolean();

    private KafkaConsumer<String, Buffer> kafkaConsumer;
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
    private Handler<Set<TopicPartition>> onPartitionsAssignedHandler;
    private Handler<Set<TopicPartition>> onRebalanceDoneHandler;
    private Handler<Set<TopicPartition>> onPartitionsRevokedHandler;
    private boolean respectTtl = true;
    private Supplier<Consumer<String, Buffer>> kafkaConsumerSupplier;

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
            final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler,
            final Map<String, String> consumerConfig) {
        this(vertx, Objects.requireNonNull(topics), null, recordHandler, consumerConfig);
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
            final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler,
            final Map<String, String> consumerConfig) {
        this(vertx, null, Objects.requireNonNull(topicPattern), recordHandler, consumerConfig);
    }

    /**
     * Creates a consumer to receive records on topics defined either via a topic list or a topic pattern.
     *
     * @param vertx The Vert.x instance to use.
     * @param topics The Kafka topic to consume records from ({@code null} if topicPattern is set).
     * @param topicPattern The pattern of Kafka topic names to consume records from ({@code null} if topics is set).
     * @param recordHandler The handler to be invoked for each received record.
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
            final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler,
            final Map<String, String> consumerConfig) {

        this.vertx = Objects.requireNonNull(vertx);
        this.topics = topics;
        this.topicPattern = topicPattern;
        this.recordHandler = Objects.requireNonNull(recordHandler);
        this.consumerConfig = Objects.requireNonNull(consumerConfig);

        if ((topics == null) == (topicPattern == null)) {
            throw new NullPointerException("either topics or topicPattern has to be set");
        }
        if (!consumerConfig.containsKey(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG)) {
            consumerConfig.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, DEFAULT_MAX_POLL_INTERNAL_MS);
        }
        if (!consumerConfig.containsKey(ConsumerConfig.GROUP_ID_CONFIG)) {
            if ("true".equals(consumerConfig.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG))) {
                throw new IllegalArgumentException(ConsumerConfig.GROUP_ID_CONFIG + " config entry has to be set if auto-commit is enabled");
            }
            log.trace("no group.id set, using a random UUID as default and disabling auto-commit");
            consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
            consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
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
     * Defines whether records that contain a <em>ttl</em> header where the time-to-live has elapsed should be ignored.
     * <p>
     * The default is true.
     *
     * @param respectTtl The intended behaviour: if true, messages with elapsed ttl are silently dropped and the message
     *            handler is not invoked.
     */
    public final void setRespectTtl(final boolean respectTtl) {
        this.respectTtl = respectTtl;
    }

    /**
     * Only to be used for unit tests.
     * @param supplier Supplier creating the internal Kafka consumer.
     */
    public void setKafkaConsumerSupplier(final Supplier<Consumer<String, Buffer>> supplier) {
        kafkaConsumerSupplier = supplier;
    }

    /**
     * Pauses the consumer polling operation (if not already paused).
     *
     * @return {@code true} if the consumer polling operation was active before and is now paused.
     */
    public final boolean pause() {
        if (!paused.compareAndSet(false, true)) {
            return false;
        }
        getKafkaConsumer().pause();
        return true;
    }

    /**
     * Resumes the consumer polling operation (if paused).
     *
     * @return {@code true} if the consumer polling operation was paused and is now resumed.
     */
    public final boolean resume() {
        if (!paused.compareAndSet(true, false)) {
            return false;
        }
        getKafkaConsumer().resume();
        return true;
    }

    /**
     * Checks if the consumer polling operation currently is paused.
     *
     * @return {@code true} if the consumer polling operation is paused.
     */
    public final boolean isPaused() {
        return paused.get();
    }

    /**
     * Gets the used vert.x KafkaConsumer.
     *
     * @return The Kafka consumer.
     * @throws IllegalStateException if invoked before the KafkaConsumer is set via the {@link #start()} method.
     */
    protected final KafkaConsumer<String, Buffer> getKafkaConsumer() {
        if (kafkaConsumer == null) {
            throw new IllegalStateException("consumer not initialized/started");
        }
        return kafkaConsumer;
    }

    @Override
    public Future<Void> start() {
        context = vertx.getOrCreateContext();
        final Promise<Void> startPromise = Promise.promise();
        runOnContext(v -> {
            // create KafkaConsumer here so that it is created in the Vert.x context of the start() method (KafkaConsumer uses vertx.getOrCreateContext())
            kafkaConsumer = Optional.ofNullable(kafkaConsumerSupplier)
                    .map(supplier -> KafkaConsumer.create(vertx, supplier.get()))
                    .orElseGet(() -> KafkaConsumer.create(vertx, consumerConfig, String.class, Buffer.class));
            kafkaConsumer.handler(record -> {
                if (!startPromise.future().isComplete()) {
                    log.debug("postponing record handling until start() is completed [topic: {}, partition: {}, offset: {}]",
                            record.topic(), record.partition(), record.offset());
                }
                startPromise.future().onSuccess(v2 -> {
                    if (respectTtl && KafkaRecordHelper.isTtlElapsed(record.headers())) {
                        onRecordHandlerSkippedForExpiredRecord(record);
                    } else {
                        try {
                            recordHandler.handle(record);
                        } catch (final Exception e) {
                            log.warn("error handling record [topic: {}, partition: {}, offset: {}, headers: {}]",
                                    record.topic(), record.partition(), record.offset(), record.headers(), e);
                        }
                    }
                });
            });
            kafkaConsumer.exceptionHandler(error -> log.error("consumer error occurred", error));
            installRebalanceListeners();
            // subscribe and wait for rebalance to make sure that when start() completes,
            // the consumer is actually ready to receive records already
            kafkaConsumer.asStream().pollTimeout(Duration.ofMillis(10)); // let polls finish quickly until start() is completed
            subscribeAndWaitForRebalance()
                    .onSuccess(v2 -> {
                        kafkaConsumer.asStream().pollTimeout(POLL_TIMEOUT);
                        logSubscribedTopicsOnStartComplete();
                    })
                    .onComplete(startPromise.future());
        });
        return startPromise.future();
    }

    /**
     * Ensures that partition offset positions have been set according to either the last committed state
     * or the auto offset reset config ("latest" being relevant here) if no offset is committed yet.
     * <p>
     * This makes sure records published after this method returns are actually received by the consumer.
     * <p>
     * To be invoked on the Kafka polling thread on partition assignment.
     */
    private void ensurePositionsHaveBeenSetIfNeeded(final Set<TopicPartition> assignedPartitions) {
        // not needed if offset reset config set to "earliest", no need to wait for the retrieval of fetch positions in that case since consumer will receive all records anyway
        if (!"earliest".equals(consumerConfig.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG))) {
            // check if among the assigned partitions there is one of a newly created topic;
            // take the first such partition to fetch its position, thereby triggering a complete update of fetch positions (via KafkaConsumer#updateFetchPositions())
            assignedPartitions.stream()
                    .filter(tp -> !subscribedTopicPatternTopics.contains(tp.getTopic()))
                    .findFirst()
                    .ifPresent(firstNewTopicPartition -> {
                        log.trace("triggering update of fetch positions (via fetch of position for new {})...", firstNewTopicPartition);
                        try {
                            kafkaConsumer.asStream().unwrap().position(Helper.to(firstNewTopicPartition));
                        } catch (final Exception e) {
                            log.warn("error fetching position for {}: {}", firstNewTopicPartition, e.toString());
                        }
                        log.trace("done triggering update of fetch positions (via fetch of position for new {})", firstNewTopicPartition);
                    });
        }
    }

    private void logSubscribedTopicsOnStartComplete() {
        if (topicPattern != null) {
            if (subscribedTopicPatternTopics.size() <= 5) {
                log.debug("consumer started, subscribed to topic pattern [{}], matching topics: {}", topicPattern,
                        subscribedTopicPatternTopics);
            } else {
                log.debug("consumer started, subscribed to topic pattern [{}], matching {} topics", topicPattern,
                        subscribedTopicPatternTopics.size());
            }
        } else {
            log.debug("consumer started, subscribed to topics {}", topics);
        }
    }

    /**
     * Invoked when <em>respectTtl</em> is {@code true} and an expired record was received (meaning the
     * <em>recordHandler</em> isn't getting invoked for the record).
     * <p>
     * This default implementation does nothing. Subclasses may override this method to implement specific handling.
     *
     * @param record The received record which has expired.
     */
    protected void onRecordHandlerSkippedForExpiredRecord(final KafkaConsumerRecord<String, Buffer> record) {
        // do nothing by default
    }

    private void installRebalanceListeners() {
        // apply workaround to set listeners working on the kafka polling thread
        replaceRebalanceListener(kafkaConsumer, new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsAssigned(final Collection<org.apache.kafka.common.TopicPartition> partitions) {
                // invoked on the Kafka polling thread, not the event loop thread!
                final Set<TopicPartition> partitionsSet = Helper.from(partitions);
                if (log.isDebugEnabled()) {
                    log.debug("partitions assigned: [{}]", HonoKafkaConsumerHelper.getPartitionsDebugString(partitions));
                }
                ensurePositionsHaveBeenSetIfNeeded(partitionsSet);
                onPartitionsAssignedBlocking(partitionsSet);
                final Set<TopicPartition> allAssignedPartitions = Optional.ofNullable(onRebalanceDoneHandler)
                        .map(h -> Helper.from(getKafkaConsumer().asStream().unwrap().assignment()))
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
                if (log.isDebugEnabled()) {
                    log.debug("partitions revoked: [{}]", HonoKafkaConsumerHelper.getPartitionsDebugString(partitions));
                }
                onPartitionsRevokedBlocking(partitionsSet);
                context.runOnContext(v -> HonoKafkaConsumer.this.onPartitionsRevoked(partitionsSet));
            }

            @Override // override default implementation (which just calls onPartitionsRevoked()) to log this situation specifically
            public void onPartitionsLost(final Collection<org.apache.kafka.common.TopicPartition> partitions) {
                // invoked on the Kafka polling thread, not the event loop thread!
                final Set<TopicPartition> partitionsSet = Helper.from(partitions);
                if (log.isInfoEnabled()) {
                    log.info("partitions lost: [{}]", HonoKafkaConsumerHelper.getPartitionsDebugString(partitions));
                }
                onPartitionsRevokedBlocking(partitionsSet);
                context.runOnContext(v -> HonoKafkaConsumer.this.onPartitionsRevoked(partitionsSet));
            }
        });
    }

    private Future<Void> subscribeAndWaitForRebalance() {
        if (stopCalled.get()) {
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "already stopped"));
        }
        final Promise<Void> subscribeDonePromise = subscribeDonePromiseRef
                .updateAndGet(promise -> promise == null ? Promise.promise() : promise);
        final Promise<Void> subscriptionPromise = Promise.promise();
        if (topicPattern != null) {
            kafkaConsumer.subscribe(topicPattern, subscriptionPromise);
        } else {
            // Trigger retrieval of metadata for each of the subscription topics if not already available locally;
            // this will also trigger topic auto-creation if the topic doesn't exist yet.
            // Doing so before the "subscribe" invocation shall ensure that these partitions are considered for
            // partition assignment.
            topics.forEach(topic -> {
                final Promise<List<PartitionInfo>> partitionsForFuture = Promise.promise();
                partitionsForFuture.future()
                        .onSuccess(partitions -> {
                            if (partitions.isEmpty()) {
                                log.info("subscription topic doesn't exist and didn't get auto-created: {}", topic);
                            }
                        });
                HonoKafkaConsumerHelper.partitionsFor(kafkaConsumer, topic, partitionsForFuture);
            });

            kafkaConsumer.subscribe(topics, subscriptionPromise);
        }
        // kafkaConsumerWorker has to be retrieved after the first "subscribe" invocation
        if (kafkaConsumerWorker == null) {
            kafkaConsumerWorker = getKafkaConsumerWorker(kafkaConsumer);
        }
        vertx.setTimer(WAIT_FOR_REBALANCE_TIMEOUT, ar -> {
            if (!subscribeDonePromise.future().isComplete()) {
                final String errorMsg = "timed out waiting for rebalance and update of subscribed topics";
                log.warn(errorMsg);
                subscribeDonePromise.tryFail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, errorMsg));
            }
        });
        return CompositeFuture.all(subscriptionPromise.future(), subscribeDonePromise.future()).mapEmpty();
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
        if (topicPattern != null) {
            // update subscribedTopicPatternTopics (subscription() will return the actual topics, i.e. not the topic pattern if one is used)
            final Promise<Set<String>> subscriptionResultPromise = Promise.promise();
            kafkaConsumer.subscription(subscriptionResultPromise);
            subscriptionResultPromise.future()
                    .onSuccess(result -> subscribedTopicPatternTopics = new HashSet<>(subscriptionResultPromise.future().result()))
                    .onFailure(thr -> log.info("failed to get subscription", thr))
                    .map((Void) null)
                    .onComplete(ar -> Optional.ofNullable(subscribeDonePromiseRef.getAndSet(null))
                            .ifPresent(promise -> tryCompletePromise(promise, ar)));
        } else {
            // fixed topic list used: calling kafkaConsumer.subscription() here would be of no use since it always
            // returns the topics used in the kafkaConsumer.subscribe(Collection) call, regardless of whether they exist
            Optional.ofNullable(subscribeDonePromiseRef.getAndSet(null))
                    .ifPresent(Promise::tryComplete);
        }
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

    private void onPartitionsRevoked(final Set<TopicPartition> partitionsSet) {
        if (onPartitionsRevokedHandler != null) {
            onPartitionsRevokedHandler.handle(partitionsSet);
        }
    }

    @Override
    public Future<Void> stop() {
        if (kafkaConsumer == null) {
            return Future.failedFuture("not started");
        } else if (!stopCalled.compareAndSet(false, true)) {
            log.trace("stop already called");
            return Future.succeededFuture();
        }
        final Promise<Void> consumerClosePromise = Promise.promise();
        kafkaConsumer.close(consumerClosePromise);
        return consumerClosePromise.future();
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
    protected void runOnKafkaWorkerThread(final Handler<Void> handler) {
        Objects.requireNonNull(handler);
        if (kafkaConsumerWorker == null) {
            throw new IllegalStateException("consumer not initialized/started");
        }
        if (!stopCalled.get()) {
            kafkaConsumerWorker.submit(() -> {
                if (!stopCalled.get()) {
                    try {
                        handler.handle(null);
                    } catch (final Exception ex) {
                        log.error("error running task on Kafka worker thread", ex);
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
     * It also means that if partitions of this topic have been assigned to this consumer, the positions for these
     * partitions have already been fetched and the consumer will receive records published thereafter.
     * <p>
     * Note that this method is only applicable if a topic pattern subscription is used, otherwise an
     * {@link IllegalStateException} is thrown.
     * <p>
     * This is relevant if the topic either has just been created and this consumer doesn't know about it yet or
     * the topic doesn't exist yet. In the latter case, this method will try to trigger creation of the topic,
     * which may succeed if "auto.create.topics.enable" is true, and wait for the following rebalance to check
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
        if (kafkaConsumer == null) {
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, "not started"));
        } else if (stopCalled.get()) {
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "already stopped"));
        }
        // check whether topic exists and its existence has been applied to the wildcard subscription yet;
        // use previously updated topics list (less costly than invoking kafkaConsumer.subscription() here)
        if (subscribedTopicPatternTopics.contains(topic)) {
            log.debug("ensureTopicIsAmongSubscribedTopics: topic is already subscribed [{}]", topic);
            return Future.succeededFuture();
        }
        final Promise<List<PartitionInfo>> topicCheckFuture = Promise.promise();
        // check whether topic has been created since the last rebalance
        // and if not, potentially create it here implicitly
        // (partitionsFor() will create the topic if it doesn't exist, provided "auto.create.topics.enable" is true)
        HonoKafkaConsumerHelper.partitionsFor(kafkaConsumer, topic, topicCheckFuture);
        topicCheckFuture.future()
                .onFailure(thr -> log.warn("ensureTopicIsAmongSubscribedTopics: error getting partitions for topic [{}]", topic, thr))
                .onSuccess(partitions -> {
                    if (partitions.isEmpty()) {
                        log.warn("ensureTopicIsAmongSubscribedTopics: topic doesn't exist and didn't get auto-created: {}", topic);
                    }
                });
        // the topic list of a wildcard subscription only gets refreshed periodically by default (interval is defined by "metadata.max.age.ms");
        // therefore enforce a refresh here by again subscribing to the topic pattern
        log.debug("ensureTopicIsAmongSubscribedTopics: wait for subscription update and rebalance [{}]", topic);
        return subscribeAndWaitForRebalance()
                .compose(v -> {
                    if (!subscribedTopicPatternTopics.contains(topic)) {
                        // first metadata refresh could have failed with a LEADER_NOT_AVAILABLE error for the new topic;
                        // seems to happen when some other topics have just been deleted for example
                        log.debug("ensureTopicIsAmongSubscribedTopics: subscription not updated with topic after rebalance; try again [topic: {}]", topic);
                        return subscribeAndWaitForRebalance();
                    }
                    return Future.succeededFuture(v);
                })
                .compose(v -> {
                    if (!subscribedTopicPatternTopics.contains(topic)) {
                        log.warn("ensureTopicIsAmongSubscribedTopics: subscription not updated with topic after rebalance [topic: {}]", topic);
                        return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                                "subscription not updated with topic after rebalance"));
                    }
                    log.debug("ensureTopicIsAmongSubscribedTopics: done updating topic subscription [{}]", topic);
                    return Future.succeededFuture(v);
                });
    }

    private static <T> void tryCompletePromise(final Promise<T> promise, final AsyncResult<T> asyncResult) {
        if (asyncResult.succeeded()) {
            promise.tryComplete(asyncResult.result());
        } else {
            promise.tryFail(asyncResult.cause());
        }
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
    private static void replaceRebalanceListener(final KafkaConsumer<String, Buffer> consumer,
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
    private static ExecutorService getKafkaConsumerWorker(final KafkaConsumer<String, Buffer> consumer) {
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
}
