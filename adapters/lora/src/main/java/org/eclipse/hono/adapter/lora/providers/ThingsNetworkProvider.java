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

import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.hono.adapter.lora.GatewayInfo;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A LoRaWAN provider with API for Things Network.
 */
@ApplicationScoped
public class ThingsNetworkProvider extends JsonBasedLoraProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ThingsNetworkProvider.class);
    private static final Pattern PATTERN_DATA_RATE = Pattern.compile("^SF(\\d+)BW(\\d+)$");

    private static final String FIELD_TTN_ALTITUDE = "altitude";
    private static final String FIELD_TTN_CHANNEL = "channel";
    private static final String FIELD_TTN_CODING_RATE = "coding_rate";
    private static final String FIELD_TTN_DATA_RATE = "data_rate";
    private static final String FIELD_TTN_DEVICE_EUI = "hardware_serial";
    private static final String FIELD_TTN_FRAME_COUNT = "counter";
    private static final String FIELD_TTN_FREQUENCY = "frequency";
    private static final String FIELD_TTN_FPORT = "port";
    private static final String FIELD_TTN_GW_EUI = "gtw_id";
    private static final String FIELD_TTN_LATITUDE = "latitude";
    private static final String FIELD_TTN_LONGITUDE = "longitude";
    private static final String FIELD_TTN_PAYLOAD_RAW = "payload_raw";
    private static final String FIELD_TTN_RSSI = "rssi";
    private static final String FIELD_TTN_SNR = "snr";

    private static final String OBJECT_GATEWAYS = "gateways";
    private static final String OBJECT_META_DATA = "metadata";

    @Override
    public String getProviderName() {
        return "ttn";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/ttn");
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
        return LoraUtils.getChildObject(loraMessage, FIELD_TTN_DEVICE_EUI, String.class)
                .map(LoraUtils::convertFromHexToBytes)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        if (loraMessage.containsKey(FIELD_TTN_PAYLOAD_RAW) && loraMessage.getValue(FIELD_TTN_PAYLOAD_RAW) == null) {
            // ... this is an empty payload message, still valid.
            return Buffer.buffer();
        }

        return LoraUtils.getChildObject(loraMessage, FIELD_TTN_PAYLOAD_RAW, String.class)
                .map(s -> Buffer.buffer(Base64.getDecoder().decode(s)))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain Base64 encoded payload property"));
    }

    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        final LoraMetaData data = new LoraMetaData();

        LoraUtils.getChildObject(loraMessage, FIELD_TTN_FRAME_COUNT, Integer.class)
            .ifPresent(data::setFrameCount);
        LoraUtils.getChildObject(loraMessage, FIELD_TTN_FPORT, Integer.class)
            .ifPresent(data::setFunctionPort);

        LoraUtils.getChildObject(loraMessage, OBJECT_META_DATA, JsonObject.class)
            .map(meta -> {

                LoraUtils.getChildObject(meta, FIELD_TTN_CODING_RATE, String.class)
                .ifPresent(data::setCodingRate);

                LoraUtils.getChildObject(meta, FIELD_TTN_DATA_RATE, String.class)
                    .ifPresent(dataRate -> {
                        final Matcher matcher = PATTERN_DATA_RATE.matcher(dataRate);
                        if (matcher.matches()) {
                            data.setSpreadingFactor(Integer.parseInt(matcher.group(1)));
                            data.setBandwidth(Integer.parseInt(matcher.group(2)));
                        } else {
                            LOG.debug("invalid data rate [{}]", dataRate);
                        }
                    });

                LoraUtils.getChildObject(meta, FIELD_TTN_FREQUENCY, Double.class)
                    .ifPresent(data::setFrequency);

                return meta.getValue(OBJECT_GATEWAYS);
            })
            .filter(JsonArray.class::isInstance)
            .map(JsonArray.class::cast)
            .ifPresent(gws -> {
                gws.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .forEach(gw -> {
                        final GatewayInfo gwInfo = new GatewayInfo();
                        LoraUtils.getChildObject(gw, FIELD_TTN_GW_EUI, String.class)
                            .ifPresent(gwInfo::setGatewayId);
                        LoraUtils.getChildObject(gw, FIELD_TTN_CHANNEL, Integer.class)
                            .ifPresent(gwInfo::setChannel);
                        LoraUtils.getChildObject(gw, FIELD_TTN_RSSI, Integer.class)
                            .ifPresent(gwInfo::setRssi);
                        LoraUtils.getChildObject(gw, FIELD_TTN_SNR, Double.class)
                            .ifPresent(gwInfo::setSnr);
                        Optional.ofNullable(LoraUtils.newLocation(
                                LoraUtils.getChildObject(gw, FIELD_TTN_LONGITUDE, Double.class),
                                LoraUtils.getChildObject(gw, FIELD_TTN_LATITUDE, Double.class),
                                LoraUtils.getChildObject(gw, FIELD_TTN_ALTITUDE, Double.class)))
                            .ifPresent(gwInfo::setLocation);
                        data.addGatewayInfo(gwInfo);
                    });
            });

        return data;
    }
}
