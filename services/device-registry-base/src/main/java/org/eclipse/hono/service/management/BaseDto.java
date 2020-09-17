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
import java.util.Objects;

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

    @JsonProperty(value = FIELD_VERSION, required = true)
    private String version;

    @JsonProperty(RegistryManagementConstants.FIELD_STATUS_CREATION_DATE)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    @HonoTimestamp
    private Instant creationTime;

    @JsonProperty(value = FIELD_UPDATED_ON, required = true)
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
     * Creates a new DTO object holding the given data with its creation time initialized at <pre>now</pre>.
     *
     * @param data The data object
     */
    public BaseDto(final T data) {
        this(Instant.now(), null, data);
    }

    /**
     * Creates a new DTO object.
     *
     * @param creationTime The time when the data object was created.
     * @param updatedOn The time when the data object was most recently updated.
     * @param data The data object.
     */
    public BaseDto(final Instant creationTime, final Instant updatedOn, final T data) {
        this.creationTime = creationTime;
        this.updatedOn = updatedOn;
        this.data = data;
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
     * @throws NullPointerException if the version is {@code null}.
     */
    public final void setVersion(final String version) {
        this.version = Objects.requireNonNull(version);
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
     * @throws NullPointerException if the last modification date and time is {@code null}.
     */
    public final void setUpdatedOn(final Instant updatedOn) {
        this.updatedOn = Objects.requireNonNull(updatedOn);
    }

    public Instant getLastUpdate() {
        return this.updatedOn;
    }

    public final void setLastUpdate(final Instant updatedOn) {
        this.updatedOn = updatedOn;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(final Instant creationTime) {
        this.creationTime = creationTime;
    }

    /**
     * Gets the data object of this DTO. This method may be overriden by subclasses to set the JSON property name
     * to a more meaningful one.
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
    public void setData(final T data) {
        this.updatedOn = Instant.now();
        this.data = data;
    }

    public String getLastUser() {
        return null;
    }
}
