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

import static org.mockito.Mockito.mock;

import static com.google.common.truth.Truth.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.pubsub.v1.MessageReceiver;

import io.vertx.core.Vertx;

/**
 * Verifies behavior of {@link CachingPubSubSubscriberFactory}.
 */
public class CachingPubSubSubscriberFactoryTest {

    private static final String PROJECT_ID = "test-project";

    private static final String TENANT_ID = "test-tenant";

    private static final String TOPIC_NAME = "command";

    private CachingPubSubSubscriberFactory factory;
    private PubSubSubscriberClient client;
    private String topic;
    private MessageReceiver receiver;

    @BeforeEach
    void setUp() {
        final Vertx vertx = mock(Vertx.class);
        final CredentialsProvider credentialsProvider = mock(CredentialsProvider.class);
        topic = String.format("%s.%s", TENANT_ID, TOPIC_NAME);
        receiver = mock(MessageReceiver.class);
        client = mock(PubSubSubscriberClient.class);
        factory = new CachingPubSubSubscriberFactory(vertx, PROJECT_ID, credentialsProvider);
        factory.setClientSupplier(() -> client);
    }

    /**
     * Verifies that the factory creates a subscriber and adds it to the cache.
     */
    @Test
    public void testThatSubscriberIsAddedToCache() {
        assertThat(factory.getSubscriber(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();
        final PubSubSubscriberClient createdSubscriber = factory.getOrCreateSubscriber(topic, receiver);
        final Optional<PubSubSubscriberClient> actual = factory.getSubscriber(TOPIC_NAME, TENANT_ID);
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(createdSubscriber);
    }

    /**
     * Verifies that the factory removes a subscriber from the cache when it gets closed.
     */
    @Test
    public void testCloseSubscriberClosesAndRemovesFromCache() {
        assertThat(factory.getSubscriber(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();

        final PubSubSubscriberClient createdSubscriber = factory.getOrCreateSubscriber(topic, receiver);
        assertThat(createdSubscriber).isNotNull();
        assertThat(factory.getSubscriber(TOPIC_NAME, TENANT_ID).isPresent()).isTrue();

        factory.closeSubscriber(TOPIC_NAME, TENANT_ID);
        assertThat(factory.getSubscriber(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();
    }

    /**
     * Verifies that the factory closes all active subscribers and removes them from the cache.
     */
    @Test
    public void testCloseAllSubscriberClosesAndRemovesFromCache() {
        assertThat(factory.getSubscriber(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();

        final PubSubSubscriberClient createdSubscriber = factory.getOrCreateSubscriber(topic, receiver);
        assertThat(createdSubscriber).isNotNull();
        assertThat(factory.getSubscriber(TOPIC_NAME, TENANT_ID).isPresent()).isTrue();

        factory.closeAllSubscribers();
        assertThat(factory.getSubscriber(TOPIC_NAME, TENANT_ID).isEmpty()).isTrue();
    }
}
