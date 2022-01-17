/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.notification;

import org.eclipse.hono.util.Lifecycle;

import io.vertx.core.Handler;

/**
 * A client that supports receiving Hono's (internal) notifications.
 *
 */
public interface NotificationReceiver extends Lifecycle {

    /**
     * Registers a notification consumer for a specific type of notifications.
     * <p>
     * Has to be invoked before the {@link #start()} method is called.
     *
     * @param notificationType The class of the notifications to consume.
     * @param consumer The handler to be invoked with the received notification.
     * @param <T> The type of notifications to consume.
     * @throws IllegalStateException If invoked after the {@link #start()} method was called.
     */
    <T extends AbstractNotification> void registerConsumer(NotificationType<T> notificationType, Handler<T> consumer);
}
