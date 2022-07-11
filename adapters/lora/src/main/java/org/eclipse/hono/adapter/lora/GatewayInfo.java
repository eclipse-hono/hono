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
 * A container for meta information about a Lora gateway.
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayInfo {

    @JsonProperty(LoraConstants.APP_PROPERTY_SNR)
    private Double snr;
    @JsonProperty(LoraConstants.APP_PROPERTY_RSS)
    private Integer rssi;
    @JsonProperty(LoraConstants.GATEWAY_ID)
    private String gatewayId;
    @JsonProperty(LoraConstants.APP_PROPERTY_CHANNEL)
    private Integer channel;
    @JsonProperty(LoraConstants.LOCATION)
    private Location location;

    /**
     * Gets the gateway's identifier.
     *
     * @return The identifier or {@code null} if unknown.
     * @throws NullPointerException if id is {@code null}.
     */
    public String getGatewayId() {
        return gatewayId;
    }

    /**
     * Sets the gateway's identifier.
     *
     * @param id The identifier or {@code null} if unknown.
     * @throws NullPointerException if id is {@code null}.
     */
    public void setGatewayId(final String id) {
        this.gatewayId = Objects.requireNonNull(id);
    }

    /**
     * Gets the concentrator IF channel that the gateway used for receiving
     * the data.
     *
     * @return The channel or {@code null} if unknown.
     */
    public Integer getChannel() {
        return channel;
    }

    /**
     * Sets the concentrator IF channel that the gateway used for receiving
     * the data.
     *
     * @param channel The channel or {@code null} if unknown.
     * @return This object for command chaining.
     */
    public GatewayInfo setChannel(final Integer channel) {
        this.channel = channel;
        return this;
    }

    /**
     * Gets the location of the receiving gateway.
     *
     * @return The location or {@code null} if unknown.
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Sets the location of the receiving gateway.
     *
     * @param location The location or {@code null} if unknown.
     * @return This object for command chaining.
     */
    public GatewayInfo setLocation(final Location location) {
        this.location = location;
        return this;
    }

    /**
     * Gets the signal-to-noise ratio (SNR) detected by the
     * gateway when receiving the data.
     *
     * @return The ratio in dB or {@code null} if unknown.
     */
    public Double getSnr() {
        return snr;
    }

    /**
     * Sets the signal-to-noise ratio (SNR) detected by the
     * gateway when receiving the data.
     *
     * @param snr The ratio in dB or {@code null} if unknown.
     * @return This object for command chaining.
     */
    public GatewayInfo setSnr(final Double snr) {
        this.snr = snr;
        return this;
    }

    /**
     * Gets the received signal strength indicator (RSSI) detected by the
     * gateway when receiving the data.
     *
     * @return The RSSI value in dBm or {@code null} if unknown.
     */
    public Integer getRssi() {
        return rssi;
    }

    /**
     * Sets the received signal strength indicator (RSSI) detected by the
     * gateway when receiving the data.
     *
     * @param rssi The RSSI value in dBm or {@code null} if unknown.
     * @return This object for command chaining.
     * @throws IllegalArgumentException if the rssi value is positive.
     */
    public GatewayInfo setRssi(final Integer rssi) {
        if (rssi != null && rssi.intValue() > 0) {
            throw new IllegalArgumentException("RSSI value must be a negative integer");
        }
        this.rssi = rssi;
        return this;
    }
}
