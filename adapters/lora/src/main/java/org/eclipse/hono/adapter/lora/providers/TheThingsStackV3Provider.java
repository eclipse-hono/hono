/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.hono.adapter.lora.GatewayInfo;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Things Stack V3.
 * <p>
 * This provider supports messages that comply with the
 * <a href="https://www.thethingsindustries.com/docs/reference/data-formats/#uplink-messages">
 * TTS V3 data format</a>.
 */
@ApplicationScoped
public class TheThingsStackV3Provider extends JsonBasedLoraProvider {

    private static final String FIELD_TTN_ALTITUDE = "altitude";
    private static final String FIELD_TTN_BANDWIDTH = "bandwidth";
    private static final String FIELD_TTN_CHANNEL_INDEX = "channel_index";
    private static final String FIELD_TTN_CODING_RATE = "coding_rate";
    private static final String FIELD_TTN_DATA_RATE = "data_rate";
    private static final String FIELD_TTN_DEVICE_EUI = "dev_eui";
    private static final String FIELD_TTN_EUI = "eui";
    private static final String FIELD_TTN_FRAME_COUNT = "f_cnt";
    private static final String FIELD_TTN_FREQUENCY = "frequency";
    private static final String FIELD_TTN_FPORT = "f_port";
    private static final String FIELD_TTN_FRM_PAYLOAD = "frm_payload";
    private static final String FIELD_TTN_LATITUDE = "latitude";
    private static final String FIELD_TTN_LONGITUDE = "longitude";
    private static final String FIELD_TTN_LORA = "lora";
    private static final String FIELD_TTN_RSSI = "rssi";
    private static final String FIELD_TTN_SPREADING_FACTOR = "spreading_factor";
    private static final String FIELD_TTN_SNR = "snr";
    private static final String FIELD_TTN_UPLINK_MESSAGE = "uplink_message";
    private static final String FIELD_TTN_SETTINGS = "settings";

    private static final String OBJECT_END_DEVICE_IDS = "end_device_ids";
    private static final String OBJECT_GW_IDS = "gateway_ids";
    private static final String OBJECT_LOCATION = "location";
    private static final String OBJECT_RX_METADATA = "rx_metadata";

    @Override
    public String getProviderName() {
        return "theThingsStackV3";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/thethingsstackV3");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected LoraMessageType getMessageType(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        if (loraMessage.containsKey(FIELD_TTN_UPLINK_MESSAGE)) {
            return LoraMessageType.UPLINK;
        } else {
            return LoraMessageType.UNKNOWN;
        }
    }

    @Override
    protected String getDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        return LoraUtils.getChildObject(loraMessage, OBJECT_END_DEVICE_IDS, JsonObject.class)
            .flatMap(endDeviceIds -> LoraUtils.getChildObject(endDeviceIds, FIELD_TTN_DEVICE_EUI, String.class))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        return LoraUtils.getChildObject(loraMessage, FIELD_TTN_UPLINK_MESSAGE, JsonObject.class)
                .flatMap(uplink -> LoraUtils.getChildObject(uplink, FIELD_TTN_FRM_PAYLOAD, String.class))
            .map(s -> Buffer.buffer(Base64.getDecoder().decode(s)))
            .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain Base64 encoded payload property"));
    }

    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        final LoraMetaData data = new LoraMetaData();

        LoraUtils.getChildObject(loraMessage, FIELD_TTN_UPLINK_MESSAGE, JsonObject.class)
                .map(uplink -> {
                    LoraUtils.getChildObject(uplink, FIELD_TTN_FRAME_COUNT, Integer.class)
                        .ifPresent(data::setFrameCount);
                    LoraUtils.getChildObject(uplink, FIELD_TTN_FPORT, Integer.class)
                        .ifPresent(data::setFunctionPort);

                    LoraUtils.getChildObject(uplink, FIELD_TTN_SETTINGS, JsonObject.class)
                        .ifPresent(settings -> {
                            LoraUtils.getChildObject(settings, FIELD_TTN_CODING_RATE, String.class)
                                .ifPresent(data::setCodingRate);

                            LoraUtils.getChildObject(settings, FIELD_TTN_FREQUENCY, String.class)
                                .map(Integer::valueOf)
                                .map(frequency -> frequency / 1000000.0)
                                .ifPresent(data::setFrequency);

                            LoraUtils.getChildObject(settings, FIELD_TTN_DATA_RATE, JsonObject.class)
                                .flatMap(dataRate -> LoraUtils.getChildObject(dataRate, FIELD_TTN_LORA, JsonObject.class))
                                .ifPresent(loraDataRate -> {
                                    LoraUtils.getChildObject(loraDataRate, FIELD_TTN_BANDWIDTH, Integer.class).map(bandwidth -> bandwidth / 1000).ifPresent(data::setBandwidth);
                                    LoraUtils.getChildObject(loraDataRate, FIELD_TTN_SPREADING_FACTOR, Integer.class).ifPresent(data::setSpreadingFactor);
                                });
                        });
                    return uplink.getValue(OBJECT_RX_METADATA);
                })
            .filter(JsonArray.class::isInstance)
            .map(JsonArray.class::cast)
            .ifPresent(gws -> {
                gws.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .forEach(gw -> {

                        final GatewayInfo gwInfo = new GatewayInfo();
                        LoraUtils.getChildObject(gw, OBJECT_GW_IDS, JsonObject.class)
                            .flatMap(gwId -> LoraUtils.getChildObject(gwId, FIELD_TTN_EUI, String.class))
                            .ifPresent(gwInfo::setGatewayId);

                        LoraUtils.getChildObject(gw, FIELD_TTN_CHANNEL_INDEX, Integer.class)
                            .ifPresent(gwInfo::setChannel);
                        LoraUtils.getChildObject(gw, FIELD_TTN_RSSI, Integer.class)
                            .ifPresent(gwInfo::setRssi);
                        LoraUtils.getChildObject(gw, FIELD_TTN_SNR, Double.class)
                            .ifPresent(gwInfo::setSnr);

                        LoraUtils.getChildObject(gw, OBJECT_LOCATION, JsonObject.class)
                            .ifPresent(location -> {
                                Optional.ofNullable(LoraUtils.newLocation(
                                        LoraUtils.getChildObject(location, FIELD_TTN_LONGITUDE, Double.class),
                                        LoraUtils.getChildObject(location, FIELD_TTN_LATITUDE, Double.class),
                                        LoraUtils.getChildObject(location, FIELD_TTN_ALTITUDE, Double.class)))
                                    .ifPresent(gwInfo::setLocation);
                            });

                        data.addGatewayInfo(gwInfo);
                    });
            });

        return data;
    }
}
