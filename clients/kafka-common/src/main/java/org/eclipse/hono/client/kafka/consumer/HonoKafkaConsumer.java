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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final long WAIT_FOR_REBALANCE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);
    /**
     * Default value for 'max.poll.interval.ms', i.e. the maximum delay between invocations of poll(), also
     * the time commit() will block when retrying the commit on an UNKNOWN_TOPIC_OR_PARTITION error.
     * Kafka consumer default is 5min. Since the vert.x kafka consumer continuously polls, we can set this
     * to a much lower value.
     */
    private static final String DEFAULT_MAX_POLL_INTERNAL_MS = Long.toString(TimeUnit.SECONDS.toMillis(20));

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final Vertx vertx;
    protected final AtomicBoolean stopped = new AtomicBoolean();

    private final Supplier<KafkaConsumer<String, Buffer>> consumerCreator;
    private final AtomicReference<Promise<Void>> subscribeDonePromiseRef = new AtomicReference<>();
    private final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler;
    private final Set<String> topics;
    private final Pattern topicPattern;

    private KafkaConsumer<String, Buffer> kafkaConsumer;
    /**
     * The ver.x context the KafkaConsumer runs in.
     */
    private Context context;
    /**
     * Currently subscribed topics matching the topic pattern (if set).
     * Note that these are not (necessarily) the topics that this particular kafkaConsumer
     * here will receive messages for.
     */
    private Set<String> subscribedTopicPatternTopics = new HashSet<>();
    private Handler<Set<TopicPartition>> onPartitionsAssignedHandler;
    private Handler<Set<TopicPartition>> onPartitionsRevokedHandler;
    private Handler<Set<TopicPartition>> blockingOnPartitionsRevokedHandler;

    /**
     * Creates a consumer to receive records on the given topics.
     *
     * @param vertx The Vert.x instance to use.
     * @param topics The Kafka topic to consume records from.
     * @param recordHandler The handler to be invoked for each received record.
     * @param consumerConfig The Kafka consumer configuration.
     * @throws NullPointerException if any of the parameters is {@code null}.
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
     */
    public HonoKafkaConsumer(
            final Vertx vertx,
            final Pattern topicPattern,
            final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler,
            final Map<String, String> consumerConfig) {
        this(vertx, null, Objects.requireNonNull(topicPattern), recordHandler, consumerConfig);
    }

    private HonoKafkaConsumer(
            final Vertx vertx,
            final Set<String> topics,
            final Pattern topicPattern,
            final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler,
            final Map<String, String> consumerConfig) {

        this.vertx = Objects.requireNonNull(vertx);
        this.topics = topics;
        this.topicPattern = topicPattern;
        this.recordHandler = Objects.requireNonNull(recordHandler);
        Objects.requireNonNull(consumerConfig);

        if (!consumerConfig.containsKey(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG)) {
            consumerConfig.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, DEFAULT_MAX_POLL_INTERNAL_MS);
        }
        if (!consumerConfig.containsKey(ConsumerConfig.GROUP_ID_CONFIG)) {
            log.trace("no group.id set, using a random UUID as default");
            consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        }
        consumerCreator = () -> KafkaConsumer.create(vertx, consumerConfig, String.class, Buffer.class);
    }

    /**
     * Sets a handler to be invoked on the vert.x event loop thread
     * when partitions are about to be assigned as part of a rebalance.
     *
     * @param onPartitionsAssignedHandler The handler to be invoked.
     */
    public final void setOnPartitionsAssignedHandler(final Handler<Set<TopicPartition>> onPartitionsAssignedHandler) {
        this.onPartitionsAssignedHandler = Objects.requireNonNull(onPartitionsAssignedHandler);
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
     * Sets a handler to be invoked when partitions are about to be revoked as part of a rebalance.
     * <p>
     * This handler will be invoked on the Kafka thread of the consumer and can be used to commit partition offset
     * synchronously.
     *
     * @param blockingOnPartitionsRevokedHandler The handler to be invoked.
     * @throws IllegalStateException if invoked after {@link #start()} has been called.
     */
    protected final void setBlockingOnPartitionsRevokedHandler(final Handler<Set<TopicPartition>> blockingOnPartitionsRevokedHandler) {
        if (kafkaConsumer != null) {
            throw new IllegalStateException("must be called before the consumer is started");
        }
        this.blockingOnPartitionsRevokedHandler = Objects.requireNonNull(blockingOnPartitionsRevokedHandler);
    }

    /**
     * Only to be used for unit tests.
     * @param context The vert.x context to use.
     */
    void setContext(final Context context) {
        this.context = context;
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
        if (context == null) {
            context = Vertx.currentContext();
            if (context == null) {
                return Future.failedFuture(new IllegalStateException("consumer must be started in a Vert.x context"));
            }
        }
        // create KafkaConsumer here so that it is created in the Vert.x context of the start() method (KafkaConsumer uses vertx.getOrCreateContext())
        kafkaConsumer = consumerCreator.get();
        kafkaConsumer.handler(recordHandler);
        kafkaConsumer.exceptionHandler(error -> log.error("consumer error occurred", error));
        installRebalanceListeners();
        return subscribeAndWaitForRebalance()
                .map(ok -> {
                    if (topicPattern != null) {
                        if (subscribedTopicPatternTopics.size() <= 5) {
                            log.debug("subscribed to topic pattern [{}], matching topics: {}", topicPattern,
                                    subscribedTopicPatternTopics);
                        } else {
                            log.debug("subscribed to topic pattern [{}], matching {} topics", topicPattern,
                                    subscribedTopicPatternTopics.size());
                        }
                    } else {
                        log.debug("subscribed to topics {}", topics);
                    }
                    return null;
                });
    }

    private void installRebalanceListeners() {
        if (blockingOnPartitionsRevokedHandler != null) {
            // need to apply workaround to set the blockingOnPartitionsRevokedHandler
            replaceRebalanceListener(kafkaConsumer, new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsAssigned(final Collection<org.apache.kafka.common.TopicPartition> partitions) {
                    // invoked on the actual Kafka consumer thread, not the event loop thread!
                    context.runOnContext(v -> HonoKafkaConsumer.this.onPartitionsAssigned(Helper.from(partitions)));
                }
                @Override
                public void onPartitionsRevoked(final Collection<org.apache.kafka.common.TopicPartition> partitions) {
                    // invoked on the actual Kafka consumer thread, not the event loop thread!
                    final Set<TopicPartition> partitionsSet = Helper.from(partitions);
                    blockingOnPartitionsRevokedHandler.handle(partitionsSet);
                    context.runOnContext(v -> HonoKafkaConsumer.this.onPartitionsRevoked(partitionsSet));
                }
            });
        } else {
            kafkaConsumer.partitionsAssignedHandler(this::onPartitionsAssigned);
            kafkaConsumer.partitionsRevokedHandler(this::onPartitionsRevoked);
        }
    }

    private Future<Void> subscribeAndWaitForRebalance() {
        if (stopped.get()) {
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "already stopped"));
        }
        final Promise<Void> subscribeDonePromise = subscribeDonePromiseRef
                .updateAndGet(promise -> promise == null ? Promise.promise() : promise);
        final Promise<Void> subscriptionPromise = Promise.promise();
        if (topicPattern != null) {
            kafkaConsumer.subscribe(topicPattern, subscriptionPromise);
        } else {
            kafkaConsumer.subscribe(topics, subscriptionPromise);
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

    private void onPartitionsAssigned(final Set<TopicPartition> partitionsSet) {
        if (log.isDebugEnabled()) {
            log.debug("partitions assigned: [{}]", HonoKafkaConsumerHelper.getPartitionsDebugString(partitionsSet));
        }
        // if a topic pattern is used, kafkaConsumer.subscription() will be used to fetch the actual subscribed topics;
        // if a fixed topic list is used instead, calling kafkaConsumer.subscription() would be of no use since it
        // always returns the topics used in the kafkaConsumer.subscribe(Collection) call, regardless of whether they exist
        // (kafkaConsumer.subscribe(Collection) would have auto-created the topics anyway, if "auto.create.topics.enable" is true)
        if (topicPattern != null) {
            updateSubscribedTopicPatternTopics();
        } else {
            Optional.ofNullable(subscribeDonePromiseRef.getAndSet(null))
                    .ifPresent(Promise::tryComplete);
        }
        if (onPartitionsAssignedHandler != null) {
            onPartitionsAssignedHandler.handle(partitionsSet);
        }
    }

    private void updateSubscribedTopicPatternTopics() {
        // update subscribedTopicPatternTopics (subscription() will return the actual topics, i.e. not the topic pattern if one is used
        kafkaConsumer.subscription(ar -> {
            if (ar.succeeded()) {
                subscribedTopicPatternTopics = new HashSet<>(ar.result());
            } else {
                log.warn("failed to get subscription", ar.cause());
            }
            Optional.ofNullable(subscribeDonePromiseRef.getAndSet(null))
                    .ifPresent(promise -> {
                        if (ar.succeeded()) {
                            promise.tryComplete();
                        } else {
                            promise.tryFail(ar.cause());
                        }
                    });
        });
    }

    private void onPartitionsRevoked(final Set<TopicPartition> partitionsSet) {
        if (log.isDebugEnabled()) {
            log.debug("partitions revoked: [{}]", HonoKafkaConsumerHelper.getPartitionsDebugString(partitionsSet));
        }
        if (onPartitionsRevokedHandler != null) {
            onPartitionsRevokedHandler.handle(partitionsSet);
        }
    }

    @Override
    public Future<Void> stop() {
        if (kafkaConsumer == null) {
            return Future.failedFuture("not started");
        } else if (!stopped.compareAndSet(false, true)) {
            return Future.failedFuture("already stopped");
        }
        final Promise<Void> consumerClosePromise = Promise.promise();
        kafkaConsumer.close(consumerClosePromise);
        return consumerClosePromise.future();
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
        } else if (stopped.get()) {
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
        return topicCheckFuture.future()
                .recover(thr -> {
                    log.warn("ensureTopicIsAmongSubscribedTopics: error getting partitions for topic [{}]", topic, thr);
                    return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                            "error getting topic partitions", thr));
                }).compose(partitions -> {
                    if (partitions.isEmpty()) {
                        log.warn("ensureTopicIsAmongSubscribedTopics: topic doesn't exist and didn't get auto-created: {}", topic);
                        return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                                "topic doesn't exist and didn't get auto-created"));
                    }
                    // again check topics in case rebalance happened in between
                    if (subscribedTopicPatternTopics.contains(topic)) {
                        return Future.succeededFuture();
                    }
                    // the topic list of a wildcard subscription only gets refreshed periodically by default (interval is defined by "metadata.max.age.ms");
                    // therefore enforce a refresh here by again subscribing to the topic pattern
                    log.debug("ensureTopicIsAmongSubscribedTopics: verified topic existence, wait for subscription update and rebalance [{}]", topic);
                    return subscribeAndWaitForRebalance()
                            .compose(v -> {
                                if (!subscribedTopicPatternTopics.contains(topic)) {
                                    log.warn("ensureTopicIsAmongSubscribedTopics: subscription not updated with topic after rebalance [topic: {}]", topic);
                                    return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                                                    "subscription not updated with topic after rebalance"));
                                }
                                log.debug("ensureTopicIsAmongSubscribedTopics: done updating topic subscription");
                                return Future.succeededFuture(v);
                            });
                });
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
}
