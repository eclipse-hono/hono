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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaAdminClientConfigProperties;
import org.eclipse.hono.commandrouter.AdapterInstanceStatusService;
import org.eclipse.hono.util.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.kafka.admin.KafkaAdminClient;

/**
 * A service to delete obsolete {@link HonoTopic.Type#COMMAND_INTERNAL} topics.
 */
public class InternalKafkaTopicCleanupService implements Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(InternalKafkaTopicCleanupService.class);

    private static final long CHECK_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private static final String CLIENT_NAME = "internal-topic-cleanup";
    private static final Pattern INTERNAL_COMMAND_TOPIC_PATTERN = Pattern
            .compile(Pattern.quote(HonoTopic.Type.COMMAND_INTERNAL.prefix) + "(.+)");

    private final Vertx vertx;
    private final AdapterInstanceStatusService adapterInstanceStatusService;
    private final Supplier<KafkaAdminClient> kafkaAdminClientCreator;
    private final Set<String> topicsToDelete = new HashSet<>();
    private final AtomicBoolean stopCalled = new AtomicBoolean();

    private KafkaAdminClient adminClient;
    private long timerId;

    /**
     * Creates an InternalKafkaTopicCleanupService.
     *
     * @param vertx The Vert.x instance to use.
     * @param adapterInstanceStatusService The service providing info about the status of adapter instances.
     * @param adminClientConfigProperties The Kafka admin client config properties.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public InternalKafkaTopicCleanupService(
            final Vertx vertx,
            final AdapterInstanceStatusService adapterInstanceStatusService,
            final KafkaAdminClientConfigProperties adminClientConfigProperties) {
        this.vertx = Objects.requireNonNull(vertx);
        this.adapterInstanceStatusService = Objects.requireNonNull(adapterInstanceStatusService);
        Objects.requireNonNull(adminClientConfigProperties);

        this.kafkaAdminClientCreator = () -> KafkaAdminClient.create(vertx,
                adminClientConfigProperties.getAdminClientConfig(CLIENT_NAME));
    }

    /**
     * Creates an InternalKafkaTopicCleanupService.
     * <p>
     * To be used for unit tests.
     *
     * @param vertx The Vert.x instance to use.
     * @param adapterInstanceStatusService The service providing info about the status of adapter instances.
     * @param kafkaAdminClientCreator The supplier for the Kafka admin client to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    InternalKafkaTopicCleanupService(
            final Vertx vertx,
            final AdapterInstanceStatusService adapterInstanceStatusService,
            final Supplier<KafkaAdminClient> kafkaAdminClientCreator) {
        this.vertx = Objects.requireNonNull(vertx);
        this.adapterInstanceStatusService = Objects.requireNonNull(adapterInstanceStatusService);
        this.kafkaAdminClientCreator = Objects.requireNonNull(kafkaAdminClientCreator);
    }

    @Override
    public Future<Void> start() {
        if (adminClient == null) {
            adminClient = kafkaAdminClientCreator.get();
            timerId = vertx.setPeriodic(CHECK_INTERVAL_MILLIS, tid -> performCleanup());
            LOG.info("started InternalKafkaTopicCleanupService");
        }
        return Future.succeededFuture();
    }

    /**
     * Determine topics to be deleted and delete the set of such topics determined in a previous invocation.
     */
    protected final void performCleanup() {
        if (!topicsToDelete.isEmpty()) {
            adminClient.listTopics()
                    .onFailure(thr -> LOG.warn("error listing topics", thr))
                    .onSuccess(allTopics -> {
                        final List<String> existingTopicsToDelete = topicsToDelete.stream().filter(allTopics::contains)
                                .collect(Collectors.toList());
                        if (existingTopicsToDelete.isEmpty()) {
                            topicsToDelete.clear();
                            determineToBeDeletedTopics(allTopics);
                        } else {
                            adminClient.deleteTopics(existingTopicsToDelete)
                                    .onSuccess(v -> LOG.info("deleted topics {}", existingTopicsToDelete))
                                    .onFailure(thr -> LOG.warn("error deleting topics {}", existingTopicsToDelete, thr))
                                    .onComplete(ar -> {
                                        topicsToDelete.clear();
                                        determineToBeDeletedTopics();
                                    });
                        }
                    });
        } else {
            // just determine topics to be deleted here, let them be deleted in the next cleanup invocation
            // so that we don't delete topics too early (while producers may still publish messages to them)
            determineToBeDeletedTopics();
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

    @Override
    public Future<Void> stop() {
        if (!stopCalled.compareAndSet(false, true) || adminClient == null) {
            return Future.succeededFuture();
        }
        vertx.cancelTimer(timerId);
        final Promise<Void> adminClientClosedPromise = Promise.promise();
        adminClient.close(adminClientClosedPromise);
        return adminClientClosedPromise.future()
                .recover(thr -> {
                    LOG.warn("error closing admin client", thr);
                    return Future.succeededFuture();
                });
    }
}
