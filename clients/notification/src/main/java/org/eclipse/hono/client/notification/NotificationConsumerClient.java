/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.notification;

import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * A client that supports receiving Hono's (internal) notifications.
 *
 * @param <T> The type of notification to consume.
 */
public interface NotificationConsumerClient<T extends Notification> {

    /**
     * Creates a client for consuming notifications.
     *
     * @param notificationHandler The handler to invoke with every notification received. The message passed in will be
     *            acknowledged automatically if the handler does not throw an exception.
     *            <p>
     *            Implementors are encouraged to specify in detail the types of exceptions that handler might throw,
     *            what kind of problem they indicate and what the consequences regarding the underlying messaging
     *            infrastructure will be.
     * @param closeHandler An (optional) handler to be invoked when the consumer is being closed by the peer. The
     *            handler will be invoked with an exception indicating the cause of the consumer being closed or
     *            {@code null} if unknown.
     *            <p>
     *            Implementors are encouraged to specify in detail the types of exceptions that might be passed in, what
     *            kind of problem they indicate and what the consequences regarding the underlying messaging
     *            infrastructure will be.
     * @return A future that will complete with the consumer once it is ready. The future will fail if the consumer
     *         cannot be started.
     * @throws NullPointerException if notificationHandler is {@code null}.
     */
    Future<NotificationConsumer> createConsumer(Handler<T> notificationHandler,
            Handler<Throwable> closeHandler);

}
