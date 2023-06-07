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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.protobuf.util.Durations;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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
    private final Supplier<Future<SubscriptionAdminClient>> subscriptionAdminClientCreator;
    private final Supplier<Future<TopicAdminClient>> topicAdminClientCreator;
    private final String projectId;

    /**
     * A set of existing subscriptions. It contains subscriptions with the format
     * `"projects/{project}/subscriptions/{subscription}"`.
     */
    private final Set<String> subscriptions = new HashSet<>();
    /**
     * A set of existing topics. It contains topics with the format `"projects/{project}/topics/{topic}"`.
     */
    private final Set<String> topics = new HashSet<>();
    private final CredentialsProvider credentialsProvider;
    private final Context context;
    private SubscriptionAdminClient subscriptionAdminClient;
    private TopicAdminClient topicAdminClient;

    /**
     * Creates a new PubSubBasedAdminClientManager.
     *
     * @param projectId The identifier of the Google Cloud Project to connect to.
     * @param credentialsProvider The provider for credentials to use for authenticating to the Pub/Sub service.
     * @param vertx The Vert.x instance to use.
     */
    public PubSubBasedAdminClientManager(final String projectId, final CredentialsProvider credentialsProvider, final Vertx vertx) {
        this.projectId = Objects.requireNonNull(projectId);
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider);
        subscriptionAdminClientCreator = this::createSubscriptionAdminClient;
        topicAdminClientCreator = this::createTopicAdminClient;
        context = vertx.getOrCreateContext();
    }

    private Future<SubscriptionAdminClient> createSubscriptionAdminClient() {
        try {
            final SubscriptionAdminSettings adminSettings = SubscriptionAdminSettings
                    .newBuilder()
                    .setCredentialsProvider(credentialsProvider)
                    .build();
            final SubscriptionAdminClient adminClient = SubscriptionAdminClient.create(adminSettings);
            return Future.succeededFuture(adminClient);
        } catch (IOException e) {
            LOG.debug("Error initializing subscription admin client: {}", e.getMessage());
            return Future.failedFuture("Error creating client");
        }
    }

    private Future<TopicAdminClient> createTopicAdminClient() {
        try {
            final TopicAdminSettings adminSettings = TopicAdminSettings
                    .newBuilder()
                    .setCredentialsProvider(credentialsProvider)
                    .build();
            final TopicAdminClient adminClient = TopicAdminClient.create(adminSettings);
            return Future.succeededFuture(adminClient);
        } catch (IOException e) {
            LOG.debug("Error initializing topic admin client: {}", e.getMessage());
            return Future.failedFuture("Error creating client");
        }
    }

    /**
     * Gets an existing or creates a new Topic and Subscription on Pub/Sub based on the given topic endpoint and prefix.
     *
     * @param endpoint The endpoint name of the topic and the subscription, e.g. command_internal.
     * @param prefix The prefix of the topic and the subscription, e.g. the adapter instance ID.
     * @return A succeeded Future if topic and subscription are successfully created or already exists, or a failed
     *         Future if the topic or the subscription could not be created.
     */
    public Future<String> getOrCreateTopicAndSubscription(final String endpoint, final String prefix) {
        final String topicAndSubscriptionName = PubSubMessageHelper.getTopicName(endpoint, prefix);
        final TopicName topic = TopicName.of(projectId, topicAndSubscriptionName);
        final SubscriptionName subscription = SubscriptionName.of(projectId, topicAndSubscriptionName);

        return getOrCreateTopic(topic)
                .onFailure(t -> LOG.warn("Error creating topic {}", topicAndSubscriptionName))
                .recover(Future::failedFuture)
                .compose(s -> getOrCreateSubscription(subscription, topic));
    }

    /**
     * Closes the TopicAdminClient and the SubscriptionAdminClient if it exists. This method is expected to be invoked
     * as soon as the TopicAdminClient and the SubscriptionAdminClient is no longer needed.
     *
     * @return A future that is completed when the close operation completed or a succeeded future if no
     *         TopicAdminClient or SubscriptionAdminClient existed.
     * @throws IllegalStateException if clients are not running on a Vert.x Context.
     */
    public Future<Void> closeAdminClients() {
        if (topicAdminClient == null && subscriptionAdminClient == null) {
            return Future.succeededFuture();
        }
        final Promise<Void> adminClientClosePromise = Promise.promise();
        if (context == null) {
            throw new IllegalStateException("Clients are not running on a Vert.x Context");
        } else {
            context.executeBlocking(future -> {
                closeSubscriptionAdminClient();
                closeTopicAdminClient();
                future.complete();
            }, adminClientClosePromise);
            return adminClientClosePromise.future();
        }
    }

    private Future<Void> getOrCreateTopic(final TopicName topic) {
        return topicAdminClientCreator.get()
                .onFailure(thr -> LOG.error("admin client creation failed", thr))
                .recover(Future::failedFuture)
                .compose(client -> {
                    topicAdminClient = client;
                    return getOrCreateTopic(projectId, topic);
                });
    }

    private Future<Void> getOrCreateTopic(final String projectId, final TopicName topic) {
        if (topics.contains(topic.toString())) {
            LOG.debug("Topic {} already exists, continue", topic);
            return Future.succeededFuture();
        }
        try {
            final Topic createdTopic = topicAdminClient.createTopic(topic);
            if (createdTopic == null) {
                LOG.error("Creating topic failed [topic: {}, projectId: {}]", topic, projectId);
                return Future.failedFuture("Topic creation failed.");
            }
            topics.add(topic.toString());
            return Future.succeededFuture();
        } catch (AlreadyExistsException ex) {
            return Future.succeededFuture();
        } catch (ApiException e) {
            LOG.error("Error creating topic {} on project {}", topic, projectId, e);
            return Future.failedFuture("Topic creation failed.");
        }

    }

    private Future<String> getOrCreateSubscription(final SubscriptionName subscription, final TopicName topic) {
        return subscriptionAdminClientCreator.get()
                .onFailure(thr -> LOG.error("admin client creation failed", thr))
                .recover(Future::failedFuture)
                .compose(client -> {
                    subscriptionAdminClient = client;
                    return getOrCreateSubscription(projectId, subscription, topic);
                });
    }

    private Future<String> getOrCreateSubscription(
            final String projectId,
            final SubscriptionName subscription,
            final TopicName topic) {

        if (subscriptions.contains(subscription.toString())) {
            LOG.debug("Subscription {} already exists, continue", subscription);
            return Future.succeededFuture(subscription.getSubscription());
        }
        try {
            final Subscription request = Subscription.newBuilder()
                    .setName(subscription.toString())
                    .setTopic(topic.toString())
                    .setPushConfig(PushConfig.getDefaultInstance())
                    .setAckDeadlineSeconds(0)
                    .setMessageRetentionDuration(Durations.fromMillis(MESSAGE_RETENTION))
                    .build();
            final Subscription createdSubscription = subscriptionAdminClient.createSubscription(request);
            if (createdSubscription == null) {
                LOG.error("Creating subscription failed [subscription: {}, topic: {}, project: {}]", subscription,
                        topic,
                        projectId);
                return Future.failedFuture("Subscription creation failed.");
            }
            subscriptions.add(createdSubscription.getName());
            return Future.succeededFuture(subscription.getSubscription());
        } catch (AlreadyExistsException ex) {
            return Future.succeededFuture(subscription.getSubscription());
        } catch (ApiException e) {
            LOG.error("Error creating subscription {} for topic {} on project {}", subscription, topic, projectId, e);
            return Future.failedFuture("Subscription creation failed.");
        }
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
