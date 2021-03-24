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
package org.eclipse.hono.commandrouter.impl.kafka;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedInternalCommandSender;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.common.PartitionInfo;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.common.impl.Helper;
import io.vertx.kafka.client.consumer.KafkaConsumer;

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
public class KafkaBasedCommandConsumerFactoryImpl implements CommandConsumerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaBasedCommandConsumerFactoryImpl.class);

    private static final Pattern COMMANDS_TOPIC_PATTERN = Pattern
            .compile(Pattern.quote(HonoTopic.Type.COMMAND.prefix) + ".*");
    private static final long WAIT_FOR_REBALANCE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    private final KafkaConsumer<String, Buffer> kafkaConsumer;
    private final Vertx vertx;
    /**
     * Currently subscribed topics, i.e. the topics matching COMMANDS_TOPIC_PATTERN.
     * Note that these are not (necessarily) the topics that this particular kafkaConsumer
     * here will receive messages for.
     */
    private Set<String> subscribedTopics = new HashSet<>();
    private final AtomicReference<Promise<Void>> onSubscribedTopicsNextUpdated = new AtomicReference<>();

    private final KafkaBasedMappingAndDelegatingCommandHandler commandHandler;

    /**
     * Creates a new factory to process commands via the Kafka cluster.
     *
     * @param vertx The Vert.x instance to use.
     * @param tenantClient The Tenant service client.
     * @param commandTargetMapper The component for mapping an incoming command to the gateway (if applicable) and
     *            protocol adapter instance that can handle it. Note that no initialization of this factory will be done
     *            here, that is supposed to be done by the calling method.
     * @param kafkaProducerFactory The producer factory for creating Kafka producers for sending messages.
     * @param kafkaProducerConfig The Kafka producer configuration.
     * @param kafkaConsumerConfig The Kafka consumer configuration.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public KafkaBasedCommandConsumerFactoryImpl(
            final Vertx vertx,
            final TenantClient tenantClient,
            final CommandTargetMapper commandTargetMapper,
            final KafkaProducerFactory<String, Buffer> kafkaProducerFactory,
            final KafkaProducerConfigProperties kafkaProducerConfig,
            final KafkaConsumerConfigProperties kafkaConsumerConfig,
            final Tracer tracer) {

        this.vertx = Objects.requireNonNull(vertx);
        Objects.requireNonNull(tenantClient);
        Objects.requireNonNull(commandTargetMapper);
        Objects.requireNonNull(kafkaProducerFactory);
        Objects.requireNonNull(kafkaProducerConfig);
        Objects.requireNonNull(kafkaConsumerConfig);
        Objects.requireNonNull(tracer);

        final KafkaBasedInternalCommandSender internalCommandSender = new KafkaBasedInternalCommandSender(
                kafkaProducerFactory, kafkaProducerConfig, tracer);
        commandHandler = new KafkaBasedMappingAndDelegatingCommandHandler(tenantClient, commandTargetMapper,
                internalCommandSender, tracer);
        final Map<String, String> consumerConfig = kafkaConsumerConfig.getConsumerConfig("cmd-router");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "cmd-router-group");
        this.kafkaConsumer = KafkaConsumer.create(vertx, consumerConfig, String.class, Buffer.class);
    }

    @Override
    public Future<Void> start() {
        //TODO in the next iteration: handling of offsets and commits.
        // And if required, to use a consumer(at most once) class similar to the AbstractAtLeastOnceKafkaConsumer
        kafkaConsumer
                .handler(commandHandler::mapAndDelegateIncomingCommandMessage)
                .partitionsAssignedHandler(this::onPartitionsAssigned)
                .partitionsRevokedHandler(this::onPartitionsRevoked)
                .exceptionHandler(error -> LOG.error("consumer error occurred", error));

        return CompositeFuture.all(commandHandler.start(), subscribeAndWaitForRebalanceAndTopicsUpdate())
                .map(ok -> {
                    LOG.debug("subscribed to topic pattern [{}], matching {} topics", COMMANDS_TOPIC_PATTERN, subscribedTopics.size());
                    return null;
                });
    }

    private Future<Void> subscribeAndWaitForRebalanceAndTopicsUpdate() {
        final Promise<Void> subscribedTopicsPromise = onSubscribedTopicsNextUpdated
                .updateAndGet(promise -> promise == null ? Promise.promise() : promise);
        final Promise<Void> subscriptionPromise = Promise.promise();
        kafkaConsumer.subscribe(COMMANDS_TOPIC_PATTERN, subscriptionPromise);
        vertx.setTimer(WAIT_FOR_REBALANCE_TIMEOUT, ar -> {
            if (!subscribedTopicsPromise.future().isComplete()) {
                final String errorMsg = "timed out waiting for rebalance and update of subscribed topics";
                LOG.error(errorMsg);
                subscribedTopicsPromise.tryFail(errorMsg);
            }
        });
        return CompositeFuture.all(subscriptionPromise.future(), subscribedTopicsPromise.future()).mapEmpty();
    }

    private void onPartitionsAssigned(final Set<TopicPartition> partitionsSet) {
        LOG.debug("partitions assigned: [{}]", getPartitionsDebugString(partitionsSet));
        // update subscribedTopics (subscription() will return the actual topics, not the topic pattern)
        kafkaConsumer.subscription(ar -> {
            if (ar.succeeded()) {
                subscribedTopics = new HashSet<>(ar.result());
            } else {
                LOG.warn("failed to get subscription", ar.cause());
            }
            Optional.ofNullable(onSubscribedTopicsNextUpdated.getAndSet(null))
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
        LOG.debug("partitions revoked: [{}]", getPartitionsDebugString(partitionsSet));
    }

    private String getPartitionsDebugString(final Set<TopicPartition> partitionsSet) {
        if (partitionsSet.size() <= 20) {
            // return detailed info
            return partitionsSet.stream()
                    .map(topicPartition -> String.format("'%s' -> %s", topicPartition.getTopic(), topicPartition.getPartition()))
                    .collect(Collectors.joining(", "));
        }
        return partitionsSet.size() + " topic partitions";
    }

    @Override
    public Future<Void> stop() {
        final Promise<Void> consumerClosePromise = Promise.promise();

        kafkaConsumer.close(consumerClosePromise);

        return CompositeFuture.all(commandHandler.stop(), consumerClosePromise.future())
                .mapEmpty();
    }

    @Override
    public Future<Void> createCommandConsumer(final String tenantId, final SpanContext context) {

        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, tenantId).toString();
        // check whether tenant topic exists and its existence has been applied to the wildcard subscription yet;
        // use previously updated topics list (less costly than invoking kafkaConsumer.subscription() here)
        if (subscribedTopics.contains(topic)) {
            LOG.debug("createCommandConsumer: topic is already subscribed [{}]", topic);
            return Future.succeededFuture();
        }

        final Promise<List<PartitionInfo>> topicCheckFuture = Promise.promise();
        // check whether tenant topic has been created since the last rebalance
        // and if not, potentially create it here implicitly
        // (partitionsFor() will create the topic if it doesn't exist, provided "auto.create.topics.enable" is true)
        partitionsFor(topic, topicCheckFuture);
        return topicCheckFuture.future()
                .onFailure(thr -> LOG.error("createCommandConsumer: error getting partitions for topic [{}]", topic, thr))
                .compose(partitions -> {
                    LOG.debug("createCommandConsumer: got partitions for topic [{}]: {}", topic, partitions.size());
                    if (partitions.isEmpty()) {
                        return Future.failedFuture("topic does not exist: " + topic);
                    }
                    // again check topics in case rebalance happened in between
                    if (subscribedTopics.contains(topic)) {
                        return Future.succeededFuture();
                    }
                    // the topic list of a wildcard subscription only gets refreshed periodically by default (interval is defined by "metadata.max.age.ms");
                    // therefore enforce a refresh here by again subscribing to the topic pattern
                    return subscribeAndWaitForRebalanceAndTopicsUpdate();
                })
                .onSuccess(ar -> LOG.debug("createCommandConsumer: done with refreshing topic subscription"));
    }

    /**
     * This method is adapted from {@code io.vertx.kafka.client.consumer.impl.KafkaConsumerImpl#partitionsFor(String, Handler)}
     * and fixes an NPE in case {@code KafkaConsumer#partitionsFor(String)} returns {@code null}
     * (happens if "auto.create.topics.enable" is false).
     * <p>
     * This method will become obsolete when updating to a Kafka client in which https://issues.apache.org/jira/browse/KAFKA-12260
     * ("PartitionsFor should not return null value") is solved.
     * TODO remove this method once updated Kafka client is used
     */
    private KafkaConsumer<String, Buffer> partitionsFor(final String topic, final Handler<AsyncResult<List<PartitionInfo>>> handler) {
        kafkaConsumer.asStream().partitionsFor(topic, done -> {

            if (done.succeeded()) {
                if (done.result() == null) {
                    handler.handle(Future.succeededFuture(List.of()));
                } else {
                    final List<PartitionInfo> partitions = new ArrayList<>();
                    for (final org.apache.kafka.common.PartitionInfo kafkaPartitionInfo: done.result()) {

                        final PartitionInfo partitionInfo = new PartitionInfo();
                        partitionInfo
                                .setInSyncReplicas(
                                        Stream.of(kafkaPartitionInfo.inSyncReplicas()).map(Helper::from).collect(Collectors.toList()))
                                .setLeader(Helper.from(kafkaPartitionInfo.leader()))
                                .setPartition(kafkaPartitionInfo.partition())
                                .setReplicas(
                                        Stream.of(kafkaPartitionInfo.replicas()).map(Helper::from).collect(Collectors.toList()))
                                .setTopic(kafkaPartitionInfo.topic());

                        partitions.add(partitionInfo);
                    }
                    handler.handle(Future.succeededFuture(partitions));
                }
            } else {
                handler.handle(Future.failedFuture(done.cause()));
            }
        });
        return kafkaConsumer;
    }
}
