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

import static org.mockito.Mockito.mock;

import static com.google.common.truth.Truth.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.api.gax.core.CredentialsProvider;

import io.vertx.core.Vertx;

/**
 * Verifies behavior of {@link CachingPubSubPublisherFactory}.
 */
public class CachingPubSubPublisherFactoryTest {

    private static final String TOPIC_NAME = "event";

    private static final String TENANT_ID = "test-tenant";

    private static final String PROJECT_ID = "test-project";

    private CachingPubSubPublisherFactory factory;
    private PubSubPublisherClient client;
    private Vertx vertx;
    private String topic;

    @BeforeEach
    void setUp() {
        vertx = mock(Vertx.class);
        topic = String.format("%s.%s", TENANT_ID, TOPIC_NAME);
        client = mock(PubSubPublisherClient.class);
        final CredentialsProvider credentialsProvider = mock(CredentialsProvider.class);
        factory = new CachingPubSubPublisherFactory(vertx, PROJECT_ID, credentialsProvider);
        factory.setClientSupplier(() -> client);
    }

    /**
     * Verifies that the factory creates a publisher and adds it to the cache.
     */
    @Test
    public void testThatPublisherIsAddedToCache() {
        assertThat(factory.getPublisher(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();
        final PubSubPublisherClient createdPublisher = factory.getOrCreatePublisher(topic);
        final Optional<PubSubPublisherClient> actual = factory.getPublisher(TOPIC_NAME, TENANT_ID);
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(createdPublisher);
    }

    /**
     * Verifies that the factory removes a publisher from the cache when it gets closed.
     */
    @Test
    public void testClosePublisherClosesAndRemovesFromCache() {
        assertThat(factory.getPublisher(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();

        final PubSubPublisherClient createdPublisher = factory.getOrCreatePublisher(topic);
        assertThat(createdPublisher).isNotNull();
        assertThat(factory.getPublisher(TOPIC_NAME, TENANT_ID).isPresent()).isTrue();

        factory.closePublisher(TOPIC_NAME, TENANT_ID);
        assertThat(factory.getPublisher(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();
    }

    /**
     * Verifies that the factory closes all active publishers and removes them from the cache.
     */
    @Test
    public void testCloseAllPublisherClosesAndRemovesFromCache() {
        assertThat(factory.getPublisher(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();

        final PubSubPublisherClient createdPublisher = factory.getOrCreatePublisher(topic);
        assertThat(createdPublisher).isNotNull();
        assertThat(factory.getPublisher(TOPIC_NAME, TENANT_ID).isPresent()).isTrue();

        factory.closeAllPublisher();
        assertThat(factory.getPublisher(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();
    }

}
