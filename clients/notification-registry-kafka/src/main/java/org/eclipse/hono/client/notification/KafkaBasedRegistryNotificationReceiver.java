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

import java.util.Map;

import org.eclipse.hono.notification.deviceregistry.AbstractDeviceRegistryNotification;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;

/**
 * A client for receiving Device Registry notifications with Kafka.
 */
public class KafkaBasedRegistryNotificationReceiver extends KafkaBasedNotificationReceiver {

    /**
     * Creates a client for consuming Device Registry notifications.
     *
     * @param vertx The vert.x instance to be used.
     * @param consumerConfig The configuration for the Kafka consumer.
     *
     * @throws NullPointerException if any parameter is {@code null}.
     * @throws IllegalArgumentException if consumerConfig does not contain a valid configuration (at least a bootstrap
     *             server).
     */
    public KafkaBasedRegistryNotificationReceiver(final Vertx vertx, final Map<String, String> consumerConfig) {
        super(vertx, consumerConfig);
    }

    @Override
    protected <T extends Notification> String getAddressForType(final Class<T> notificationType) {
        if (TenantChangeNotification.class.equals(notificationType)) {
            return TenantChangeNotification.ADDRESS;
        } else if (DeviceChangeNotification.class.equals(notificationType)) {
            return DeviceChangeNotification.ADDRESS;
        } else if (CredentialsChangeNotification.class.equals(notificationType)) {
            return CredentialsChangeNotification.ADDRESS;
        }
        throw new IllegalArgumentException("Unknown notification type " + notificationType.getName());
    }

    @Override
    protected AbstractDeviceRegistryNotification decodeNotification(final Buffer json) {
        return Json.decodeValue(json, AbstractDeviceRegistryNotification.class);
    }

}
