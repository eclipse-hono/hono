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

import static org.mockito.Mockito.mock;

import static com.google.common.truth.Truth.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Verifies behavior of {@link CachingPubSubPublisherFactory}.
 */
public class CachingPubSubPublisherFactoryTest {

    private static final String TOPIC_NAME = "event";

    private static final String TENANT_ID = "test-tenant";

    private static final String PROJECT_ID = "test-project";

    private CachingPubSubPublisherFactory factory;

    @BeforeEach
    void setUp() {
        factory = CachingPubSubPublisherFactory.createFactory();
    }

    /**
     * Verifies that {@link CachingPubSubPublisherFactory#getOrCreatePublisher(String, String, String)} creates a
     * publisher and adds it to the cache.
     */
    @Test
    public void testThatPublisherIsAddedToCache() {
        assertThat(factory.getPublisher(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();

        final PubSubPublisherClient client = mock(PubSubPublisherClient.class);

        try (MockedStatic<PubSubPublisherClient> pubSubClient = Mockito.mockStatic(PubSubPublisherClient.class)) {
            final String topicTenantName = String.format("%s.%s", TENANT_ID, TOPIC_NAME);
            pubSubClient.when(() -> PubSubPublisherClient.createShared(PROJECT_ID, topicTenantName)).thenReturn(client);

            final PubSubPublisherClient createPublisher = factory.getOrCreatePublisher(TOPIC_NAME, PROJECT_ID,
                    TENANT_ID);
            final Optional<PubSubPublisherClient> actual = factory.getPublisher(TOPIC_NAME, TENANT_ID);
            assertThat(actual.isPresent()).isTrue();
            assertThat(actual.get()).isEqualTo(createPublisher);
        }
    }

    /**
     * Verifies that {@link CachingPubSubPublisherFactory#closePublisher(String, String)} closes the publisher and
     * removes it from the cache.
     */
    @Test
    public void testClosePublisherClosesAndRemovesFromCache() {
        assertThat(factory.getPublisher(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();

        final PubSubPublisherClient client = mock(PubSubPublisherClient.class);

        try (MockedStatic<PubSubPublisherClient> pubSubClient = Mockito.mockStatic(PubSubPublisherClient.class)) {
            final String topicTenantName = String.format("%s.%s", TENANT_ID, TOPIC_NAME);
            pubSubClient.when(() -> PubSubPublisherClient.createShared(PROJECT_ID, topicTenantName)).thenReturn(client);

            final PubSubPublisherClient createdPublisher = factory.getOrCreatePublisher(TOPIC_NAME, PROJECT_ID,
                    TENANT_ID);
            assertThat(createdPublisher).isNotNull();
            assertThat(factory.getPublisher(TOPIC_NAME, TENANT_ID).isPresent()).isTrue();

            factory.closePublisher(TOPIC_NAME, TENANT_ID);
            assertThat(factory.getPublisher(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();
        }
    }

    /**
     * Verifies that {@link CachingPubSubPublisherFactory#closeAllPublisher} closes all active publisher and removes
     * them from the cache.
     */
    @Test
    public void testCloseAllPublisherClosesAndRemovesFromCache() {
        assertThat(factory.getPublisher(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();

        final PubSubPublisherClient client = mock(PubSubPublisherClient.class);

        try (MockedStatic<PubSubPublisherClient> pubSubClient = Mockito.mockStatic(PubSubPublisherClient.class)) {
            final String topicTenantName = String.format("%s.%s", TENANT_ID, TOPIC_NAME);
            pubSubClient.when(() -> PubSubPublisherClient.createShared(PROJECT_ID, topicTenantName)).thenReturn(client);

            final PubSubPublisherClient createdPublisher = factory.getOrCreatePublisher(TOPIC_NAME, PROJECT_ID,
                    TENANT_ID);
            assertThat(createdPublisher).isNotNull();
            assertThat(factory.getPublisher(TOPIC_NAME, TENANT_ID).isPresent()).isTrue();

            factory.closeAllPublisher();
            assertThat(factory.getPublisher(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();
        }

    }

}
