/**
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
 */
package org.eclipse.hono.client.pubsub;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.ClientErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;

import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * A client for publishing messages to Pub/Sub
 * <p>
 * Wraps a Pub/Sub publisher.
 * </p>
 */
public class PubSubPublisherClient implements AutoCloseable {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private Publisher publisher;

    private PubSubPublisherClient(final String projectId, final String topic) throws ClientErrorException {
        try {
            final TopicName topicName = TopicName.of(projectId, topic);
            this.publisher = Publisher.newBuilder(topicName).build();
        } catch (IOException e) {
            this.publisher = null;
            log.debug("Error initializing publisher client: {}", e.getMessage());
            throw new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "Publisher client is null", e);
        }
    }

    /**
     * Creates a new instance of PubSubPublisherClient where a Pub/Sub Publisher is initialized. The Publisher is based
     * on a created TopicName, which follows the format: projects/projectId/topics/topic.
     *
     * @param projectId The Google project id to use.
     * @param topic The topic to create the publisher for.
     * @return An instance of a PubSubPublisherClient.
     * @throws ClientErrorException if the initialization of the Publisher failed.
     */
    public static PubSubPublisherClient createShared(final String projectId, final String topic)
            throws ClientErrorException {
        return new PubSubPublisherClient(projectId, topic);
    }

    /**
     * Shuts the publisher down and frees resources.
     */
    @Override
    public void close() {
        if (Thread.currentThread().isInterrupted()) {
            final boolean interrupted = Thread.interrupted();
            if (interrupted) {
                log.debug("Thread was interrupted, thus interrupted state has been cleared.");
            }
        }
        if (publisher != null) {
            publisher.shutdown();
            try {
                publisher.awaitTermination(1, TimeUnit.MINUTES);

            } catch (InterruptedException e) {
                log.debug("Resources are not freed properly, error", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Publishes a message to Pub/Sub and transfer the returned ApiFuture into a Future.
     *
     * @param pubsubMessage The message to publish.
     * @return The messageId wrapped in a Future.
     */
    public Future<String> publish(final PubsubMessage pubsubMessage) {
        final Promise<String> result = Promise.promise();
        final ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);
        ApiFutures.addCallback(messageIdFuture, new ApiFutureCallback<>() {

            public void onSuccess(final String messageId) {
                result.handle(Future.succeededFuture(messageId));
            }

            public void onFailure(final Throwable t) {
                log.debug("Error publishing messages to Pub/Sub", t);
                result.handle(Future.failedFuture(t));
            }
        }, MoreExecutors.directExecutor());

        return result.future();
    }

}
