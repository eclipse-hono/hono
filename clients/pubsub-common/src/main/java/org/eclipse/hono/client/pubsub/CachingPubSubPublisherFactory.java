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

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.hono.client.ServerErrorException;

import io.vertx.core.Future;

/**
 * A factory for creating PubSubPublisherClients. Created publishers are being cached.
 */
public class CachingPubSubPublisherFactory implements PubSubPublisherFactory {

    private final Map<String, PubSubPublisherClient> activePublishers = new ConcurrentHashMap<>();

    /**
     * Creates a new Factory that will produce {@link PubSubPublisherClient#createShared(String, String) publishers}.
     *
     * @return an instance of the Factory.
     */
    public static CachingPubSubPublisherFactory createFactory() {
        return new CachingPubSubPublisherFactory();
    }

    @Override
    public Future<Void> closePublisher(final String topic, final String tenantId) {
        final String topicName = getTopicTenantName(topic, tenantId);
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
    public PubSubPublisherClient getOrCreatePublisher(final String topic, final String projectId,
            final String tenantId) {
        final String topicName = getTopicTenantName(topic, tenantId);
        return activePublishers.computeIfAbsent(topicName,
                s -> getPubSubPublisherClient(projectId, topicName));
    }

    @Override
    public Optional<PubSubPublisherClient> getPublisher(final String topic, final String tenantId) {
        final String topicTenantName = getTopicTenantName(topic, tenantId);
        return Optional.ofNullable(activePublishers.get(topicTenantName));
    }

    private PubSubPublisherClient getPubSubPublisherClient(final String projectId, final String topic) {
        return PubSubPublisherClient.createShared(projectId, topic);
    }

    private String getTopicTenantName(final String topic, final String tenantId) {
        return String.format("%s.%s", tenantId, topic);
    }

    private Future<Void> removePublisher(final String topicName) {
        final PubSubPublisherClient publisher = activePublishers.remove(topicName);
        if (publisher == null) {
            return Future.succeededFuture();
        }
        publisher.close();
        return Future.succeededFuture();
    }
}
