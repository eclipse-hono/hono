/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.service.management;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

import org.eclipse.hono.annotation.HonoTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The base class for implementing a DTO (Data Transfer Object) to store data in registry implementations.
 *
 * @param <T> The type of the data object
 */
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseDto<T> {

    /**
     * The name of the JSON property containing the point in time when the object was initially created.
     */
    public static final String FIELD_CREATED = "created";
    /**
     * The default name of the JSON property to map the object data to.
     */
    public static final String FIELD_DATA = "data";
    /**
     * The name of the property that contains the identifier of a tenant.
     */
    public static final String FIELD_TENANT_ID = "tenant-id";
    /**
     * The name of the JSON property containing the point in time of the object's last modification.
     */
    public static final String FIELD_UPDATED_ON = "updatedOn";
    /**
     * The name of the JSON property containing the object's resource version.
     */
    public static final String FIELD_VERSION = "version";

    @JsonProperty(FIELD_TENANT_ID)
    private String tenantId;

    @JsonProperty(FIELD_VERSION)
    private String version;

    @JsonProperty(FIELD_CREATED)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    @HonoTimestamp
    private Instant creationTime;

    @JsonProperty(FIELD_UPDATED_ON)
    @HonoTimestamp
    private Instant updatedOn;

    private T data;

    /**
     * Default constructor for serialisation/deserialization.
     */
    protected BaseDto() {
        // Explicit default constructor.
    }

    /**
     * Creates a DTO for persisting an object instance.
     *
     * @param supplier The supplier to use for creating the concrete DTO instance.
     * @param data The object instance to persist.
     * @param version The object's (initial) resource version.
     *
     * @param <P> The type of object to persist.
     * @param <T> The concrete type of DTO being created.
     *
     * @return The DTO.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static <P, T extends BaseDto<P>> T forCreation(final Supplier<T> supplier, final P data, final String version) {

        Objects.requireNonNull(supplier);
        Objects.requireNonNull(data);
        Objects.requireNonNull(version);

        final T dto = supplier.get();
        dto.setCreationTime(Instant.now());
        dto.setData(data);
        dto.setVersion(version);

        return dto;
    }

    /**
     * Creates a DTO for updating data in the store.
     *
     * @param supplier The supplier to use for creating the concrete DTO instance.
     * @param data The object instance to update.
     * @param version The new resource version to use for the object in the store.
     *
     * @param <P> The type of object to update.
     * @param <T> The concrete type of DTO being created.
     *
     * @return The DTO.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static <P, T extends BaseDto<P>> T forUpdate(final Supplier<T> supplier, final P data, final String version) {

        Objects.requireNonNull(supplier);
        Objects.requireNonNull(data);
        Objects.requireNonNull(version);

        final T dto = supplier.get();
        dto.setUpdatedOn(Instant.now());
        dto.setData(data);
        dto.setVersion(version);

        return dto;
    }

    /**
     * Creates a DTO for data that has been read from a persistent store.
     *
     * @param supplier The supplier to use for creating the concrete DTO instance.
     * @param data The data from the store.
     * @param created The point in time when the object was created initially in the store (may be {@code null}).
     * @param updated The point in time when the object was updated most recently in the store (may be {@code null}).
     * @param version The object's resource version in the store (may be {@code null}).
     *
     * @param <P> The type of object being read.
     * @param <T> The concrete type of DTO being created.
     *
     * @return The DTO.
     * @throws NullPointerException if supplier or data are {@code null}.
     */
    public static <P, T extends BaseDto<P>> T forRead(
            final Supplier<T> supplier,
            final P data,
            final Instant created,
            final Instant updated,
            final String version) {

        Objects.requireNonNull(supplier);
        Objects.requireNonNull(data);

        final T dto = supplier.get();
        dto.setCreationTime(created);
        dto.setUpdatedOn(updated);
        dto.setData(data);
        dto.setVersion(version);

        return dto;
    }

    /**
     * Gets the identifier of the tenant that the object belongs to.
     *
     * @return The identifier.
     */
    public final String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the identifier of the tenant that the object belongs to.
     *
     * @param tenantId The identifier.
     * @throws NullPointerException if the identifier is {@code null}.
     */
    protected final void setTenantId(final String tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId);
    }

    /**
     * Gets the version of the object.
     *
     * @return The version.
     */
    public final String getVersion() {
        return version;
    }

    /**
     * Sets the version of the object.
     *
     * @param version The version of the object or {@code null} if unknown.
     */
    protected final void setVersion(final String version) {
        this.version = version;
    }

    /**
     * Gets the point in time at which the object has been last updated.
     *
     * @return The object's time of last modification.
     */
    public final Instant getUpdatedOn() {
        return updatedOn;
    }

    /**
     * Sets the point in time at which the object has been last updated.
     *
     * @param updatedOn The object's time of last modification.
     */
    protected final void setUpdatedOn(final Instant updatedOn) {
        this.updatedOn = updatedOn;
    }

    /**
     * Gets the point in time at which the object has been initially created.
     *
     * @return The Object's creation time.
     */
    public final Instant getCreationTime() {
        return creationTime;
    }

    /**
     * Sets the point in time at which the object has been initially created.
     *
     * @param creationTime The object's creation time.
     */
    protected final void setCreationTime(final Instant creationTime) {
        this.creationTime = creationTime;
    }

    /**
     * Gets the data object.
     * <p>
     * Subclasses overriding this method must also add a {@link JsonProperty} annotation defining
     * the name of the JSON property that the object should be mapped to.
     * <p>
     * If this method is not overridden, then the data will be mapped to property {@value #FIELD_DATA}.
     *
     * @return The data object.
     */
    @JsonProperty(FIELD_DATA)
    public T getData() {
        return data;
    }

    /**
     * Sets the data object of this DTO.
     * <p>
     * This method may be overridden by subclasses to include validation logic dependent on the payload type.
     *
     * @param data The data object.
     */
    protected void setData(final T data) {
        this.data = data;
    }

    /**
     * Gets the name of the user who did the most recent modification.
     *
     * @return The user's name.
     */
    public final String getLastUser() {
        // not implemented yet
        return null;
    }
}
