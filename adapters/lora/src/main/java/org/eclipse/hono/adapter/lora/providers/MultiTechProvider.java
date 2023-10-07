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
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.hono.adapter.lora.GatewayInfo;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;
import org.eclipse.hono.util.MessageHelper;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A LoRaWAN provider with API for MultiTechProvider.
 * <p>
 * This provider supports uplink messages only from the embedded LNS on MultiTech gateways.
 */
@ApplicationScoped
public class MultiTechProvider extends JsonBasedLoraProvider {

    private static final String FIELD_MULTITECH_ADR = "adr";
    private static final String FIELD_MULTITECH_CHANNEL = "chan";
    private static final String FIELD_MULTITECH_CODE_RATE = "codr";
    private static final String FIELD_MULTITECH_DATA_RATE = "datr";
    private static final String FIELD_MULTITECH_DEVICE = "deveui";
    private static final String FIELD_MULTITECH_FRAME_COUNT = "fcnt";
    private static final String FIELD_MULTITECH_FREQUENCY = "freq";
    private static final String FIELD_MULTITECH_FUNCTION_PORT = "port";
    private static final String FIELD_MULTITECH_GATEWAY_ID = "gweui";
    private static final String FIELD_MULTITECH_LSNR = "lsnr";
    private static final String FIELD_MULTITECH_PAYLOAD = "data";
    private static final String FIELD_MULTITECH_RSSI = "rssi";

    private static final String MULTITECH_BANDWIDTH = "BW";
    private static final String MULTITECH_SPREADING_FACTOR = "SF";

    @Override
    public String getProviderName() {
        return "multiTech";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/multitech");
    }

    @Override
    protected byte[] getDevEui(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_MULTITECH_DEVICE, String.class)
                .map(s -> multiTechEuiToHex(s))
                .map(LoraUtils::convertFromHexToBytes)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain device ID property"));
    }

    private String multiTechEuiToHex(final String s) {
        return s.replace("-", "");
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_MULTITECH_PAYLOAD, String.class)
                .map(s -> Buffer.buffer(Base64.getDecoder().decode(s)))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain Base64 encoded payload property"));
    }

    @Override
    protected LoraMessageType getMessageType(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        if (loraMessage.containsKey(FIELD_MULTITECH_PAYLOAD)) {
            return LoraMessageType.UPLINK;
        } else {
            return LoraMessageType.UNKNOWN;
        }
    }

    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        final LoraMetaData data = new LoraMetaData();

        LoraUtils.getChildObject(loraMessage, FIELD_MULTITECH_FUNCTION_PORT, Integer.class)
            .ifPresent(data::setFunctionPort);
        LoraUtils.getChildObject(loraMessage, FIELD_MULTITECH_FRAME_COUNT, Integer.class)
            .ifPresent(data::setFrameCount);
        LoraUtils.getChildObject(loraMessage, FIELD_MULTITECH_ADR, Boolean.class)
            .ifPresent(data::setAdaptiveDataRateEnabled);

        LoraUtils.getChildObject(loraMessage, FIELD_MULTITECH_FREQUENCY, Double.class)
            .ifPresent(v -> data.setFrequency(v));

        LoraUtils.getChildObject(loraMessage, FIELD_MULTITECH_DATA_RATE, String.class)
            .ifPresent(datr -> {
                final String[] dataRateParts = datr.split(MULTITECH_BANDWIDTH, 2);
                final String spreadingFactor = dataRateParts[0].replace(MULTITECH_SPREADING_FACTOR, "");
                final String bandWith = dataRateParts[1];
                data.setSpreadingFactor(Integer.parseInt(spreadingFactor));
                data.setBandwidth(Integer.parseInt(bandWith));
            });
        LoraUtils.getChildObject(loraMessage, FIELD_MULTITECH_CODE_RATE, String.class)
            .ifPresent(data::setCodingRate);

        final GatewayInfo gateway = new GatewayInfo();
        LoraUtils.getChildObject(loraMessage, FIELD_MULTITECH_GATEWAY_ID, String.class)
            .ifPresent(v -> gateway.setGatewayId(multiTechEuiToHex(v)));
        LoraUtils.getChildObject(loraMessage, FIELD_MULTITECH_RSSI, Integer.class)
            .ifPresent(gateway::setRssi);
        LoraUtils.getChildObject(loraMessage, FIELD_MULTITECH_LSNR, Double.class)
            .ifPresent(gateway::setSnr);
        LoraUtils.getChildObject(loraMessage, FIELD_MULTITECH_CHANNEL, Integer.class)
            .ifPresent(gateway::setChannel);

        data.addGatewayInfo(gateway);

        return data;
    }

    @Override
    public Map<String, String> getDefaultHeaders() {
        return Map.of(
            HttpHeaders.CONTENT_TYPE.toString(), MessageHelper.CONTENT_TYPE_APPLICATION_JSON,
            HttpHeaders.ACCEPT.toString(), MessageHelper.CONTENT_TYPE_APPLICATION_JSON
        );
    }
}
