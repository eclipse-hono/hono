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
package org.eclipse.hono.deviceregistry.mongodb.model;

import java.time.Instant;
import java.util.Objects;

import org.eclipse.hono.annotation.HonoTimestamp;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDeviceRegistryUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The base class for implementing a DTO (Data Transfer Object) to store data in mongodb.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseDto {

    @JsonProperty(value = MongoDbDeviceRegistryUtils.FIELD_VERSION, required = true)
    private String version;
    @JsonProperty(value = MongoDbDeviceRegistryUtils.FIELD_UPDATED_ON, required = true)
    @HonoTimestamp
    private Instant updatedOn;

    /**
     * Default constructor for serialisation/deserialization.
     */
    public BaseDto() {
        // Explicit default constructor.
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
}
