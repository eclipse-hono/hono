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

import java.util.List;

import org.eclipse.hono.notification.deviceregistry.AllDevicesOfTenantDeletedNotification;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.util.Constants;

/**
 * Constants used by the notification API.
 */
public class NotificationConstants {

    /**
     * The name of the notification API endpoint.
     */
    public static final String NOTIFICATION_ENDPOINT = "notification";

    public static final String CONTENT_TYPE = "application/json";
    /**
     * The field name of the JSON object that indicates the class into the object can be decoded.
     */
    public static final String JSON_FIELD_TYPE = "type";
    /**
     * The field name of the JSON object that indicates the component that publishes the notification.
     */
    public static final String JSON_FIELD_SOURCE = "source";
    /**
     * The field name of the JSON object that indicates the creation time of the notification.
     */
    public static final String JSON_FIELD_CREATION_TIME = "creation-time";

    /**
     * The canonical name of the Device Registry to indicate the component that publishes a notification.
     */
    public static final String SOURCE_DEVICE_REGISTRY = "device-registry";
    /**
     * The field name of the JSON object that indicates the change.
     */
    public static final String JSON_FIELD_DATA_CHANGE = "change";
    /**
     * The field name of the JSON object that indicates if the entity is enabled.
     */
    public static final String JSON_FIELD_DATA_ENABLED = "enabled";
    /**
     * The field name of the JSON object that indicates if cache invalidation is required on update operations.
     */
    public static final String JSON_FIELD_DATA_INVALIDATE_CACHE_ON_UPDATE = "invalidate-cache-on-update";
    /**
     * The field name of the JSON object that indicates the tenant ID.
     */
    public static final String JSON_FIELD_TENANT_ID = Constants.JSON_FIELD_TENANT_ID;
    /**
     * The field name of the JSON object that indicates the device ID.
     */
    public static final String JSON_FIELD_DEVICE_ID = Constants.JSON_FIELD_DEVICE_ID;
    /**
     * All device registry notification types.
     */
    public static final List<NotificationType<?>> DEVICE_REGISTRY_NOTIFICATION_TYPES = List.of(
            TenantChangeNotification.TYPE,
            DeviceChangeNotification.TYPE,
            CredentialsChangeNotification.TYPE,
            AllDevicesOfTenantDeletedNotification.TYPE);

    private NotificationConstants() {
        // prevent instantiation
    }

}
