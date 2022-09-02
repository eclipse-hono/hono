/**
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
 */


package org.eclipse.hono.adapter.lora;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A container for meta information contained in Lora
 * messages.
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class LoraMetaData {

    @JsonProperty(LoraConstants.APP_PROPERTY_BANDWIDTH)
    private Integer bandwidth;
    @JsonProperty(LoraConstants.APP_PROPERTY_FUNCTION_PORT)
    private Integer functionPort;
    @JsonProperty(LoraConstants.FRAME_COUNT)
    private Integer frameCount;
    @JsonProperty(LoraConstants.FREQUENCY)
    private Double frequency;
    @JsonProperty(LoraConstants.CODING_RATE)
    private String codingRate;
    @JsonProperty(LoraConstants.ADAPTIVE_DATA_RATE_ENABLED)
    private Boolean adaptiveDataRateEnabled;
    @JsonProperty(LoraConstants.APP_PROPERTY_SPREADING_FACTOR)
    private Integer spreadingFactor;
    @JsonProperty(LoraConstants.LOCATION)
    private Location location;
    @JsonProperty(LoraConstants.GATEWAYS)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<GatewayInfo> gatewayInfo = new ArrayList<>();

    /**
     * Gets the bandwidth used by the device's radio for sending the data.
     *
     * @return The bandwidth in kHz or {@code null} if unknown.
     * @see <a href="https://docs.exploratory.engineering/lora/dr_sf/">
     * Data Rate and Spreading Factor</a>
     */
    public Integer getBandwidth() {
        return bandwidth;
    }

    /**
     * Sets the bandwidth used by the device's radio for sending the data.
     *
     * @param bandwidth The bandwidth in kHz or {@code null} if unknown.
     * @return This object for command chaining.
     * @see <a href="https://docs.exploratory.engineering/lora/dr_sf/">
     * Data Rate and Spreading Factor</a>
     */
    public LoraMetaData setBandwidth(final Integer bandwidth) {
        this.bandwidth = bandwidth;
        return this;
    }

    /**
     * Gets the function port number used to represent the type and/or
     * characteristics of the payload data.
     *
     * @return The port number or {@code null} if unknown.
     */
    public Integer getFunctionPort() {
        return functionPort;
    }

    /**
     * Sets the function port number used to represent the type and/or
     * characteristics of the payload data.
     *
     * @param functionPort The port number or {@code null} if unknown.
     * @return This object for command chaining.
     */
    public LoraMetaData setFunctionPort(final Integer functionPort) {
        this.functionPort = functionPort;
        return this;
    }

    /**
     * Gets the number of uplink messages that have been sent by the device
     * since the beginning of the LoRa network session.
     *
     * @return The number of messages or {@code null} if unknown.
     */
    public Integer getFrameCount() {
        return frameCount;
    }

    /**
     * Sets the number of uplink messages that have been sent by the device
     * since the beginning of the LoRa network session.
     *
     * @param frameCount The number of messages or {@code null} if unknown.
     * @return This object for command chaining.
     */
    public LoraMetaData setFrameCount(final Integer frameCount) {
        this.frameCount = frameCount;
        return this;
    }

    /**
     * Gets the frequency used by the device's radio for sending the data.
     *
     * @return The frequency in mHz or {@code null} if unknown.
     */
    public Double getFrequency() {
        return frequency;
    }

    /**
     * Sets the frequency used by the device's radio for sending the data.
     *
     * @param frequency The frequency in mHz or {@code null} if unknown.
     *
     * @return This object for command chaining.
     * @throws IllegalArgumentException if frequency is negative.
     */
    public LoraMetaData setFrequency(final Double frequency) {
        if (frequency < 0) {
            throw new IllegalArgumentException("frequency must be positive");
        }
        this.frequency = frequency;
        return this;
    }

    /**
     * Gets the coding rate used by the device to send the data.
     *
     * @return The coding rate or {@code null} if unknown.
     * @see <a href="https://en.wikipedia.org/wiki/Code_rate">Code Rate</a>
     */
    public String getCodingRate() {
        return codingRate;
    }

    /**
     * Sets the coding rate used by the device to send the data.
     *
     * @param codingRate The coding rate or {@code null} if unknown.
     * @return This object for command chaining.
     */
    public LoraMetaData setCodingRate(final String codingRate) {
        this.codingRate = codingRate;
        return this;
    }

    /**
     * Checks if the network server uses Adaptive Data Rate (ADR) control to optimize
     * the device's data rate.
     *
     * @return {@code true} if ADR is in use or {@code null} if unknown.
     */
    public Boolean getAdaptiveDataRateEnabled() {
        return adaptiveDataRateEnabled;
    }

    /**
     * Sets whether the network server uses Adaptive Data Rate (ADR) control to optimize
     * the device's data rate.
     *
     * @param flag {@code true} if ADR is in use or {@code null} if unknown.
     * @return This object for command chaining.
     */
    public LoraMetaData setAdaptiveDataRateEnabled(final Boolean flag) {
        this.adaptiveDataRateEnabled = flag;
        return this;
    }

    /**
     * Gets the spreading factor used by the device's radio to send the data.
     *
     * @return The spreading factor or {@code null} if unknown.
     * @see <a href="https://docs.exploratory.engineering/lora/dr_sf/">
     * Data Rate and Spreading Factor</a>
     */
    public Integer getSpreadingFactor() {
        return spreadingFactor;
    }

    /**
     * Sets the spreading factor used by the device's radio to send the data.
     *
     * @param spreadingFactor The spreading factor or {@code null} if unknown.
     * @return This object for command chaining.
     * @throws IllegalArgumentException if the spreading factor is smaller than 7 or greater than 12.
     * @see <a href="https://docs.exploratory.engineering/lora/dr_sf/">
     * Data Rate and Spreading Factor</a>
     */
    public LoraMetaData setSpreadingFactor(final Integer spreadingFactor) {

        if (spreadingFactor != null && (spreadingFactor < 7 || spreadingFactor > 12)) {
            throw new IllegalArgumentException("spreading factor must be > 6 and < 13");
        }
        this.spreadingFactor = spreadingFactor;
        return this;
    }

    /**
     * Gets the location of the device.
     *
     * @return The location or {@code null} if unknown.
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Sets the location of the device.
     *
     * @param location The location or {@code null} if unknown.
     * @return This object for command chaining.
     */
    public LoraMetaData setLocation(final Location location) {
        this.location = location;
        return this;
    }

    /**
     * Adds meta information for a gateway.
     *
     * @param gwInfo The meta information.
     * @return This object for command chaining.
     * @throws NullPointerException if info is {@code null}.
     */
    @JsonIgnore
    public LoraMetaData addGatewayInfo(final GatewayInfo gwInfo) {
        Objects.requireNonNull(gwInfo);
        this.gatewayInfo.add(gwInfo);
        return this;
    }

    /**
     * Gets meta information for the gateways.
     *
     * @return An unmodifiable list of meta information.
     */
    public List<GatewayInfo> getGatewayInfo() {
        return Collections.unmodifiableList(gatewayInfo);
    }
}
