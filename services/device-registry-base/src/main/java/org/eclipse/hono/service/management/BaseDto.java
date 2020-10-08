/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import java.util.function.Supplier;

import org.eclipse.hono.annotation.HonoTimestamp;
import org.eclipse.hono.util.RegistryManagementConstants;

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

    public static final String FIELD_UPDATED_ON = "updatedOn";
    public static final String FIELD_VERSION = "version";
    public static final String FIELD_DATA = "data";

    @JsonProperty(FIELD_VERSION)
    private String version;

    @JsonProperty(RegistryManagementConstants.FIELD_STATUS_CREATION_DATE)
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
    public BaseDto() {
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
    public Instant getCreationTime() {
        return creationTime;
    }

    /**
     * Sets the date and time when the entity detailed by this status was created.
     *
     * @param creationTime The entity's creation time.
     */
    protected void setCreationTime(final Instant creationTime) {
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
    public String getLastUser() {
        return null;
    }
}
