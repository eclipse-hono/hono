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

import java.util.Objects;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * A client that consumes notifications.
 */
public class NotificationConsumer {

    private final String type;
    private final String address;
    private final Handler<Buffer> notificationHandler;

    /**
     * Creates an instance.
     *
     * @param type The type of the notification to consume as returned by {@link Notification#getType()}.
     * @param address The address to consume the notification from as returned by {@link Notification#getAddress()}.
     * @param notificationHandler The handler to invoke with each notification.
     */
    public NotificationConsumer(final String type, final String address,
            final Handler<Buffer> notificationHandler) {

        Objects.requireNonNull(type);
        Objects.requireNonNull(address);
        Objects.requireNonNull(notificationHandler);

        this.type = type;
        this.address = address;
        this.notificationHandler = notificationHandler;

    }

    /**
     * Gets the type of the notification to consume.
     *
     * @return The type name as returned by {@link Notification#getType()}.
     */
    public String getType() {
        return type;
    }

    /**
     * Gets the address to consume the notification from.
     *
     * @return The type name as returned by {@link Notification#getAddress()}.
     */
    public String getAddress() {
        return address;
    }

    /**
     * Handles a received notification.
     *
     * @param json A buffer containing the serialized notification.
     */
    public void handle(final Buffer json) {
        if (json != null) {
            notificationHandler.handle(json);
        }
    }

    /**
     * Closes the client.
     *
     * @return A future indicating the outcome of the operation.
     */
    public Future<Void> close() {
        return Future.succeededFuture();
    }

}
