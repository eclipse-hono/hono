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

import org.eclipse.hono.util.Constants;

/**
 * Constants used for notifications of the Device Registry.
 */
public final class NotificationConstants {

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
     * The field name of the JSON object that indicates the tenant ID.
     */
    public static final String JSON_FIELD_TENANT_ID = Constants.JSON_FIELD_TENANT_ID;
    /**
     * The field name of the JSON object that indicates the device ID.
     */
    public static final String JSON_FIELD_DEVICE_ID = Constants.JSON_FIELD_DEVICE_ID;

    private NotificationConstants() {
        // prevent instantiation
    }

}
