/*******************************************************************************
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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

import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.adapter.lora.GatewayInfo;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;

import com.google.common.io.BaseEncoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A LoRaWAN provider with API for The Thing Stack.
 */
@ApplicationScoped
public class TheThingsStackProvider extends JsonBasedLoraProvider {

    private static final String FIELD_THE_THINGS_STACK_ALTITUDE = "altitude";
    private static final String FIELD_THE_THINGS_STACK_BANDWIDTH = "bandwidth";
    private static final String FIELD_THE_THINGS_STACK_CODING_RATE = "coding_rate";
    private static final String FIELD_THE_THINGS_STACK_CONFIRMED = "confirmed";
    private static final String FIELD_THE_THINGS_STACK_DATA_RATE = "data_rate";
    private static final String FIELD_THE_THINGS_STACK_DECODED_PAYLOAD = "decoded_payload";
    private static final String FIELD_THE_THINGS_STACK_DEV_EUI = "dev_eui";
    private static final String FIELD_THE_THINGS_STACK_DOWNLINKS = "downlinks";
    private static final String FIELD_THE_THINGS_STACK_END_DEVICE_IDS = "end_device_ids";
    private static final String FIELD_THE_THINGS_STACK_EUI = "eui";
    private static final String FIELD_THE_THINGS_STACK_F_CNT = "f_cnt";
    private static final String FIELD_THE_THINGS_STACK_F_PORT = "f_port";
    private static final String FIELD_THE_THINGS_STACK_FREQUENCY = "frequency";
    private static final String FIELD_THE_THINGS_STACK_FRM_PAYLOAD = "frm_payload";
    private static final String FIELD_THE_THINGS_STACK_GATEWAY_IDS = "gateway_ids";
    private static final String FIELD_THE_THINGS_STACK_JOIN_ACCEPT = "join_accept";
    private static final String FIELD_THE_THINGS_STACK_LATITUDE = "latitude";
    private static final String FIELD_THE_THINGS_STACK_LOCATIONS = "locations";
    private static final String FIELD_THE_THINGS_STACK_LONGITUDE = "longitude";
    private static final String FIELD_THE_THINGS_STACK_LORA = "lora";
    private static final String FIELD_THE_THINGS_STACK_PORT = "f_port";
    private static final String FIELD_THE_THINGS_STACK_PRIORITY = "priority";
    private static final String FIELD_THE_THINGS_STACK_RSSI = "rssi";
    private static final String FIELD_THE_THINGS_STACK_RX_METADATA = "rx_metadata";
    private static final String FIELD_THE_THINGS_STACK_SETTINGS = "settings";
    private static final String FIELD_THE_THINGS_STACK_SNR = "snr";
    private static final String FIELD_THE_THINGS_STACK_SPREADING_FACTOR = "spreading_factor";
    private static final String FIELD_THE_THINGS_STACK_UPLINK = "uplink_message";
    private static final String FIELD_THE_THINGS_STACK_USER = "user";
    private static final String VALUE_THE_THINGS_STACK_NORMAL = "normal";

    @Override
    public String getProviderName() {
        return "theThingsStack";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/thethingsstack");
    }


    private Optional<JsonObject> getUplinkObject(final JsonObject loraMessage) {
        return LoraUtils.getChildObject(loraMessage, FIELD_THE_THINGS_STACK_UPLINK, JsonObject.class);
    }

    private Optional<JsonObject> getLoraSettingsObject(final JsonObject settings) {
        return LoraUtils.getChildObject(settings, FIELD_THE_THINGS_STACK_DATA_RATE, JsonObject.class)
            .flatMap(dataRate -> LoraUtils.getChildObject(dataRate, FIELD_THE_THINGS_STACK_LORA, JsonObject.class));
    }

    private Optional<JsonObject> getSettingsObject(final JsonObject uplink) {
        return LoraUtils.getChildObject(uplink, FIELD_THE_THINGS_STACK_SETTINGS, JsonObject.class);
    }

    private Optional<JsonObject> getUserLocationsObject(final JsonObject loraMessage) {
        return LoraUtils.getChildObject(loraMessage, FIELD_THE_THINGS_STACK_LOCATIONS, JsonObject.class)
            .flatMap(locations -> LoraUtils.getChildObject(locations, FIELD_THE_THINGS_STACK_USER, JsonObject.class));
    }

    @Override
    protected LoraMessageType getMessageType(final JsonObject loraMessage) {
        if (loraMessage.containsKey(FIELD_THE_THINGS_STACK_UPLINK)) {
            return LoraMessageType.UPLINK;
        }
        if (loraMessage.containsKey(FIELD_THE_THINGS_STACK_JOIN_ACCEPT)) {
            return LoraMessageType.JOIN;
        }
        if (loraMessage.containsKey(FIELD_THE_THINGS_STACK_DOWNLINKS)) {
            return LoraMessageType.DOWNLINK;
        }
        return LoraMessageType.UNKNOWN;
    }

