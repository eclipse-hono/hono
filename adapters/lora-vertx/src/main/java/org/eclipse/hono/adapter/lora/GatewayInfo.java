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

/**
 * A container for meta information about a Lora gateway.
 */
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

    public String getGatewayId() {
        return gatewayId;
    }

    /**
     * Sets the gateway's identifier.
     *
     * @param id The identifier.
     * @throws NullPointerException if id is {@code null}.
     */
    public void setGatewayId(final String id) {
        this.gatewayId = Objects.requireNonNull(id);
    }

    public Integer getChannel() {
        return channel;
    }

    /**
     * Sets the concentrator IF channel used for RX.
     *
     * @param channel The channel.
     * @return This object for command chaining.
     */
    public GatewayInfo setChannel(final Integer channel) {
        this.channel = channel;
        return this;
    }

    public Location getLocation() {
        return location;
    }

    /**
     * Sets the location of the receiving gateway.
     *
     * @param location The location.
     * @return This object for command chaining.
     */
    public GatewayInfo setLocation(final Location location) {
        this.location = location;
        return this;
    }

    public Double getSnr() {
        return snr;
    }

    /**
     * Sets the Lora SNR ratio.
     *
     * @param snr The ration in dB.
     * @return This object for command chaining.
     */
    public GatewayInfo setSnr(final Double snr) {
        this.snr = snr;
        return this;
    }

    public Integer getRssi() {
        return rssi;
    }

    /**
     * @param rssi The RSSI value to set.
     * @return This object for command chaining.
     */
    public GatewayInfo setRssi(final Integer rssi) {
        this.rssi = rssi;
        return this;
    }
}
