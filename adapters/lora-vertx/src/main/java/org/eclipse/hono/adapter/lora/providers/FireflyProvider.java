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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.springframework.stereotype.Component;

import com.google.common.io.BaseEncoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Firefly.
 */
@Component
public class FireflyProvider extends BaseLoraProvider {

    private static final String FIELD_FIREFLY_SPREADING_FACTOR = "spreading_factor";
    private static final String FIELD_FIREFLY_FUNCTION_PORT = "port";
    private static final String FIELD_FIREFLY_PAYLOAD = "payload";
    private static final String FIELD_FIREFLY_SERVER_DATA = "server_data";
    private static final String FIELD_FIREFLY_MESSAGE_TYPE = "mtype";
    private static final String FIELD_FIREFLY_MESSAGE_TYPE_UPLINK = "confirmed_data_up";

    private static final String FIELD_FIREFLY_DEVICE = "device";
    private static final String FIELD_FIREFLY_DEVICE_EUI = "eui";
    private static final String FIELD_FIREFLY_GATEWAY_RX = "gwrx";
    private static final String FIELD_FIREFLY_GATEWAY_EUI = "gweui";
    private static final String FIELD_FIREFLY_RSSI = "rssi";
    private static final String FIELD_FIREFLY_LSNR = "lsnr";
    private static final String FIELD_FIREFLY_DATA_RATE = "datr";
    private static final String FIELD_FIREFLY_CODING_RATE = "codr";
    private static final String FIELD_FIREFLY_FREQUENCY = "freq";
    private static final String FIELD_FIREFLY_PARSED_PACKET = "parsed_packet";
    private static final String FIELD_FIREFLY_FRAME_COUNT = "fcnt";
    private static final String FIELD_FIREFLY_BANDWIDTH = "bandwidth";
    private static final String FIELD_FIREFLY_MIC_PASS = "mic_pass";

    @Override
    public String getProviderName() {
        return "firefly";
    }

    @Override
    public String pathPrefix() {
        return "/firefly";
    }

    @Override
    protected String extractDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_FIREFLY_DEVICE, JsonObject.class)
                .map(device -> device.getValue(FIELD_FIREFLY_DEVICE_EUI))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer extractPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_FIREFLY_PAYLOAD, String.class)
                .map(s -> Buffer.buffer(BaseEncoding.base16().decode(s.toUpperCase())))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued payload property"));
    }

    @Override
    protected LoraMessageType extractMessageType(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_FIREFLY_SERVER_DATA, JsonObject.class)
                .map(serverData -> serverData.getValue(FIELD_FIREFLY_MESSAGE_TYPE))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(type -> FIELD_FIREFLY_MESSAGE_TYPE_UPLINK.equals(type) ? LoraMessageType.UPLINK : LoraMessageType.UNKNOWN)
                .orElse(LoraMessageType.UNKNOWN);
    }

    @Override
    protected Map<String, Object> extractNormalizedData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        final Map<String, Object> data = new HashMap<>();

        LoraUtils.addNormalizedValue(
                loraMessage,
                FIELD_FIREFLY_SPREADING_FACTOR,
                Integer.class,
                LoraConstants.APP_PROPERTY_SPREADING_FACTOR,
                v -> v,
                data);
        LoraUtils.addNormalizedValue(
                loraMessage,
                FIELD_FIREFLY_BANDWIDTH,
                Integer.class,
                LoraConstants.APP_PROPERTY_BANDWIDTH,
                v -> v,
                data);
        LoraUtils.addNormalizedValue(
                loraMessage,
                FIELD_FIREFLY_FUNCTION_PORT,
                Integer.class,
                LoraConstants.APP_PROPERTY_FUNCTION_PORT,
                v -> v,
                data);
        LoraUtils.addNormalizedValue(
                loraMessage,
                FIELD_FIREFLY_MIC_PASS,
                Boolean.class,
                LoraConstants.APP_PROPERTY_MIC,
                v -> v,
                data);

        LoraUtils.getChildObject(loraMessage, FIELD_FIREFLY_PARSED_PACKET, JsonObject.class)
            .map(parsedPacket -> parsedPacket.getValue(FIELD_FIREFLY_FRAME_COUNT))
            .filter(Integer.class::isInstance)
            .map(Integer.class::cast)
            .ifPresent(v -> data.put(LoraConstants.FRAME_COUNT, v));


        LoraUtils.getChildObject(loraMessage, FIELD_FIREFLY_SERVER_DATA, JsonObject.class)
            .map(serverData -> {

                LoraUtils.addNormalizedValue(
                        serverData,
                        FIELD_FIREFLY_DATA_RATE,
                        String.class,
                        LoraConstants.DATA_RATE,
                        v -> v,
                        data);
                LoraUtils.addNormalizedValue(
                        serverData,
                        FIELD_FIREFLY_CODING_RATE,
                        String.class,
                        LoraConstants.CODING_RATE,
                        v -> v,
                        data);
                LoraUtils.addNormalizedValue(
                        serverData,
                        FIELD_FIREFLY_FREQUENCY,
                        Double.class,
                        LoraConstants.FREQUENCY,
                        v -> v,
                        data);

                return serverData.getValue(FIELD_FIREFLY_GATEWAY_RX);
            })
            .filter(JsonArray.class::isInstance)
            .map(JsonArray.class::cast)
            .ifPresent(gwRxs -> {
                final JsonArray normalizedGatways = gwRxs.stream()
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast)
                        .map(gwRx -> {
                            final JsonObject normalizedGatway = new JsonObject();
                            LoraUtils.getChildObject(gwRx, FIELD_FIREFLY_GATEWAY_EUI, String.class)
                                .ifPresent(v -> normalizedGatway.put(LoraConstants.GATEWAY_ID, v));
                            LoraUtils.getChildObject(gwRx, FIELD_FIREFLY_RSSI, Integer.class)
                                .ifPresent(v -> normalizedGatway.put(LoraConstants.APP_PROPERTY_RSS, v));
                            LoraUtils.getChildObject(gwRx, FIELD_FIREFLY_LSNR, Double.class)
                                .ifPresent(v -> normalizedGatway.put(LoraConstants.APP_PROPERTY_SNR, v));
                            return normalizedGatway;
                        })
                        .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
                data.put(LoraConstants.GATEWAYS, normalizedGatways.toString());
            });

        return data;
    }

    @Override
    protected JsonObject extractAdditionalData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        final JsonObject returnMessage = loraMessage.copy();
        if (returnMessage.containsKey(FIELD_FIREFLY_SPREADING_FACTOR)) {
            returnMessage.remove(LoraConstants.APP_PROPERTY_SPREADING_FACTOR);
        }
        if (returnMessage.containsKey(FIELD_FIREFLY_FUNCTION_PORT)) {
            returnMessage.remove(LoraConstants.APP_PROPERTY_FUNCTION_PORT);
        }
        return null;
    }
}
