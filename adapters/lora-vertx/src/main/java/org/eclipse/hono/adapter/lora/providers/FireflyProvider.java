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

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Firefly.
 */
@Component
public class FireflyProvider implements LoraProvider {

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
    private static final String FIELD_FIREFLY_FREQUENCY ="freq";
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
    public String extractDeviceId(final JsonObject loraMessage) {
        return loraMessage.getJsonObject(FIELD_FIREFLY_DEVICE, new JsonObject())
                .getString(FIELD_FIREFLY_DEVICE_EUI);
    }

    @Override
    public String extractPayload(final JsonObject loraMessage) {
        return loraMessage.getString(FIELD_FIREFLY_PAYLOAD);
    }

    @Override
    public LoraMessageType extractMessageType(final JsonObject loraMessage) {
        if (FIELD_FIREFLY_MESSAGE_TYPE_UPLINK.equals(loraMessage.getJsonObject(FIELD_FIREFLY_SERVER_DATA, new JsonObject())
                .getString(FIELD_FIREFLY_MESSAGE_TYPE))) {
            return LoraMessageType.UPLINK;
        }
        return LoraMessageType.UNKNOWN;
    }

    @Override
    public Map<String, Object> extractNormalizedData(final JsonObject loraMessage) {
        final Map<String, Object> returnMap = new HashMap<>();
        if (loraMessage.containsKey(FIELD_FIREFLY_SPREADING_FACTOR)) {
            returnMap.put(LoraConstants.APP_PROPERTY_SPREADING_FACTOR, loraMessage.getInteger(FIELD_FIREFLY_SPREADING_FACTOR));
        }
        if (loraMessage.containsKey(FIELD_FIREFLY_BANDWIDTH)) {
            returnMap.put(LoraConstants.APP_PROPERTY_BANDWIDTH, loraMessage.getInteger(FIELD_FIREFLY_BANDWIDTH));
        }
        if (loraMessage.containsKey(FIELD_FIREFLY_FUNCTION_PORT)) {
            returnMap.put(LoraConstants.APP_PROPERTY_FUNCTION_PORT, loraMessage.getInteger(FIELD_FIREFLY_FUNCTION_PORT));
        }
        if (loraMessage.containsKey(FIELD_FIREFLY_MIC_PASS)) {
            returnMap.put(LoraConstants.APP_PROPERTY_MIC, loraMessage.getBoolean(FIELD_FIREFLY_MIC_PASS));
        }
        final JsonObject dataRateJson = loraMessage.getJsonObject(FIELD_FIREFLY_SERVER_DATA, new JsonObject());

        if (dataRateJson.containsKey(FIELD_FIREFLY_DATA_RATE)) {
            returnMap.put(LoraConstants.DATA_RATE, dataRateJson.getString(FIELD_FIREFLY_DATA_RATE));
        }
        if (dataRateJson.containsKey(FIELD_FIREFLY_CODING_RATE)) {
            returnMap.put(LoraConstants.CODING_RATE, dataRateJson.getString(FIELD_FIREFLY_CODING_RATE));
        }
        if (dataRateJson.containsKey(FIELD_FIREFLY_FREQUENCY)) {
            returnMap.put(LoraConstants.FREQUENCY, dataRateJson.getDouble(FIELD_FIREFLY_FREQUENCY));
        }

        final JsonObject parsedPacketJson = loraMessage.getJsonObject(FIELD_FIREFLY_PARSED_PACKET, new JsonObject());
        if (parsedPacketJson.containsKey(FIELD_FIREFLY_FRAME_COUNT)) {
            returnMap.put(LoraConstants.FRAME_COUNT, parsedPacketJson.getInteger(FIELD_FIREFLY_FRAME_COUNT));
        }

        if (dataRateJson.containsKey(FIELD_FIREFLY_GATEWAY_RX)) {
            final JsonArray gwRxs = dataRateJson.getJsonArray(FIELD_FIREFLY_GATEWAY_RX);
            final JsonArray normalizedGatways = new JsonArray();
            for (int i = 0; i < gwRxs.size(); i++) {
                final JsonObject gwRx = gwRxs.getJsonObject(i);
                final JsonObject normalizedGatway = new JsonObject();
                if (gwRx.containsKey(FIELD_FIREFLY_GATEWAY_EUI)) {
                    normalizedGatway.put(LoraConstants.GATEWAY_ID, gwRx.getString(FIELD_FIREFLY_GATEWAY_EUI));
                }
                if (gwRx.containsKey(FIELD_FIREFLY_RSSI)) {
                    normalizedGatway.put(LoraConstants.APP_PROPERTY_RSS, gwRx.getInteger(FIELD_FIREFLY_RSSI));
                }
                if (gwRx.containsKey(FIELD_FIREFLY_LSNR)) {
                    normalizedGatway.put(LoraConstants.APP_PROPERTY_SNR, gwRx.getDouble(FIELD_FIREFLY_LSNR));
                }
                normalizedGatways.add(normalizedGatway);
            }
            returnMap.put(LoraConstants.GATEWAYS, normalizedGatways.toString());
        }

        return returnMap;
    }

    @Override
    public JsonObject extractAdditionalData(final JsonObject loraMessage) {
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
