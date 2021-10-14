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

package org.eclipse.hono.notification.deviceregistry;

import java.time.Instant;
import java.util.Objects;

import org.eclipse.hono.client.notification.Notification;

import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

/**
 * Base class for the notifications of the Device Registry.
 */
@JsonTypeIdResolver(NotificationTypeResolver.class)
public abstract class AbstractDeviceRegistryNotification implements Notification {

    private final String source;
    private final Instant timestamp;

    /**
     * Creates a new instance.
     *
     * @param source The canonical name of the component that publishes the notification.
     * @param timestamp The timestamp of the event (Unix epoch, UTC, in milliseconds).
     * @throws NullPointerException If any of the parameters are {@code null}.
     */
    protected AbstractDeviceRegistryNotification(final String source, final Instant timestamp) {
        this.source = Objects.requireNonNull(source);
        this.timestamp = Objects.requireNonNull(timestamp);
    }

    @Override
    public abstract String getType();

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

}
