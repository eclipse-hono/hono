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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Notification that informs about changes on a tenant.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantChangeNotification extends AbstractNotification {

    public static final String TYPE = "tenant-change-v1";
    public static final String ADDRESS = "registry-tenant";

    private final LifecycleChange change;
    private final String tenantId;
    private final boolean enabled;

    @JsonCreator
    TenantChangeNotification(
            @JsonProperty(value = NotificationConstants.JSON_FIELD_SOURCE, required = true)
            final String source,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_CREATION_TIME, required = true)
            @HonoTimestamp
            final Instant creationTime,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_CHANGE, required = true)
            final LifecycleChange change,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_TENANT_ID, required = true)
            final String tenantId,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_ENABLED, required = true)
            final boolean enabled) {

        super(source, creationTime);

        this.change = Objects.requireNonNull(change);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.enabled = enabled;
    }

    /**
     * Creates an instance.
     *
     * @param change The type of change to notify about.
     * @param tenantId The ID of the tenant.
     * @param creationTime The creation time of the event.
     * @param enabled {@code true} if the device is enabled.
     * @throws NullPointerException If any of the parameters are {@code null}.
     */
    public TenantChangeNotification(final LifecycleChange change, final String tenantId, final Instant creationTime,
            final boolean enabled) {
        this(NotificationConstants.SOURCE_DEVICE_REGISTRY, creationTime, change, tenantId, enabled);
    }

    /**
     * Gets the change that caused the notification.
     *
     * @return The change.
     */
    @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_CHANGE)
    public final LifecycleChange getChange() {
        return change;
    }

    /**
     * Gets the ID of the changed tenant.
     *
     * @return The tenant ID.
     */
    @JsonProperty(value = NotificationConstants.JSON_FIELD_TENANT_ID)
    public final String getTenantId() {
        return tenantId;
    }

    /**
     * Checks if the tenant is enabled.
     *
     * @return {@code true} if this tenant is enabled.
     */
    @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_ENABLED)
    public final boolean isEnabled() {
        return enabled;
    }

    @Override
    @JsonIgnore
    public final String getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        return "TenantChangeNotification{" +
                "change=" + change +
                ", tenantId='" + tenantId + '\'' +
                ", enabled=" + enabled +
                ", creationTime='" + getCreationTime() + '\'' +
                '}';
    }
}
