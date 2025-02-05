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

import org.eclipse.hono.adapter.lora.GatewayInfo;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;

import com.google.common.io.BaseEncoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A LoRaWAN provider with API for Kerlink.
 * This provider supports messages as described by the
 * <a href="https://wmc-poc.wanesy.com/gms/application/doc#PushDataUpDto">Kerlink API</a>.
 */
@ApplicationScoped
public class KerlinkProvider extends JsonBasedLoraProvider {

    private static final String ENCODING_TYPE_HEX = "HEXA";
    private static final String ENCODING_TYPE_BASE64 = "BASE64";

    private static final String FIELD_KERLINK_ADR = "adr";
    private static final String FIELD_KERLINK_CHANNEL = "channel";
    private static final String FIELD_KERLINK_CODING_RATE = "codingRate";
    private static final String FIELD_KERLINK_DATA_RATE = "dataRate";
    private static final String FIELD_KERLINK_DEVICE_EUI = "devEui";
    private static final String FIELD_KERLINK_ENCODING_TYPE = "encodingType";
    private static final String FIELD_KERLINK_END_DEVICE = "endDevice";
    private static final String FIELD_KERLINK_FRAME_COUNT = "fCntUp";
    private static final String FIELD_KERLINK_FREQUENCY = "ulFrequency";
    private static final String FIELD_KERLINK_FUNCTION_PORT = "fPort";
    private static final String FIELD_KERLINK_GATEWAY_EUI = "gwEui";
    private static final String FIELD_KERLINK_GW_INFO = "gwInfo";
    private static final String FIELD_KERLINK_LATITUDE = "latitude";
    private static final String FIELD_KERLINK_LONGITUDE = "longitude";
    private static final String FIELD_KERLINK_PAYLOAD = "payload";
    private static final String FIELD_KERLINK_RSSI = "rssi";
    private static final String FIELD_KERLINK_SNR = "snr";

    private static final String KERLINK_BANDWIDTH = "BW";
    private static final String KERLINK_SPREADING_FACTOR = "SF";

    @Override
    public String getProviderName() {
        return "kerlink";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/kerlink/dataUp");
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method always returns {@link LoraMessageType#UPLINK}.
     */
    @Override
    public LoraMessageType getMessageType(final JsonObject loraMessage) {
        if (loraMessage.containsKey(FIELD_KERLINK_PAYLOAD)) {
            return LoraMessageType.UPLINK;
        }
        return LoraMessageType.UNKNOWN;
    }

    @Override
    protected byte[] getDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_KERLINK_END_DEVICE, JsonObject.class)
            .map(endDevice -> endDevice.getValue(FIELD_KERLINK_DEVICE_EUI))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(LoraUtils::convertFromHexToBytes)
            .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        final String encodingType = LoraUtils.getChildObject(loraMessage, FIELD_KERLINK_ENCODING_TYPE, String.class).orElse("");
        final Optional<String> payload = LoraUtils.getChildObject(loraMessage, FIELD_KERLINK_PAYLOAD, String.class);

        switch (encodingType) {
            case ENCODING_TYPE_HEX:
                return payload
                    .map(s -> Buffer.buffer(BaseEncoding.base16().decode(s.toUpperCase())))
                    .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain HEX encoded payload property"));
            default:
            case ENCODING_TYPE_BASE64:
                return payload
                    .map(s -> Buffer.buffer(Base64.getDecoder().decode(s)))
                    .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain BASE64 encoded payload property"));
        }
    }

    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        final LoraMetaData data = new LoraMetaData();

        LoraUtils.getChildObject(loraMessage, FIELD_KERLINK_DATA_RATE, String.class)
            .ifPresent(datr -> {
                final String[] dataRateParts = datr.split(KERLINK_BANDWIDTH, 2);
                final String spreadingFactor = dataRateParts[0].replace(KERLINK_SPREADING_FACTOR, "");
                final String bandWith = dataRateParts[1];
                data.setSpreadingFactor(Integer.parseInt(spreadingFactor));
                data.setBandwidth(Integer.parseInt(bandWith));
            });
        LoraUtils.getChildObject(loraMessage, FIELD_KERLINK_FUNCTION_PORT, Integer.class)
            .ifPresent(data::setFunctionPort);
        LoraUtils.getChildObject(loraMessage, FIELD_KERLINK_FRAME_COUNT, Integer.class)
            .ifPresent(data::setFrameCount);

        LoraUtils.getChildObject(loraMessage, FIELD_KERLINK_ADR, Boolean.class)
            .ifPresent(data::setAdaptiveDataRateEnabled);

        LoraUtils.getChildObject(loraMessage, FIELD_KERLINK_FREQUENCY, Double.class)
            .ifPresent(data::setFrequency);
        LoraUtils.getChildObject(loraMessage, FIELD_KERLINK_CODING_RATE, String.class)
            .ifPresent(data::setCodingRate);

        LoraUtils.getChildObject(loraMessage, FIELD_KERLINK_END_DEVICE, JsonObject.class)
            .ifPresent(endDevice -> {
                Optional.ofNullable(LoraUtils.newLocation(
                        LoraUtils.getChildObject(endDevice, FIELD_KERLINK_LONGITUDE, Double.class),
                        LoraUtils.getChildObject(endDevice, FIELD_KERLINK_LATITUDE, Double.class),
                        Optional.empty()))
                    .ifPresent(data::setLocation);
            });

        LoraUtils.getChildObject(loraMessage, FIELD_KERLINK_GW_INFO, JsonArray.class)
            .ifPresent(gws -> {
                gws.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .forEach(gw -> {
                        final GatewayInfo gwInfo = new GatewayInfo();
                        LoraUtils.getChildObject(gw, FIELD_KERLINK_GATEWAY_EUI, String.class)
                            .ifPresent(gwInfo::setGatewayId);
                        LoraUtils.getChildObject(gw, FIELD_KERLINK_RSSI, Integer.class)
                            .ifPresent(gwInfo::setRssi);
                        LoraUtils.getChildObject(gw, FIELD_KERLINK_SNR, Double.class)
                            .ifPresent(gwInfo::setSnr);
                        LoraUtils.getChildObject(gw, FIELD_KERLINK_CHANNEL, Integer.class)
                            .ifPresent(gwInfo::setChannel);
                        Optional.ofNullable(LoraUtils.newLocation(
                                LoraUtils.getChildObject(gw, FIELD_KERLINK_LONGITUDE, Double.class),
                                LoraUtils.getChildObject(gw, FIELD_KERLINK_LATITUDE, Double.class),
                                Optional.empty()))
                            .ifPresent(gwInfo::setLocation);
                        data.addGatewayInfo(gwInfo);
                    });
            });

        return data;
    }
}
