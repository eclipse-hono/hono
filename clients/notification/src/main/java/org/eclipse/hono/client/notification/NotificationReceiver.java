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

import org.eclipse.hono.util.Lifecycle;

/**
 * A client that supports receiving Hono's (internal) notifications.
 *
 */
public interface NotificationReceiver extends Lifecycle {

    /**
     * Registers a notification consumer for aa specific type of notifications.
     * <p>
     * Implementations are expected to return the method quickly. Long-running operations should be executed at the
     * subsequent start of the consumer in {@link #start()}.
     *
     * @param consumer The consumer to be registered.
     * @throws IllegalStateException if this method is invoked after the receiver has been started.
     */
    void addConsumer(NotificationConsumer consumer);
}
