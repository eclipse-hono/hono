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

import com.google.pubsub.v1.PubsubMessage;

import io.vertx.core.Future;

/**
 * A client for publishing messages to Pub/Sub.
 */
public interface PubSubPublisherClient extends AutoCloseable {

    /**
     * Publishes a message to Pub/Sub and transfer the returned ApiFuture into a Future.
     *
     * @param pubsubMessage The message to publish.
     * @return The messageId wrapped in a Future.
     */
    Future<String> publish(PubsubMessage pubsubMessage);
}
