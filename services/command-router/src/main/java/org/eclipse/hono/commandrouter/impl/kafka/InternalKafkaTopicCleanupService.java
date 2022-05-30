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

package org.eclipse.hono.commandrouter.impl.kafka;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.HonoTopic.Type;
import org.eclipse.hono.client.kafka.KafkaAdminClientConfigProperties;
import org.eclipse.hono.client.kafka.KafkaClientFactory;
import org.eclipse.hono.commandrouter.AdapterInstanceStatusService;
import org.eclipse.hono.util.LifecycleStatus;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.kafka.admin.KafkaAdminClient;

/**
 * A service to delete obsolete {@link Type#COMMAND_INTERNAL} topics.
 */
public class InternalKafkaTopicCleanupService extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(InternalKafkaTopicCleanupService.class);

    private static final long CHECK_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private static final String CLIENT_NAME = "internal-topic-cleanup";
    private static final Pattern INTERNAL_COMMAND_TOPIC_PATTERN = Pattern
            .compile(Pattern.quote(HonoTopic.Type.COMMAND_INTERNAL.prefix) + "(.+)");

    private final AdapterInstanceStatusService adapterInstanceStatusService;
    private final Supplier<Future<KafkaAdminClient>> kafkaAdminClientCreator;
    private final Set<String> topicsToDelete = new HashSet<>();
    private final LifecycleStatus lifecycleStatus = new LifecycleStatus();

    private KafkaAdminClient adminClient;
    private long timerId;

    /**
     * Creates an InternalKafkaTopicCleanupService.
     *
     * @param adapterInstanceStatusService The service providing info about the status of adapter instances.
     * @param adminClientConfigProperties The Kafka admin client config properties.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public InternalKafkaTopicCleanupService(
            final AdapterInstanceStatusService adapterInstanceStatusService,
            final KafkaAdminClientConfigProperties adminClientConfigProperties) {
        this.adapterInstanceStatusService = Objects.requireNonNull(adapterInstanceStatusService);
        Objects.requireNonNull(adminClientConfigProperties);

        final var adminClientConfig = adminClientConfigProperties.getAdminClientConfig(CLIENT_NAME);
        this.kafkaAdminClientCreator = () -> {
            final var kafkaClientFactory = new KafkaClientFactory(vertx);
            return kafkaClientFactory.createKafkaAdminClientWithRetries(
                    adminClientConfig,
                    lifecycleStatus::isStarting,
                    KafkaClientFactory.UNLIMITED_RETRIES_DURATION);
        };
    }

    /**
     * Creates an InternalKafkaTopicCleanupService.
     * <p>
     * To be used for unit tests.
     *
     * @param adapterInstanceStatusService The service providing info about the status of adapter instances.
     * @param kafkaAdminClient The Kafka admin client to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    InternalKafkaTopicCleanupService(
            final AdapterInstanceStatusService adapterInstanceStatusService,
            final KafkaAdminClient kafkaAdminClient) {
        this.adapterInstanceStatusService = Objects.requireNonNull(adapterInstanceStatusService);
        Objects.requireNonNull(kafkaAdminClient);
        this.kafkaAdminClientCreator = () -> Future.succeededFuture(kafkaAdminClient);
    }

    /**
     * Adds a handler to be invoked with a succeeded future once this service is ready to be used.
     *
     * @param handler The handler to invoke. The handler will never be invoked with a failed future.
     */
    public final void addOnServiceReadyHandler(final Handler<AsyncResult<Void>> handler) {
        if (handler != null) {
            lifecycleStatus.addOnStartedHandler(handler);
        }
    }

    /**
     * Checks if this service is ready to be used.
     *
     * @return The result of the check.
     */
    public HealthCheckResponse checkReadiness() {
        return HealthCheckResponse.builder()
                .name("Kafka-topic-cleanup-service")
                .status(lifecycleStatus.isStarted())
                .build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This methods triggers the creation of a Kafka admin client in the background. A new attempt to create the
     * client is made periodically until creation succeeds or the {@link #stop(Promise)} method has been
     * invoked.
     * <p>
     * Client code may {@linkplain #addOnServiceReadyHandler(Handler) register a dedicated handler}
     * to be notified once the service is up and running.
     * @throws IllegalStateException if this component is already started or is currently stopping.
     */
    @Override
    public void start() {
        if (lifecycleStatus.isStarting()) {
            return;
        } else if (!lifecycleStatus.setStarting()) {
            throw new IllegalStateException("client is already started/stopping");
        }
        kafkaAdminClientCreator.get()
                .onSuccess(client -> {
                    adminClient = client;
                    timerId = vertx.setPeriodic(CHECK_INTERVAL_MILLIS, tid -> performCleanup());
                    LOG.info("started InternalKafkaTopicCleanupService");
                    lifecycleStatus.setStarted();
                });
    }

    /**
     * Determine topics to be deleted and delete the set of such topics determined in a previous invocation.
     */
    protected final void performCleanup() {
        if (topicsToDelete.isEmpty()) {
            // just determine topics to be deleted here, let them be deleted in the next cleanup invocation
            // so that we don't delete topics too early (while producers may still publish messages to them)
            determineToBeDeletedTopics();
        } else {
            adminClient.listTopics()
                .onFailure(thr -> LOG.warn("error listing topics", thr))
                .onSuccess(allTopics -> {
                    final List<String> existingTopicsToDelete = topicsToDelete.stream()
                            .filter(allTopics::contains)
                            .collect(Collectors.toList());
                    if (existingTopicsToDelete.isEmpty()) {
                        topicsToDelete.clear();
                        determineToBeDeletedTopics(allTopics);
                    } else {
                        adminClient.deleteTopics(existingTopicsToDelete)
                                .onSuccess(v -> LOG.info("triggered deletion of {} topics ({})",
                                        existingTopicsToDelete.size(), existingTopicsToDelete))
                                .onFailure(thr -> {
                                    if (thr instanceof UnknownTopicOrPartitionException) {
                                        LOG.info("triggered deletion of {} topics, some had already been deleted ({})",
                                                existingTopicsToDelete.size(), existingTopicsToDelete);
                                    } else {
                                        LOG.warn("error deleting topics {}", existingTopicsToDelete, thr);
                                    }
                                })
                                .onComplete(ar -> {
                                    topicsToDelete.clear();
                                    determineToBeDeletedTopics();
                                });
                    }
                });
        }
    }

    private void determineToBeDeletedTopics() {
        adminClient.listTopics()
                .onSuccess(this::determineToBeDeletedTopics)
                .onFailure(thr -> LOG.warn("error listing topics", thr));
    }

    private void determineToBeDeletedTopics(final Set<String> allTopics) {
        final Map<String, String> adapterInstanceIdToTopicMap = new HashMap<>();
        for (final String topic : allTopics) {
            final Matcher matcher = INTERNAL_COMMAND_TOPIC_PATTERN.matcher(topic);
            if (matcher.matches()) {
                final String adapterInstanceId = matcher.group(1);
                adapterInstanceIdToTopicMap.put(adapterInstanceId, topic);
            }
        }
        adapterInstanceStatusService.getDeadAdapterInstances(adapterInstanceIdToTopicMap.keySet())
                .onFailure(thr -> LOG.warn("error determining dead adapter instances", thr))
                .onSuccess(deadAdapterInstances -> {
                    deadAdapterInstances.forEach(id -> topicsToDelete.add(adapterInstanceIdToTopicMap.get(id)));
                    if (topicsToDelete.isEmpty()) {
                        LOG.debug("found no topics to be deleted; no. of checked topics: {}", adapterInstanceIdToTopicMap.size());
                    } else {
                        LOG.info("marking topics as to be deleted on next run {}", topicsToDelete);
                    }
                });
    }

    /**
     * {@inheritDoc}
     * <p>
     * Closes the Kafka admin client.
     *
     * @param stopResult The handler to invoke with the outcome of the operation.
     *                   The result will be succeeded once this component is stopped.
     */

    @Override
    public void stop(final Promise<Void> stopResult) {

        lifecycleStatus.runStopAttempt(() -> {
            vertx.cancelTimer(timerId);
            return Optional.ofNullable(adminClient)
                    .map(KafkaAdminClient::close)
                    .orElseGet(Future::succeededFuture)
                    .onFailure(thr -> LOG.warn("error closing admin client", thr));
        }).onComplete(stopResult);
    }
}
