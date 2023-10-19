/*
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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

import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * A no-op implementation for the notification receiver.
 */
public final class NoOpNotificationReceiver implements NotificationReceiver {

    @Override
    public <T extends AbstractNotification> void registerConsumer(final NotificationType<T> notificationType,
            final Handler<T> consumer) {
        // do nothing
    }

    @Override
    public Future<Void> start() {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> stop() {
        return Future.succeededFuture();
    }
}
