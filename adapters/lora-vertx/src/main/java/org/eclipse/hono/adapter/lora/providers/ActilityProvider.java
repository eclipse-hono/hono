/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.adapter.lora.GatewayInfo;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;
import org.springframework.stereotype.Component;

import com.google.common.io.BaseEncoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Actility.
 */
@Component
public class ActilityProvider extends JsonBasedLoraProvider {

    private static final String FIELD_ACTILITY_ADR = "ADRbit";
    private static final String FIELD_ACTILITY_CHANNEL = "Channel";
    private static final String FIELD_ACTILITY_DEVICE_EUI = "DevEUI";
    private static final String FIELD_ACTILITY_FPORT = "FPort";
    private static final String FIELD_ACTILITY_FRAME_COUNT_UPLINK = "FCntUp";
    private static final String FIELD_ACTILITY_LATITUTDE = "LrrLAT";
    private static final String FIELD_ACTILITY_LONGITUDE = "LrrLON";
    private static final String FIELD_ACTILITY_ROOT_OBJECT = "DevEUI_uplink";
    private static final String FIELD_ACTILITY_PAYLOAD = "payload_hex";
    private static final String FIELD_ACTILITY_LRR = "Lrr";
    private static final String FIELD_ACTILITY_LRR_ID = "Lrrid";
    private static final String FIELD_ACTILITY_LRR_RSSI = "LrrRSSI";
    private static final String FIELD_ACTILITY_LRR_SNR = "LrrSNR";
    private static final String FIELD_ACTILITY_LRRS = "Lrrs";
    private static final String FIELD_ACTILITY_SPREADING_FACTOR = "SpFact";

    @Override
    public String getProviderName() {
        return "actility";
    }

    @Override
    public String pathPrefix() {
        return "/actility";
    }

    private Optional<JsonObject> getRootObject(final JsonObject loraMessage) {
        return LoraUtils.getChildObject(loraMessage, FIELD_ACTILITY_ROOT_OBJECT, JsonObject.class);
    }

    @Override
    protected String getDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return getRootObject(loraMessage)
                .map(root -> root.getValue(FIELD_ACTILITY_DEVICE_EUI))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device EUI property"));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return getRootObject(loraMessage)
                .map(root -> root.getValue(FIELD_ACTILITY_PAYLOAD))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(s -> Buffer.buffer(BaseEncoding.base16().decode(s.toUpperCase())))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued payload property"));
    }

    @Override
    protected LoraMessageType getMessageType(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return getRootObject(loraMessage)
                .map(root -> LoraMessageType.UPLINK)
                .orElse(LoraMessageType.UNKNOWN);
    }

    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        return getRootObject(loraMessage)
            .map(this::extractMetaData)
            .orElse(null);
    }

    private LoraMetaData extractMetaData(final JsonObject rootObject) {

        final LoraMetaData data = new LoraMetaData();

        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_SPREADING_FACTOR, String.class)
            .ifPresent(s -> data.setSpreadingFactor(Integer.valueOf(s)));
        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_FPORT, String.class)
            .ifPresent(s -> data.setFunctionPort(Integer.valueOf(s)));
        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_FRAME_COUNT_UPLINK, String.class)
            .ifPresent(s -> data.setFrameCount(Integer.valueOf(s)));
        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_ADR, String.class)
            .ifPresent(s -> data.setAdaptiveDataRateEnabled(s.equals("1") ? Boolean.TRUE : Boolean.FALSE));
        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_CHANNEL, String.class)
            .map(this::getFrequency)
            .ifPresent(data::setFrequency);

        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_LRRS, JsonObject.class)
            .map(lrrs -> lrrs.getValue(FIELD_ACTILITY_LRR))
            .filter(JsonArray.class::isInstance)
            .map(JsonArray.class::cast)
            .ifPresent(lrrList -> {
                final Optional<String> gwId = LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_LRR_ID, String.class);
                lrrList.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .map(this::extractGatewayInfo)
                    .forEach(gateway -> {
                        Optional.ofNullable(gateway.getGatewayId())
                            .ifPresent(s -> gwId.ifPresent(id -> {
                                if (id.equals(s)) {
                                    Optional.ofNullable(LoraUtils.newLocationFromString(
                                            LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_LONGITUDE, String.class),
                                            LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_LATITUTDE, String.class),
                                            Optional.empty()))
                                        .ifPresent(gateway::setLocation);
                                }
                            }));
                        data.addGatewayInfo(gateway);
                    });
            });
        return data;
    }

    /**
     * Gets the frequency corresponding to the channel ID used by Actility/ThingWork
     * as described in section 2.4 of the
     * <a href="https://partners.thingpark.com/sites/default/files/2017-11/AdvancedThingParkDeveloperGuide_V4.pdf">
     * Advanced Developer Guide</a>.
     *
     * @param logicalChannelId The channel ID.
     * @return The frequency in MHz or {@code null} if the identifier is unknown.
     */
    private Double getFrequency(final String logicalChannelId) {
        switch (logicalChannelId) {
        case "LC1":
            return 868.1;
        case "LC2":
            return 868.3;
        case "LC3":
            return 868.5;
        case "RX2":
            return 869.525;
        default:
            return null;
        }
    }

    private GatewayInfo extractGatewayInfo(final JsonObject lrr) {
        final GatewayInfo gateway = new GatewayInfo();
        LoraUtils.getChildObject(lrr, FIELD_ACTILITY_LRR_ID, String.class)
            .ifPresent(gateway::setGatewayId);
        LoraUtils.getChildObject(lrr, FIELD_ACTILITY_LRR_RSSI, String.class)
            .ifPresent(s -> gateway.setRssi(Double.valueOf(s).intValue()));
        LoraUtils.getChildObject(lrr, FIELD_ACTILITY_LRR_SNR, String.class)
            .ifPresent(s -> gateway.setSnr(Double.valueOf(s)));
        return gateway;
    }
}
