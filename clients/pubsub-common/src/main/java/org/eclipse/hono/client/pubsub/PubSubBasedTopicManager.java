/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.client.pubsub;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient.ListTopicsPagedResponse;
import com.google.pubsub.v1.ProjectName;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * A Pub/Sub based manager to manage topics. Wraps a TopicAdminClient.
 */
public class PubSubBasedTopicManager {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubBasedTopicManager.class);
    private static final long CREATE_CLIENT_RETRY_INTERVAL = 1000L;

    private final Vertx vertx;
    private final Supplier<Future<TopicAdminClient>> topicAdminClientCreator;
    private final String projectId;
    private final String tenantId;
    private final Context context;
    private TopicAdminClient topicAdminClient;

    /**
     * Creates a new PubSubBasedTopicManager.
     *
     * @param vertx The Vert.x instance to use.
     * @param projectId The GCP projectId the Pub/Sub topic is based on.
     * @param tenantId The tenant the topic is based on.
     */
    public PubSubBasedTopicManager(final Vertx vertx, final String projectId, final String tenantId) {
        this.vertx = Objects.requireNonNull(vertx);
        this.projectId = Objects.requireNonNull(projectId);
        this.tenantId = Objects.requireNonNull(tenantId);
        context = Vertx.currentContext();
        topicAdminClientCreator = this::createClientWithRetries;
    }

    private Future<TopicAdminClient> createClientWithRetries() {
        final AtomicBoolean retryCreateClient = new AtomicBoolean(true);
        final Promise<TopicAdminClient> createClientRetryPromise = Promise.promise();
        vertx.setPeriodic(CREATE_CLIENT_RETRY_INTERVAL, id -> {
            if (retryCreateClient.compareAndSet(true, false)) {
                createClient()
                        .onSuccess(client -> {
                            vertx.cancelTimer(id);
                            createClientRetryPromise.complete(client);
                        })
                        .onFailure(e -> retryCreateClient.set(true));
            }
        });
        return createClientRetryPromise.future();
    }

    private Future<TopicAdminClient> createClient() {
        try {
            final TopicAdminClient adminClient = TopicAdminClient.create();
            return Future.succeededFuture(adminClient);
        } catch (IOException e) {
            LOG.debug("Error initializing admin client: {}", e.getMessage());
            return Future.failedFuture("Error creating client");
        }
    }

    /**
     * Creates a new Topic on Pub/Sub based on the given projectId, topicName and tenantId.
     *
     * @param topicName The name of the topic.
     * @return A succeeded Future if the topic is successfully created or already exists, or a failed Future if the
     *         topic could not be created.
     */
    public Future<Void> createTopic(final String topicName) {
        topicAdminClientCreator.get()
                .onFailure(thr -> LOG.error("admin client creation failed", thr))
                .compose(client -> {
                    topicAdminClient = client;
                    return createTopic(projectId, topicName, tenantId);
                });
        return Future.succeededFuture();
    }

    private Future<Void> createTopic(final String projectId, final String topicName, final String tenantId) {
        final String topicTenantName = getTopicTenantName(topicName, tenantId);
        final TopicName topic = TopicName.of(projectId, topicTenantName);
        if (topicExists(projectId, topic.toString())) {
            return Future.succeededFuture();
        }
        LOG.debug("Create topic {} on project {}", topic, projectId);
        final Topic createdTopic = topicAdminClient.createTopic(topic);
        if (createdTopic == null) {
            LOG.debug("Creating topic failed [topic: {}, tenantId: {}, projectId: {}]", topicName, tenantId,
                    projectId);
            return Future.failedFuture("Topic creation failed.");
        }
        return Future.succeededFuture();
    }

    private String getTopicTenantName(final String topic, final String tenantId) {
        return String.format("%s.%s", tenantId, topic);
    }

    private boolean topicExists(final String projectId, final String topicId) {
        final ProjectName projectName = ProjectName.of(projectId);
        final ListTopicsPagedResponse pagedResponse = topicAdminClient.listTopics(projectName);
        if (pagedResponse != null) {
            for (Topic topic : pagedResponse.iterateAll()) {
                if (topic.getName().equals(topicId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Closes the TopicAdminClient if it exists. This method is expected to be invoked as soon as the TopicAdminClient
     * is no longer needed.
     *
     * @return A future that is completed when the close operation completed or a succeeded future if no
     *         TopicAdminClient existed.
     */
    public Future<Void> closeAdminClient() {
        if (topicAdminClient == null) {
            return Future.succeededFuture();
        }
        final Promise<Void> adminClientClosePromise = Promise.promise();
        context.executeBlocking(future -> {
            close();
            future.complete();
        }, adminClientClosePromise);
        return adminClientClosePromise.future();
    }

    private void close() {
        if (topicAdminClient != null) {
            topicAdminClient.shutdown();
            try {
                topicAdminClient.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                LOG.debug("Resources are not freed properly, error", e);
                Thread.currentThread().interrupt();
            }
        }
    }

}