    @Override
    protected byte[] getDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_THE_THINGS_STACK_END_DEVICE_IDS, JsonObject.class)
            .map(meta -> meta.getValue(FIELD_THE_THINGS_STACK_DEV_EUI))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(LoraUtils::convertFromHexToBytes)
            .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return getUplinkObject(loraMessage)
            .map(uplink -> uplink.getValue(FIELD_THE_THINGS_STACK_FRM_PAYLOAD))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(s -> Buffer.buffer(Base64.getDecoder().decode(s)))
            .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain Base64 encoded payload property"));
    }

    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        final LoraMetaData data = new LoraMetaData();

        getUplinkObject(loraMessage)
            .map(uplink -> {
                getSettingsObject(uplink).ifPresent(settings -> {
                    getLoraSettingsObject(settings).ifPresent(loraSettings -> {
                        LoraUtils.getChildObject(loraSettings, FIELD_THE_THINGS_STACK_BANDWIDTH, Integer.class).map(bandwidth -> bandwidth / 1000).ifPresent(data::setBandwidth);
                        LoraUtils.getChildObject(loraSettings, FIELD_THE_THINGS_STACK_SPREADING_FACTOR, Integer.class).ifPresent(data::setSpreadingFactor);
                    });
                    LoraUtils.getChildObject(settings, FIELD_THE_THINGS_STACK_FREQUENCY, String.class).map(frequencyString -> Double.valueOf(frequencyString)).map(frequency -> frequency / 1000000.0).ifPresent(data::setFrequency);
                    LoraUtils.getChildObject(settings, FIELD_THE_THINGS_STACK_CODING_RATE, String.class).ifPresent(data::setCodingRate);
                });

                LoraUtils.getChildObject(uplink, FIELD_THE_THINGS_STACK_F_PORT, Integer.class).ifPresent(data::setFunctionPort);
                LoraUtils.getChildObject(uplink, FIELD_THE_THINGS_STACK_F_CNT, Integer.class).ifPresent(data::setFrameCount);
                getUserLocationsObject(uplink).ifPresent(userLocation -> {
                    Optional.ofNullable(
                        LoraUtils.newLocation(
                            LoraUtils.getChildObject(userLocation, FIELD_THE_THINGS_STACK_LONGITUDE, Double.class),
                            LoraUtils.getChildObject(userLocation, FIELD_THE_THINGS_STACK_LATITUDE, Double.class),
                            LoraUtils.getChildObject(userLocation, FIELD_THE_THINGS_STACK_ALTITUDE, Double.class)))
                        .ifPresent(data::setLocation);

                });
                return uplink.getValue(FIELD_THE_THINGS_STACK_RX_METADATA);
            })
        .filter(JsonArray.class::isInstance)
        .map(JsonArray.class::cast)
        .ifPresent(metas -> metas.stream()
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .forEach(meta -> {
                LoraUtils.getChildObject(meta, FIELD_THE_THINGS_STACK_GATEWAY_IDS, JsonObject.class)
                    .flatMap(gatewayIds -> LoraUtils.getChildObject(gatewayIds, FIELD_THE_THINGS_STACK_EUI, String.class))
                    .ifPresent(gatewayEui -> {
                        final GatewayInfo gwInfo = new GatewayInfo();
                        gwInfo.setGatewayId(gatewayEui);
                        LoraUtils.getChildObject(meta, FIELD_THE_THINGS_STACK_SNR, Double.class).ifPresent(gwInfo::setSnr);
                        LoraUtils.getChildObject(meta, FIELD_THE_THINGS_STACK_RSSI, Integer.class).ifPresent(gwInfo::setRssi);
                        data.addGatewayInfo(gwInfo);
                });

            })
        );

        return data;
    }

    @Override
    protected JsonObject getCommandPayload(final Buffer payload, final String deviceId, final String subject) {
        int port;
        try {
            port = Integer.parseInt(subject);
        } catch (final NumberFormatException e) {
            port = 2;
        }

        final JsonObject json = new JsonObject();
        json.put(FIELD_THE_THINGS_STACK_DECODED_PAYLOAD, BaseEncoding.base16().encode(payload.getBytes()));
        json.put(FIELD_THE_THINGS_STACK_CONFIRMED, false);
        json.put(FIELD_THE_THINGS_STACK_PRIORITY, VALUE_THE_THINGS_STACK_NORMAL);
        json.put(FIELD_THE_THINGS_STACK_PORT, port);
        return json;
    }
}
