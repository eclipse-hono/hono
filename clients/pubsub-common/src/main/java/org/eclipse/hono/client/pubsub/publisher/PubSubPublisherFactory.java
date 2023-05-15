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

import java.util.Optional;

import io.vertx.core.Future;

/**
 * A factory for creating Pub/Sub publishers scoped to a Google Cloud Project.
 */
public interface PubSubPublisherFactory {

    /**
     * Closes the publisher with the given topic if it exists.
     * <p>
     * This method is expected to be invoked as soon as the publisher is no longer needed.
     *
     * @param topic The topic of the publisher to remove.
     * @return A future that succeeds once the publisher is closed or if no publisher with the given topic exists.
     */
    Future<Void> closePublisher(String topic);

    /**
     * Closes the publisher with the given topic if it exists.
     * <p>
     * This method is expected to be invoked as soon as the publisher is no longer needed.
     *
     * @param topic The topic of the publisher to remove.
     * @param prefix The prefix of the topic of the publisher to remove, e.g. the tenantId.
     * @return A future that succeeds once the publisher is closed or if no publisher with the given topic exists.
     */
    Future<Void> closePublisher(String topic, String prefix);

    /**
     * Closes all cached publisher.
     * This method is expected to be invoked especially before the application shuts down.
     *
     * @return A future that is succeeded when all publisher are closed or a failed future if any publisher can not be
     *         closed.
     */
    Future<Void> closeAllPublisher();

    /**
     * Gets a publisher for sending data to Pub/Sub.
     * <p>
     * The publisher returned may be either newly created or it may be an existing publisher for the given topic.
     * <p>
     * Do not hold references to the returned publisher between send operations, because the publisher might be closed
     * by the factory. Instead, always get an instance by invoking this method.
     * <p>
     *
     * @param topic The topic to create the publisher for.
     * @return an existing or new publisher.
     */
    PubSubPublisherClient getOrCreatePublisher(String topic);

    /**
     * Gets an existing Publisher for sending data to Pub/Sub if one was already created with the given topicName and
     * prefix.
     *
     * @param topic The topic to identify the publisher.
     * @param prefix The prefix of the topic to identify the publisher, e.g. the tenantId.
     * @return An existing publisher or an empty Optional if no such publisher exists.
     */
    Optional<PubSubPublisherClient> getPublisher(String topic, String prefix);
}
