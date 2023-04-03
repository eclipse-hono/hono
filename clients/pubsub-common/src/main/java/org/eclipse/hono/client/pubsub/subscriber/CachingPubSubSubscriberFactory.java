/**
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
 */
package org.eclipse.hono.client.pubsub.subscriber;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.pubsub.v1.MessageReceiver;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * A factory for creating PubSubSubscribers. Created subscribers are being cached.
 */
public class CachingPubSubSubscriberFactory implements PubSubSubscriberFactory {

    private final Vertx vertx;
    private final Map<String, PubSubSubscriberClient> activeSubscribers = new ConcurrentHashMap<>();
    private final String projectId;
    private final CredentialsProvider credentialsProvider;
    private Supplier<PubSubSubscriberClient> clientSupplier;

    /**
     * Creates a new factory for {@link PubSubSubscriberClient} instances.
     *
     * @param vertx The Vert.x instance that this factory runs on.
     * @param projectId The identifier of the Google Cloud Project to connect to.
     * @param credentialsProvider The provider for credentials to use for authenticating to the Pub/Sub service.
     * @throws NullPointerException If any of the parameters is {@code null}.
     */
    public CachingPubSubSubscriberFactory(
            final Vertx vertx,
            final String projectId,
            final CredentialsProvider credentialsProvider) {
        this.vertx = Objects.requireNonNull(vertx);
        this.projectId = Objects.requireNonNull(projectId);
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider);
    }

    /**
     * Sets a supplier for the subscriber(s) this factory creates.
     * <p>
     * This method is mainly intended to be used in test cases.
     *
     * @param supplier The supplier.
     */
    public void setClientSupplier(final Supplier<PubSubSubscriberClient> supplier) {
        this.clientSupplier = supplier;
    }

    @Override
    public Future<Void> closeSubscriber(final String subscription, final String prefix) {
        final String subscriptionId = PubSubMessageHelper.getTopicName(subscription, prefix);
        return removeSubscriber(subscriptionId);
    }

    @Override
    public Future<Void> closeAllSubscribers() {
        activeSubscribers.forEach((k, v) -> removeSubscriber(k));
        if (activeSubscribers.isEmpty()) {
            return Future.succeededFuture();
        }
        return Future.failedFuture(
                new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "Failed to close all subscriber"));
    }

    @Override
    public PubSubSubscriberClient getOrCreateSubscriber(final String subscriptionId, final MessageReceiver receiver) {
        return activeSubscribers.computeIfAbsent(subscriptionId,
                s -> createPubSubSubscriber(subscriptionId, receiver));
    }

    @Override
    public Optional<PubSubSubscriberClient> getSubscriber(final String subscription, final String prefix) {
        final String subscriptionId = PubSubMessageHelper.getTopicName(subscription, prefix);
        return Optional.ofNullable(activeSubscribers.get(subscriptionId));
    }

    private PubSubSubscriberClient createPubSubSubscriber(final String subscriptionId,
                                                          final MessageReceiver receiver) {
        return Optional.ofNullable(clientSupplier)
                .map(Supplier::get)
                .orElseGet(() -> new PubSubSubscriberClientImpl(vertx, projectId, subscriptionId, receiver, credentialsProvider));
    }

    private Future<Void> removeSubscriber(final String subscriptionId) {
        final var subscriber = activeSubscribers.remove(subscriptionId);
        if (subscriber != null) {
            try {
                subscriber.close();
            } catch (final Exception e) {
                // ignore , since there is nothing we can do about it
            }
        }
        return Future.succeededFuture();
    }
}
