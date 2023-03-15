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

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ServerErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.core.ApiService;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.ProjectSubscriptionName;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * A client for receiving messages from Pub/Sub.
 * <p>
 * Wraps a Pub/Sub Subscriber.
 * </p>
 */
public class PubSubSubscriber implements AutoCloseable {

    private final Logger log = LoggerFactory.getLogger(PubSubSubscriber.class);
    private final Subscriber subscriber;

    private final Vertx vertx;

    /**
     * Creates a new instance of PubSubSubscriberClient where a Pub/Sub Subscriber is initialized. The Subscriber is
     * based on a created subscription, which follows the format: projects/{project}/subscriptions/{subscription}
     *
     * @param vertx The Vert.x instance that this subscriber runs on.
     * @param projectId The identifier of the Google Cloud Project to connect to.
     * @param subscriptionId The name of the subscription to create the subscriber for.
     * @param receiver The message receiver used to process the received message.
     * @param credentialsProvider The provider for credentials to use for authenticating to the Pub/Sub service.
     * @throws NullPointerException If any of these parameters is {@code null}.
     */
    public PubSubSubscriber(
            final Vertx vertx,
            final String projectId,
            final String subscriptionId,
            final MessageReceiver receiver,
            final FixedCredentialsProvider credentialsProvider) {
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
     * @return A future indicating the outcome of the operation.
     * @throws ServerErrorException If subscribing was not successful.
     */
    public Future<Void> subscribe() {
        try {
            subscriber.addListener(
                    new ApiService.Listener() {

                        @Override
                        public void failed(final ApiService.State from, final Throwable failure) {
                            log.error("Error subscribing message from Pub/Sub", failure);
                            throw new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                                    "Error subscribing message from Pub/Sub", failure);
                        }
                    },
                    MoreExecutors.directExecutor());
            subscriber.startAsync().awaitRunning();
            return Future.succeededFuture();
        } catch (IllegalStateException e) {
            log.error("Service reached illegal state", e);
            return Future.failedFuture(e);
        }
    }

    /**
     * Shuts the subscriber down and frees resources.
     */
    @Override
    public void close() {
        vertx.executeBlocking(result -> {
            if (subscriber == null) {
                result.complete();
            } else {
                subscriber.stopAsync();
                result.complete();
            }
        }, false);
    }

}
