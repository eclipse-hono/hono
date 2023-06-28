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

import io.vertx.core.Future;

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
    private SubscriptionAdminClient subscriptionAdminClient;
    private TopicAdminClient topicAdminClient;

    /**
     * Creates a new PubSubBasedAdminClientManager.
     *
     * @param projectId The identifier of the Google Cloud Project to connect to.
     * @param credentialsProvider The provider for credentials to use for authenticating to the Pub/Sub service.
     */
    public PubSubBasedAdminClientManager(final String projectId, final CredentialsProvider credentialsProvider) {
        this.projectId = Objects.requireNonNull(projectId);
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider);
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
     * @return A succeeded Future if the topic is successfully created or already exists, or a failed
     *         Future if it could not be created.
     */
    public Future<String> getOrCreateTopic(final String endpoint, final String prefix) {
        final String topicName = PubSubMessageHelper.getTopicName(endpoint, prefix);
        final TopicName topic = TopicName.of(projectId, topicName);

        if (topics.contains(topic.toString())) {
            LOG.debug("Topic {} already exists, continue", topic);
            return Future.succeededFuture(topic.getTopic());
        }
        return getOrCreateTopicAdminClient()
                .onFailure(thr -> LOG.debug("admin client creation failed", thr))
                .compose(client -> getOrCreateTopic(projectId, topic, client));
    }

    private Future<String> getOrCreateTopic(final String projectId, final TopicName topic, final TopicAdminClient client) {
        try {
            final Topic createdTopic = client.createTopic(topic);
            if (createdTopic == null) {
                LOG.debug("Creating topic failed [topic: {}, projectId: {}]", topic, projectId);
                return Future.failedFuture("Topic creation failed.");
            }
            topics.add(createdTopic.getName());
            return Future.succeededFuture(topic.getTopic());
        } catch (AlreadyExistsException ex) {
            return Future.succeededFuture(topic.getTopic());
        } catch (ApiException e) {
            LOG.debug("Error creating topic {} on project {}", topic, projectId, e);
            return Future.failedFuture("Topic creation failed.");
        }

    }

    /**
     * Gets an existing subscription or creates a new one on Pub/Sub based on the given subscription endpoint and prefix.
     *
     * @param endpoint The endpoint name of the subscription, e.g. command_internal.
     * @param prefix The prefix of the subscription, e.g. the adapter instance ID.
     * @return A succeeded Future if the subscription is successfully created or already exists, or a failed
     *         Future if it could not be created.
     */
    public Future<String> getOrCreateSubscription(final String endpoint, final String prefix) {
        final String topicAndSubscriptionName = PubSubMessageHelper.getTopicName(endpoint, prefix);
        final TopicName topic = TopicName.of(projectId, topicAndSubscriptionName);
        final SubscriptionName subscription = SubscriptionName.of(projectId, topicAndSubscriptionName);

        if (subscriptions.contains(subscription.toString())) {
            LOG.debug("Subscription {} already exists, continue", subscription);
            return Future.succeededFuture(subscription.getSubscription());
        }
        return getOrCreateSubscriptionAdminClient()
                .onFailure(thr -> LOG.debug("admin client creation failed", thr))
                .compose(client -> getOrCreateSubscription(projectId, subscription, topic, client));
    }

    private Future<String> getOrCreateSubscription(
            final String projectId,
            final SubscriptionName subscription,
            final TopicName topic,
            final SubscriptionAdminClient client) {
        try {
            final Subscription request = Subscription.newBuilder()
                    .setName(subscription.toString())
                    .setTopic(topic.toString())
                    .setPushConfig(PushConfig.getDefaultInstance())
                    .setAckDeadlineSeconds(0)
                    .setMessageRetentionDuration(Durations.fromMillis(MESSAGE_RETENTION))
                    .build();
            final Subscription createdSubscription = client.createSubscription(request);
            if (createdSubscription == null) {
                LOG.debug("Creating subscription failed [subscription: {}, topic: {}, project: {}]", subscription,
                        topic,
                        projectId);
                return Future.failedFuture("Subscription creation failed.");
            }
            subscriptions.add(createdSubscription.getName());
            return Future.succeededFuture(subscription.getSubscription());
        } catch (AlreadyExistsException ex) {
            return Future.succeededFuture(subscription.getSubscription());
        } catch (ApiException e) {
            LOG.debug("Error creating subscription {} for topic {} on project {}", subscription, topic, projectId, e);
            return Future.failedFuture("Subscription creation failed.");
        }
    }

    /**
     * Closes the TopicAdminClient and the SubscriptionAdminClient if they exist. This method is expected to be invoked
     * as soon as the TopicAdminClient and the SubscriptionAdminClient is no longer needed.
     * This method will bock the current thread for up to 10 seconds!
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
