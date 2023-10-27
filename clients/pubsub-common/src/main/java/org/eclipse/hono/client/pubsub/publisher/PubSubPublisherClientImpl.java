/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.pubsub.publisher;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * A client for publishing messages to Pub/Sub
 * <p>
 * Wraps a Pub/Sub publisher.
 * </p>
 */
final class PubSubPublisherClientImpl implements PubSubPublisherClient {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubPublisherClientImpl.class);
    private final Vertx vertx;
    private Publisher publisher;

    /**
     * Creates a new instance of PubSubPublisherClientImpl where a Pub/Sub Publisher is initialized. The Publisher is
     * based on a created TopicName, which follows the format: projects/projectId/topics/topic.
     *
     * @param vertx The Vert.x instance that this publisher runs on.
     * @param projectId The Google project id to use.
     * @param topic The topic to create the publisher for.
     * @param credentialsProvider The provider for credentials to use for authenticating to the Pub/Sub service
     *                            or {@code null} if the default provider should be used.
     * @throws ClientErrorException if the initialization of the Publisher failed.
     * @throws NullPointerException if any of project ID or topic are {@code null}.
     */
    PubSubPublisherClientImpl(
            final Vertx vertx,
            final String projectId,
            final String topic,
            final CredentialsProvider credentialsProvider) throws ClientErrorException {

        this.vertx = Objects.requireNonNull(vertx);
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(topic);

        try {
            final TopicName topicName = TopicName.of(projectId, topic);
            final var builder = Publisher.newBuilder(topicName)
                    .setEnableMessageOrdering(true);
            Optional.ofNullable(credentialsProvider).ifPresent(builder::setCredentialsProvider);
            this.publisher = builder.build();
        } catch (final IOException e) {
            this.publisher = null;
            LOG.warn("error initializing publisher client", e);
            throw new ClientErrorException(
                    HttpURLConnection.HTTP_UNAVAILABLE,
                    "failed to create publisher for Pub/Sub",
                    e);
        }
    }

    /**
     * Shuts the publisher down and frees resources.
     */
    @Override
    public void close() {
        vertx.executeBlocking(result -> {
            if (publisher == null) {
                result.complete();
            } else {
                try {
                    publisher.shutdown();
                    publisher.awaitTermination(5, TimeUnit.SECONDS);
                    result.complete();
                } catch (final InterruptedException e) {
                    LOG.debug("timed out waiting for shut down of publisher", e);
                    Thread.currentThread().interrupt();
                    result.fail(e);
                }
            }
        }, false);
    }

    /**
     * Publishes a message to Pub/Sub.
     *
     * @param pubsubMessage The message to publish.
     * @return A future completed with the unique Pub/Sub assigned message ID
     *         if the message has been sent successfully to the Pub/Sub service.
     *         Otherwise, the future will be failed with a {@link ServiceInvocationException}
     *         indicating the reason for the failure.
     */
    @Override
    public Future<String> publish(final PubsubMessage pubsubMessage) {
        final Promise<String> result = Promise.promise();
        final Context context = vertx.getOrCreateContext();
        final ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);
        ApiFutures.addCallback(messageIdFuture, new ApiFutureCallback<>() {

            @Override
            public void onSuccess(final String messageId) {
                // handle result on original vert.x context instead of Publisher's Thread pool
                context.runOnContext(ok -> result.complete(messageId));
            }

            @Override
            public void onFailure(final Throwable t) {
                context.runOnContext(ok -> {
                    LOG.debug("error publishing messages to Pub/Sub", t);
                    result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, t));
                });
            }
        }, MoreExecutors.directExecutor());

        return result.future();
    }

}
