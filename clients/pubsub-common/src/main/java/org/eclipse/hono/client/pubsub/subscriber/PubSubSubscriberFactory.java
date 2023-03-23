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

import java.util.Optional;

import com.google.cloud.pubsub.v1.MessageReceiver;

import io.vertx.core.Future;

/**
 * A factory for creating Pub/Sub subscribers scoped to a Google Cloud Project.
 */
public interface PubSubSubscriberFactory {

    /**
     * Closes the subscriber with the given topic if it exists.
     * <p>
     * This method is expected to be invoked as soon as the subscriber is no longer needed.
     *
     * @param subscription The subscription of the subscriber to remove.
     * @param prefix The prefix of the topic of the subscriber to remove, e.g. the tenantId.
     * @return A future that is completed when the close operation completed or a succeeded future if no subscriber
     *         existed with the given topic.
     */
    Future<Void> closeSubscriber(String subscription, String prefix);

    /**
     * Closes all cached subscriber. This method is expected to be invoked especially before the application shuts down.
     *
     * @return A future that is succeeded when all subscriber are closed or a failed future if any subscriber can not be
     *         closed.
     */
    Future<Void> closeAllSubscribers();

    /**
     * Gets a subscriber for receiving data from Pub/Sub.
     * <p>
     * The subscriber returned may be either newly created or it may be an existing subscriber for the given
     * subscription.
     *
     * @param subscriptionId The subscription to create the subscriber for.
     * @param receiver The message receiver used to process the received message.
     * @return an existing or new subscriber.
     */
    PubSubSubscriberClient getOrCreateSubscriber(String subscriptionId, MessageReceiver receiver);

    /**
     * Gets an existing Subscriber for receiving data from Pub/Sub if one was already created with the given
     * subscription and prefix.
     *
     * @param subscription The subscription to identify the subscriber.
     * @param prefix The prefix of the subscription to identify the subscriber, e.g. the tenantId.
     * @return An existing subscriber or an empty Optional if no such subscriber exists.
     */
    Optional<PubSubSubscriberClient> getSubscriber(String subscription, String prefix);

}
