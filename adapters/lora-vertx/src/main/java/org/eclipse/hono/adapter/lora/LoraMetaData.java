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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A container for meta information contained in Lora
 * messages.
 */
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
    @JsonProperty(LoraConstants.DATA_RATE)
    private Integer dataRate;
    @JsonProperty(LoraConstants.DATA_RATE_ID)
    private String dataRateIdentifier;
    @JsonProperty(LoraConstants.CODING_RATE)
    private String codingRateIdentifier;
    @JsonProperty(LoraConstants.ADAPTIVE_DATA_RATE_ENABLED)
    private Boolean adaptiveDataRateEnabled;
    @JsonProperty(LoraConstants.APP_PROPERTY_SPREADING_FACTOR)
    private Integer spreadingFactor;
    @JsonProperty(LoraConstants.LOCATION)
    private Location location;
    @JsonProperty(LoraConstants.GATEWAYS)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<GatewayInfo> gatewayInfo = new LinkedList<>();

    /**
     * @return The bandwidth in kHz.
     */
    public Integer getBandwidth() {
        return bandwidth;
    }

    /**
     * @param bandwidth The bandwidth in kHz.
     * @return This object for command chaining.
     */
    public LoraMetaData setBandwidth(final Integer bandwidth) {
        this.bandwidth = bandwidth;
        return this;
    }

    /**
     * @return The functionPort.
     */
    public Integer getFunctionPort() {
        return functionPort;
    }

    /**
     * @param functionPort The functionPort to set.
     * @return This object for command chaining.
     */
    public LoraMetaData setFunctionPort(final Integer functionPort) {
        this.functionPort = functionPort;
        return this;
    }

    /**
     * @return The frameCount.
     */
    public Integer getFrameCount() {
        return frameCount;
    }

    /**
     * @param frameCount The frameCount to set.
     * @return This object for command chaining.
     */
    public LoraMetaData setFrameCount(final Integer frameCount) {
        this.frameCount = frameCount;
        return this;
    }

    /**
     * @return The frequency in mHz.
     */
    public Double getFrequency() {
        return frequency;
    }

    /**
     * @param frequency The frequency in mHz.
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
     * @return The dataRate.
     */
    public Integer getDataRate() {
        return dataRate;
    }

    /**
     * @param dataRate The dataRate to set.
     * @return This object for command chaining.
     */
    public LoraMetaData setDataRate(final Integer dataRate) {
        this.dataRate = dataRate;
        return this;
    }

    /**
     * @return The dataRateIdentifier.
     */
    public String getDataRateIdentifier() {
        return dataRateIdentifier;
    }

    /**
     * @param dataRateIdentifier The dataRateIdentifier to set.
     * @return This object for command chaining.
     */
    public LoraMetaData setDataRateIdentifier(final String dataRateIdentifier) {
        this.dataRateIdentifier = dataRateIdentifier;
        return this;
    }

    /**
     * @return The codingRateIdentifier.
     */
    public String getCodingRateIdentifier() {
        return codingRateIdentifier;
    }

    /**
     * @param codingRateIdentifier The codingRateIdentifier to set.
     * @return This object for command chaining.
     */
    public LoraMetaData setCodingRateIdentifier(final String codingRateIdentifier) {
        this.codingRateIdentifier = codingRateIdentifier;
        return this;
    }

    /**
     * @return The adaptiveDataRateEnabled.
     */
    public Boolean getAdaptiveDataRateEnabled() {
        return adaptiveDataRateEnabled;
    }

    /**
     * @param adaptiveDataRateEnabled The adaptiveDataRateEnabled to set.
     * @return This object for command chaining.
     */
    public LoraMetaData setAdaptiveDataRateEnabled(final Boolean adaptiveDataRateEnabled) {
        this.adaptiveDataRateEnabled = adaptiveDataRateEnabled;
        return this;
    }

    /**
     * @return The spreadingFactor.
     */
    public Integer getSpreadingFactor() {
        return spreadingFactor;
    }

    /**
     * @param spreadingFactor The spreadingFactor to set.
     * @return This object for command chaining.
     */
    public LoraMetaData setSpreadingFactor(final Integer spreadingFactor) {
        this.spreadingFactor = spreadingFactor;
        return this;
    }

    public Location getLocation() {
        return location;
    }

    /**
     * Sets the location of the device.
     *
     * @param location The location.
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
