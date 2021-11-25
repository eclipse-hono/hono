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

import org.eclipse.hono.annotation.HonoTimestamp;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationConstants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Notification that informs that all devices of a tenant have been deleted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AllDevicesOfTenantDeletedNotification extends AbstractNotification {

    public static final String TYPE = "all-devices-of-tenant-deleted-v1";
    public static final String ADDRESS = DeviceChangeNotification.ADDRESS;

    @JsonProperty(value = NotificationConstants.JSON_FIELD_TENANT_ID, required = true)
    private final String tenantId;

    @JsonCreator
    AllDevicesOfTenantDeletedNotification(
            @JsonProperty(value = NotificationConstants.JSON_FIELD_SOURCE, required = true) final String source,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_CREATION_TIME, required = true) @HonoTimestamp final Instant creationTime,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_TENANT_ID, required = true) final String tenantId) {

        super(source, creationTime);

        this.tenantId = Objects.requireNonNull(tenantId);
    }

    /**
     * Creates an instance.
     *
     * @param tenantId The ID of the tenant.
     * @param creationTime The creation time of the event.
     * @throws NullPointerException If any of the parameters are {@code null}.
     */
    public AllDevicesOfTenantDeletedNotification(final String tenantId, final Instant creationTime) {
        this(NotificationConstants.SOURCE_DEVICE_REGISTRY, creationTime, tenantId);
    }

    /**
     * Gets the ID of the tenant which devices have been deleted.
     *
     * @return The tenant ID.
     */
    public final String getTenantId() {
        return tenantId;
    }

    @Override
    public final String getType() {
        return TYPE;
    }

}
