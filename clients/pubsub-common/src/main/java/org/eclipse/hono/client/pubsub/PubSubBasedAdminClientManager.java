/*******************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.protobuf.util.Durations;
import com.google.pubsub.v1.ExpirationPolicy;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * A Pub/Sub based admin client manager to manage topics and subscriptions. Wraps a TopicAdminClient and a
 * SubscriptionAdminClient.
 */
public class PubSubBasedAdminClientManager {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubBasedAdminClientManager.class);

    /**
     * The message retention in milliseconds for a Pub/Sub subscription.
     */
    private static final long MESSAGE_RETENTION = 600000;
    private final String projectId;
    private final CredentialsProvider credentialsProvider;
    private final Vertx vertx;
    private SubscriptionAdminClient subscriptionAdminClient;
    private TopicAdminClient topicAdminClient;

    /**
     * Creates a new PubSubBasedAdminClientManager.
     *
     * @param pubSubConfigProperties The Pub/Sub config properties containing the Google project ID.
     * @param credentialsProvider The provider for credentials to use for authenticating to the Pub/Sub service.
     * @param vertx The Vert.x instance to use.
     * @throws NullPointerException if vertx, credentialsProvider or projectId is {@code null}.
     */
    public PubSubBasedAdminClientManager(final PubSubConfigProperties pubSubConfigProperties,
            final CredentialsProvider credentialsProvider, final Vertx vertx) {
        Objects.requireNonNull(pubSubConfigProperties);
        this.projectId = Objects.requireNonNull(pubSubConfigProperties.getProjectId());
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider);
        this.vertx = Objects.requireNonNull(vertx);
    }

    private Future<TopicAdminClient> getOrCreateTopicAdminClient() {
        if (topicAdminClient != null) {
            return Future.succeededFuture(topicAdminClient);
        }
        try {
            final TopicAdminSettings adminSettings = TopicAdminSettings
                    .newBuilder()
                    .setCredentialsProvider(credentialsProvider)
                    .build();
            topicAdminClient = TopicAdminClient.create(adminSettings);
            return Future.succeededFuture(topicAdminClient);
        } catch (IOException e) {
            LOG.debug("Error initializing topic admin client: {}", e.getMessage());
            return Future.failedFuture("Error creating client");
        }
    }

    private Future<SubscriptionAdminClient> getOrCreateSubscriptionAdminClient() {
        if (subscriptionAdminClient != null) {
            return Future.succeededFuture(subscriptionAdminClient);
        }
        try {
            final SubscriptionAdminSettings adminSettings = SubscriptionAdminSettings
                    .newBuilder()
                    .setCredentialsProvider(credentialsProvider)
                    .build();
            subscriptionAdminClient = SubscriptionAdminClient.create(adminSettings);
            return Future.succeededFuture(subscriptionAdminClient);
        } catch (IOException e) {
            LOG.debug("Error initializing subscription admin client: {}", e.getMessage());
            return Future.failedFuture("Error creating client");
        }
    }

    /**
     * Gets an existing topic or creates a new one on Pub/Sub based on the given topic endpoint and prefix.
     *
     * @param endpoint The endpoint name of the topic, e.g. command_internal.
     * @param prefix The prefix of the topic, e.g. the adapter instance ID.
     * @return A succeeded Future if the topic is successfully created or already exists, or a failed Future if it could
     *         not be created.
     */
    public Future<String> getOrCreateTopic(final String endpoint, final String prefix) {
        final TopicName topicName = TopicName.of(projectId, PubSubMessageHelper.getTopicName(endpoint, prefix));

        return getOrCreateTopicAdminClient()
                .onFailure(thr -> LOG.debug("admin client creation failed", thr))
                .compose(client -> getTopic(topicName, client)
                        .recover(thr -> {
                            if (thr instanceof NotFoundException) {
                                return createTopic(topicName, client);
                            } else {
                                return Future.failedFuture(thr);
                            }
                        }));
    }

    private Future<String> getTopic(final TopicName topicName, final TopicAdminClient client) {
        return vertx.executeBlocking(promise -> {
            try {
                final Topic topic = client.getTopic(topicName);
                promise.complete(topic.getName());
            } catch (ApiException e) {
                promise.fail(e);
            }
        });
    }

    private Future<String> createTopic(final TopicName topicName, final TopicAdminClient client) {
        final Future<String> createdTopic = vertx
                .executeBlocking(promise -> {
                    try {
                        final Topic topic = client.createTopic(topicName);
                        promise.complete(topic.getName());
                    } catch (ApiException e) {
                        promise.fail(e);
                    }
                });
        createdTopic.onSuccess(top -> LOG.debug("Topic {} created successfully.", topicName))
                .onFailure(thr -> LOG.debug("Creating topic failed [topic: {}, projectId: {}]", topicName, projectId));
        return createdTopic;
    }

    /**
     * Gets an existing subscription or creates a new one on Pub/Sub based on the given subscription endpoint and
     * prefix.
     *
     * @param endpoint The endpoint name of the subscription, e.g. command_internal.
     * @param prefix The prefix of the subscription, e.g. the adapter instance ID.
     * @return A succeeded Future if the subscription is successfully created or already exists, or a failed Future if
     *         it could not be created.
     */
    public Future<String> getOrCreateSubscription(final String endpoint, final String prefix) {
        final String topicAndSubscriptionName = PubSubMessageHelper.getTopicName(endpoint, prefix);
        final TopicName topicName = TopicName.of(projectId, topicAndSubscriptionName);
        final SubscriptionName subscriptionName = SubscriptionName.of(projectId, topicAndSubscriptionName);

        return getOrCreateSubscriptionAdminClient()
                .onFailure(thr -> LOG.debug("admin client creation failed", thr))
                .compose(client -> getSubscription(subscriptionName, client)
                        .recover(thr -> {
                            if (thr instanceof NotFoundException) {
                                return createSubscription(subscriptionName, topicName, client);
                            } else {
                                return Future.failedFuture(thr);
                            }
                        }));
    }

    private Future<String> getSubscription(final SubscriptionName subscriptionName,
            final SubscriptionAdminClient client) {
        return vertx.executeBlocking(promise -> {
            try {
                final Subscription subscription = client.getSubscription(subscriptionName);
                promise.complete(subscription.getName());
            } catch (ApiException e) {
                promise.fail(e);
            }
        });
    }

    private Future<String> createSubscription(final SubscriptionName subscriptionName, final TopicName topicName,
            final SubscriptionAdminClient client) {
        final Subscription request = Subscription.newBuilder()
                .setName(subscriptionName.toString())
                .setTopic(topicName.toString())
                .setPushConfig(PushConfig.getDefaultInstance())
                .setAckDeadlineSeconds(0)
                .setMessageRetentionDuration(Durations.fromMillis(MESSAGE_RETENTION))
                .setExpirationPolicy(ExpirationPolicy.getDefaultInstance())
                .build();
        final Future<String> createdSubscription = vertx
                .executeBlocking(promise -> {
                    try {
                        final Subscription subscription = client.createSubscription(request);
                        promise.complete(subscription.getName());
                    } catch (ApiException e) {
                        promise.fail(e);
                    }
                });
        createdSubscription.onSuccess(sub -> LOG.debug("Subscription {} created successfully.", subscriptionName))
                .onFailure(
                        thr -> LOG.debug("Creating subscription failed [subscription: {}, topic: {}, project: {}]",
                                subscriptionName, topicName, projectId));
        return createdSubscription;
    }

    /**
     * Closes the TopicAdminClient and the SubscriptionAdminClient if they exist. This method is expected to be invoked
     * as soon as the TopicAdminClient and the SubscriptionAdminClient is no longer needed. This method will block the
     * current thread for up to 10 seconds!
     */
    public void closeAdminClients() {
        if (topicAdminClient == null && subscriptionAdminClient == null) {
            return;
        }
        closeSubscriptionAdminClient();
        closeTopicAdminClient();
    }

    private void closeSubscriptionAdminClient() {
        if (subscriptionAdminClient != null) {
            subscriptionAdminClient.shutdown();
            try {
                subscriptionAdminClient.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOG.debug("Resources are not freed properly, error", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    private void closeTopicAdminClient() {
        if (topicAdminClient != null) {
            topicAdminClient.shutdown();
            try {
                topicAdminClient.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOG.debug("Resources are not freed properly, error", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}
