/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.pubsub.subscriber;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * A client for receiving messages from Pub/Sub.
 * <p>
 * Wraps a Pub/Sub Subscriber.
 * </p>
 */
public class PubSubSubscriberClientImpl implements PubSubSubscriberClient {

    /**
     * The number of milliseconds to wait before retrying to subscribe to a subscription.
     */
    private static final int SUBSCRIBE_RETRY_DELAY_MILLIS = 60000;
    private final Logger log = LoggerFactory.getLogger(PubSubSubscriberClientImpl.class);
    private final Subscriber subscriber;
    private final Vertx vertx;

    /**
     * Creates a new instance of PubSubSubscriberClientImpl where a Pub/Sub Subscriber is initialized. The Subscriber is
     * based on a created subscription, which follows the format: projects/{project}/subscriptions/{subscription}
     *
     * @param vertx The Vert.x instance that this subscriber runs on.
     * @param projectId The identifier of the Google Cloud Project to connect to.
     * @param subscriptionId The name of the subscription to create the subscriber for.
     * @param receiver The message receiver used to process the received message.
     * @param credentialsProvider The provider for credentials to use for authenticating to the Pub/Sub service.
     * @throws NullPointerException If any of these parameters is {@code null}.
     */
    public PubSubSubscriberClientImpl(
            final Vertx vertx,
            final String projectId,
            final String subscriptionId,
            final MessageReceiver receiver,
            final CredentialsProvider credentialsProvider) {
        this.vertx = Objects.requireNonNull(vertx);
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(subscriptionId);
        Objects.requireNonNull(receiver);
        Objects.requireNonNull(credentialsProvider);

        final ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId);
        this.subscriber = Subscriber
                .newBuilder(subscriptionName, receiver)
                .setCredentialsProvider(credentialsProvider)
                .build();
    }

    /**
     * Subscribes messages from Pub/Sub.
     *
     * @param keepTrying Condition that controls whether another attempt to subscribe to a subscription should be
     *            started. Client code can set this to {@code false} in order to prevent further attempts.
     * @return A future indicating the outcome of the operation.
     */
    public Future<Void> subscribe(final boolean keepTrying) {
        final Promise<Void> resultPromise = Promise.promise();
        subscribeWithRetries(resultPromise, keepTrying);
        return resultPromise.future();
    }

    private void subscribeWithRetries(final Promise<Void> resultPromise, final boolean keepTrying) {
        try {
            subscriber.startAsync().awaitRunning();
            log.info("Successfully subscribing on: {}", subscriber.getSubscriptionNameString());
            resultPromise.complete();
        } catch (Exception e) {
            if (keepTrying) {
                log.info("Error subscribing message from Pub/Sub, will retry in {}ms: ", SUBSCRIBE_RETRY_DELAY_MILLIS,
                        e);
                vertx.setTimer(SUBSCRIBE_RETRY_DELAY_MILLIS, tid -> subscribeWithRetries(resultPromise, keepTrying));
            } else {
                log.error("Error subscribing message from Pub/Sub", e);
                resultPromise.fail(e);
            }
        }
    }

    /**
     * Shuts the subscriber down and frees resources.
     */
    @Override
    public void close() {
        if (subscriber != null) {
            subscriber.stopAsync();
        }
    }
}
