/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.pubsub.subscriber;

import io.vertx.core.Future;

/**
 * A client for receiving messages from Pub/Sub.
 * <p>
 * Wraps a Pub/Sub Subscriber.
 * </p>
 */
public interface PubSubSubscriberClient extends AutoCloseable {

    /**
     * Subscribes messages from Pub/Sub.
     *
     * @param keepTrying Condition that controls whether another attempt to subscribe to a subscription should be
     *            started. Client code can set this to {@code false} in order to prevent further attempts.
     * @return A future indicating the outcome of the operation.
     */
    Future<Void> subscribe(boolean keepTrying);

}
