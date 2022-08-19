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

package org.eclipse.hono.notification.deviceregistry;

import java.time.Instant;
import java.util.Objects;

import org.eclipse.hono.annotation.HonoTimestamp;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationConstants;
import org.eclipse.hono.notification.NotificationType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Notification that informs about changes on a tenant.
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class TenantChangeNotification extends AbstractNotification {

    public static final String TYPE_NAME = "tenant-change-v1";
    public static final String ADDRESS = "registry-tenant";
    public static final NotificationType<TenantChangeNotification> TYPE = new NotificationType<>(
            TYPE_NAME,
            TenantChangeNotification.class,
            ADDRESS);

    private final LifecycleChange change;
    private final String tenantId;
    private final boolean tenantEnabled;
    private final boolean invalidateCacheOnUpdate;

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
            final boolean tenantEnabled,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_INVALIDATE_CACHE_ON_UPDATE, required = true)
            final boolean invalidateCacheOnUpdate) {

        super(source, creationTime);

        this.change = Objects.requireNonNull(change);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.tenantEnabled = tenantEnabled;
        this.invalidateCacheOnUpdate = invalidateCacheOnUpdate;
    }

    /**
     * Creates an instance.
     *
     * @param change The type of change to notify about.
     * @param tenantId The ID of the tenant.
     * @param creationTime The creation time of the event.
     * @param tenantEnabled {@code true} if the tenant is enabled.
     * @param invalidateCacheOnUpdate {@code true} if cache invalidation is required on update operation.
     * @throws NullPointerException If any of the parameters are {@code null}.
     */
    public TenantChangeNotification(final LifecycleChange change, final String tenantId, final Instant creationTime,
            final boolean tenantEnabled, final boolean invalidateCacheOnUpdate) {
        this(NotificationConstants.SOURCE_DEVICE_REGISTRY, creationTime, change, tenantId, tenantEnabled,
                invalidateCacheOnUpdate);
    }

    /**
     * Gets the change that caused the notification.
     *
     * @return The change.
     */
    @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_CHANGE)
    public LifecycleChange getChange() {
        return change;
    }

    /**
     * Gets the ID of the changed tenant.
     *
     * @return The tenant ID.
     */
    @JsonProperty(value = NotificationConstants.JSON_FIELD_TENANT_ID)
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Checks if the tenant is enabled.
     *
     * @return {@code true} if this tenant is enabled.
     */
    @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_ENABLED)
    public boolean isTenantEnabled() {
        return tenantEnabled;
    }

    /**
     * Checks whether update operations on this tenant requires cache invalidation.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @return {@code true} if cache invalidation is required.
     */
    @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_INVALIDATE_CACHE_ON_UPDATE)
    public boolean isInvalidateCacheOnUpdate() {
        return invalidateCacheOnUpdate;
    }

    @Override
    @JsonIgnore
    public NotificationType<TenantChangeNotification> getType() {
        return TYPE;
    }

    @Override
    @JsonIgnore
    public String getKey() {
        return getTenantId();
    }

    @Override
    public String toString() {
        return "TenantChangeNotification{" +
                "change=" + change +
                ", tenantId='" + tenantId + '\'' +
                ", tenantEnabled=" + tenantEnabled +
                ", invalidateCacheOnUpdate=" + invalidateCacheOnUpdate +
                ", creationTime='" + getCreationTime() + '\'' +
                '}';
    }
}
