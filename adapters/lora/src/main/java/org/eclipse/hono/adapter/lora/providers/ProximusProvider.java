/*******************************************************************************
 * Copyright (c) 2019, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora.providers;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.adapter.lora.GatewayInfo;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;

import com.google.common.io.BaseEncoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A LoRaWAN provider with API for Proximus.
 * <p>
 * This provider supports messages that comply with the
 * <a href="https://proximusapi.enco.io/asset/lora4maker/documentation#dataformat">
 * Proximus data format</a>.
 */
@ApplicationScoped
public class ProximusProvider extends JsonBasedLoraProvider {

    private static final String FIELD_PROXIMUS_ADR = "Adrbit";
    private static final String FIELD_PROXIMUS_DEVICE_EUI = "DevEUI";
    private static final String FIELD_PROXIMUS_FRAME_COUNT = "Fcntup";
    private static final String FIELD_PROXIMUS_LATITUDE = "latitude";
    private static final String FIELD_PROXIMUS_LONGITUDE = "longitude";
    private static final String FIELD_PROXIMUS_PAYLOAD = "payload";
    private static final String FIELD_PROXIMUS_PORT = "FPort";
    private static final String FIELD_PROXIMUS_RSSI = "Lrrrssi";
    private static final String FIELD_PROXIMUS_SNR = "Lrrsnr";
    private static final String FIELD_PROXIMUS_SPREADING_FACTOR = "Spfact";

    @Override
    public String getProviderName() {
        return "proximus";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/proximus");
    }

    /**
     * {@inheritDoc}
     *
     * @return Always {@link LoraMessageType#UPLINK}.
     */
    @Override
    protected LoraMessageType getMessageType(final JsonObject loraMessage) {
        return LoraMessageType.UPLINK;
    }

    @Override
    protected byte[] getDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_PROXIMUS_DEVICE_EUI, String.class)
                .map(LoraUtils::convertFromHexToBytes)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        return LoraUtils.getChildObject(loraMessage, FIELD_PROXIMUS_PAYLOAD, String.class)
                .map(s -> Buffer.buffer(BaseEncoding.base16().decode(s.toUpperCase())))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain HEX encoded payload property"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        final LoraMetaData data = new LoraMetaData();
        LoraUtils.getChildObject(loraMessage, FIELD_PROXIMUS_ADR, String.class)
            .ifPresent(v -> data.setAdaptiveDataRateEnabled(v.equals("1") ? Boolean.TRUE : Boolean.FALSE));
        LoraUtils.getChildObject(loraMessage, FIELD_PROXIMUS_FRAME_COUNT, String.class)
            .map(Integer::valueOf)
            .ifPresent(data::setFrameCount);
        LoraUtils.getChildObject(loraMessage, FIELD_PROXIMUS_PORT, String.class)
            .map(Integer::valueOf)
            .ifPresent(data::setFunctionPort);
        LoraUtils.getChildObject(loraMessage, FIELD_PROXIMUS_SPREADING_FACTOR, String.class)
            .map(Integer::valueOf)
            .ifPresent(data::setSpreadingFactor);
        Optional.ofNullable(LoraUtils.newLocationFromString(
                LoraUtils.getChildObject(loraMessage, FIELD_PROXIMUS_LONGITUDE, String.class),
                LoraUtils.getChildObject(loraMessage, FIELD_PROXIMUS_LATITUDE, String.class),
                Optional.empty()))
            .ifPresent(data::setLocation);

        final GatewayInfo gwInfo = new GatewayInfo();
        LoraUtils.getChildObject(loraMessage, FIELD_PROXIMUS_RSSI, String.class)
            .map(Double::valueOf)
            .map(Double::intValue)
            .ifPresent(gwInfo::setRssi);
        LoraUtils.getChildObject(loraMessage, FIELD_PROXIMUS_SNR, String.class)
            .map(Double::valueOf)
            .ifPresent(gwInfo::setSnr);
        data.addGatewayInfo(gwInfo);
        return data;
    }
}
