/**
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
 */


package org.eclipse.hono.adapter.lora;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A 3D geo-location that consists of longitude, latitude and (optional) altitude.
 *
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Location {

    @JsonProperty(LoraConstants.APP_PROPERTY_FUNCTION_LONGITUDE)
    private Double longitude;
    @JsonProperty(LoraConstants.APP_PROPERTY_FUNCTION_LATITUDE)
    private Double latitude;
    @JsonProperty(LoraConstants.APP_PROPERTY_FUNCTION_ALTITUDE)
    private Double altitude;

    /**
     * Creates a new location for coordinates.
     *
     * @param longitude The longitude in decimal degrees.
     * @param latitude The latitude in decimal degrees.
     * @param altitude The altitude in meters or {@code null} if unknown.
     * @throws NullPointerException if longitude or latitude are {@code null}.
     */
    public Location(
            @JsonProperty(value = LoraConstants.APP_PROPERTY_FUNCTION_LONGITUDE, required = true) final Double longitude,
            @JsonProperty(value = LoraConstants.APP_PROPERTY_FUNCTION_LATITUDE, required = true) final Double latitude,
            @JsonProperty(LoraConstants.APP_PROPERTY_FUNCTION_ALTITUDE) final Double altitude) {
        this.longitude = Objects.requireNonNull(longitude);
        this.latitude = Objects.requireNonNull(latitude);
        this.altitude = altitude;
    }

    /**
     * Gets the longitude of the location.
     *
     * @return The longitude in decimal degrees.
     */
    public Double getLongitude() {
        return longitude;
    }

    /**
     * Gets the latitude of the location.
     *
     * @return The latitude in decimal degrees.
     */
    public Double getLatitude() {
        return latitude;
    }

    /**
     * Gets the altitude of the location.
     *
     * @return The altitude in meters.
     */
    public Double getAltitude() {
        return altitude;
    }
}
