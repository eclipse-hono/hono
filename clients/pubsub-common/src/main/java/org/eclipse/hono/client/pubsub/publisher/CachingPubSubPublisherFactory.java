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

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;

import com.google.api.gax.core.CredentialsProvider;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * A factory for creating PubSubPublisherClients. Created publishers are being cached.
 */
public final class CachingPubSubPublisherFactory implements PubSubPublisherFactory {

    private final Vertx vertx;
    private final Map<String, PubSubPublisherClient> activePublishers = new ConcurrentHashMap<>();
    private final String projectId;
    private final CredentialsProvider credentialsProvider;
    private Supplier<PubSubPublisherClient> clientSupplier;

    /**
     * Creates a new factory for {@link PubSubPublisherClient} instances.
     *
     * @param vertx The Vert.x instance that this factory runs on.
     * @param projectId The identifier of the Google Cloud Project to connect to.
     * @param credentialsProvider The provider for credentials to use for authenticating to the Pub/Sub service.
     * @throws NullPointerException if any of the parameter is {@code null}.
     */
    public CachingPubSubPublisherFactory(
            final Vertx vertx,
            final String projectId,
            final CredentialsProvider credentialsProvider) {
        this.vertx = Objects.requireNonNull(vertx);
        this.projectId = Objects.requireNonNull(projectId);
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider);
    }

    /**
     * Sets a supplier for the publisher(s) this factory creates.
     * <p>
     * This method is mainly intended to be used in test cases.
     *
     * @param supplier The supplier.
     */
    public void setClientSupplier(final Supplier<PubSubPublisherClient> supplier) {
        this.clientSupplier = supplier;
    }

    @Override
    public Future<Void> closePublisher(final String topic) {
        return removePublisher(topic);
    }

    @Override
    public Future<Void> closePublisher(final String topic, final String prefix) {
        final String topicName = PubSubMessageHelper.getTopicName(topic, prefix);
        return removePublisher(topicName);
    }

    @Override
    public Future<Void> closeAllPublisher() {
        activePublishers.forEach((k, v) -> removePublisher(k));
        if (activePublishers.size() == 0) {
            return Future.succeededFuture();
        }
        return Future.failedFuture(
                new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "Failed to close all publisher"));
    }

    @Override
    public PubSubPublisherClient getOrCreatePublisher(final String topic) {
        return activePublishers.computeIfAbsent(topic,
                s -> getPubSubPublisherClient(topic));
    }

    @Override
    public Optional<PubSubPublisherClient> getPublisher(final String topic, final String prefix) {
        final String topicTenantName = PubSubMessageHelper.getTopicName(topic, prefix);
        return Optional.ofNullable(activePublishers.get(topicTenantName));
    }

    private PubSubPublisherClient getPubSubPublisherClient(final String topic) {
        return Optional.ofNullable(clientSupplier)
                .map(Supplier::get)
                .orElseGet(() -> new PubSubPublisherClientImpl(vertx, projectId, topic, credentialsProvider));
    }

    private Future<Void> removePublisher(final String topicName) {
        final var publisher = activePublishers.remove(topicName);
        if (publisher != null) {
            try {
                publisher.close();
            } catch (final Exception e) {
                // ignore, since there is nothing we can do about it
            }
        }
        return Future.succeededFuture();
    }
}
