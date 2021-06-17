/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

/**
 * The base class for implementing a DTO (Data Transfer Object) to store data in registry implementations.
 *
 * @param <T> The type of the data object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseDto<T> {

    /**
     * The name of the JSON property containing the point in time when the object was initially created.
     */
    public static final String FIELD_CREATED = "created";
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
     * Constructs a new DTO for use with the <b>creation of a new</b> persistent entry.
     *
     * @param supplier A DTO subclass' constructor of which a new instance shall be created.
     * @param data The data of the DTO.
     * @param version The version of the DTO
     *
     * @param <P> The type of the DTO's payload.
     * @param <T> The type of the DTO subclass.
     *
     * @return A DTO instance for creating a new entry.
     */
    public static <P, T extends BaseDto<P>> T forCreation(final Supplier<T> supplier, final P data, final String version) {
        final T dto = supplier.get();
        dto.setCreationTime(Instant.now());
        dto.setData(data);
        dto.setVersion(version);

        return dto;
    }

    /**
     * Constructs a new DTO for <b>updating</b> a persistent entry.
     *
     * @param supplier A DTO subclass' constructor of which a new instance shall be created.
     * @param data The data of the DTO.
     * @param version The version of the DTO
     *
     * @param <P> The type of the DTO's payload.
     * @param <T> The type of the DTO subclass.
     *
     * @return A DTO instance for updating an entry.
     */
    public static <P, T extends BaseDto<P>> T forUpdate(final Supplier<T> supplier, final P data, final String version) {
        final T dto = supplier.get();
        dto.setUpdatedOn(Instant.now());
        dto.setData(data);
        dto.setVersion(version);

        return dto;
    }

    /**
     * Constructs a new DTO to be returned by a read operation.
     *
     * @param supplier A DTO subclass' constructor of which a new instance shall be created.
     * @param data The data of the DTO.
     * @param created The instant when the object was created.
     * @param updated The instant of the most recent update.
     * @param version The version of the DTO
     *
     * @param <P> The type of the DTO's payload.
     * @param <T> The type of the DTO subclass.
     *
     * @return A DTO instance for updating an entry.
     */
    public static <P, T extends BaseDto<P>> T forRead(final Supplier<T> supplier, final P data, final Instant created, final Instant updated, final String version) {
        final T dto = supplier.get();
        dto.setCreationTime(created);
        dto.setUpdatedOn(updated);
        dto.setData(data);
        dto.setVersion(version);

        return dto;
    }

    /**
     * Gets the identifier of the tenant that the data belongs to.
     *
     * @return The identifier.
     */
    public final String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the identifier of the tenant that the data belongs to.
     *
     * @param tenantId The identifier.
     * @throws NullPointerException if the identifier is {@code null}.
     */
    protected final void setTenantId(final String tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId);
    }

    /**
     * Gets the version of the document.
     *
     * @return The version of the document.
     */
    public final String getVersion() {
        return version;
    }

    /**
     * Sets the version of the document.
     *
     * @param version The version of the document or {@code null} if not set.
     */
    protected final void setVersion(final String version) {
        this.version = version;
    }

    /**
     * Gets the date and time of last modification.
     *
     * @return The date and time of last modification.
     */
    public final Instant getUpdatedOn() {
        return updatedOn;
    }

    /**
     * Sets the date and time of last modification.
     *
     * @param updatedOn The date and time of last modification.
     */
    protected final void setUpdatedOn(final Instant updatedOn) {
        this.updatedOn = updatedOn;
    }

    /**
     * Gets the date and time when the entity detailed by this status was created.
     *
     * @return The entity's creation time.
     */
    public final Instant getCreationTime() {
        return creationTime;
    }

    /**
     * Sets the date and time when the entity detailed by this status was created.
     *
     * @param creationTime The entity's creation time.
     */
    protected final void setCreationTime(final Instant creationTime) {
        this.creationTime = creationTime;
    }

    /**
     * Gets the data object (i.e. what should be editable by clients) of this DTO.
     * This method may be overridden by subclasses to set the JSON property name to a more meaningful one.
     *
     * @return The data object.
     */
    @JsonProperty(FIELD_DATA)
    public T getData() {
        return data;
    }

    /**
     * Sets the data object of this DTO.
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
