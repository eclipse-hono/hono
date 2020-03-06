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
 * A LoRaWAN provider with API for Loriot.
 */
@Component
public class LoriotProvider implements LoraProvider {
    private static final String FIELD_LORIOT_EUI = "EUI";
    private static final String FIELD_LORIOT_PAYLOAD = "data";

    private static final String FIELD_LORIOT_MESSAGE_TYPE_UPLINK = "gw";
    private static final String FIELD_LORIOT_MESSAGE_TYPE = "cmd";
    private static final String FIELD_LORIOT_DATARATE = "dr";
    private static final String FIELD_LORIOT_FUNCTION_PORT = "port";
    private static final String FIELD_LORIOT_FRAME_COUNT = "fcnt";
    private static final String FIELD_LORIOT_FREQUENCY ="freq";
    private static final String FIELD_LORIOT_GATEWAYS = "gws";
    private static final String FIELD_LORIOT_GATEWAY_EUI = "gweui";
    private static final String FIELD_LORIOT_RSSI = "rssi";
    private static final String FIELD_LORIOT_SNR = "snr";

    @Override
    public String getProviderName() {
        return "loriot";
    }

    @Override
    public String pathPrefix() {
        return "/loriot";
    }

    @Override
    public String extractDeviceId(final JsonObject loraMessage) {
        return loraMessage.getString(FIELD_LORIOT_EUI);
    }

    @Override
    public String extractPayload(final JsonObject loraMessage) {
        return loraMessage.getString(FIELD_LORIOT_PAYLOAD);
    }

    @Override
    public LoraMessageType extractMessageType(final JsonObject loraMessage) {
        if (FIELD_LORIOT_MESSAGE_TYPE_UPLINK.equals(loraMessage.getString(FIELD_LORIOT_MESSAGE_TYPE))) {
            return LoraMessageType.UPLINK;
        }
        return LoraMessageType.UNKNOWN;
    }

    @Override
    public Map<String, Object> extractNormalizedData(final JsonObject loraMessage) {
        final Map<String, Object> returnMap = new HashMap<>();
        if (loraMessage.containsKey(FIELD_LORIOT_DATARATE)) {
            final String dataRate = loraMessage.getString(FIELD_LORIOT_DATARATE);
            returnMap.put(LoraConstants.DATA_RATE, dataRate);
            final String[] datarateSplit = dataRate.split(" ");
            returnMap.put(LoraConstants.APP_PROPERTY_SPREADING_FACTOR, Integer.parseInt(datarateSplit[0].substring(2)));
            returnMap.put(LoraConstants.APP_PROPERTY_BANDWIDTH, Integer.parseInt(datarateSplit[1].substring(2)));
            returnMap.put(LoraConstants.CODING_RATE, datarateSplit[2]);
        }
        if (loraMessage.containsKey(FIELD_LORIOT_FUNCTION_PORT)) {
            returnMap.put(LoraConstants.APP_PROPERTY_FUNCTION_PORT, loraMessage.getInteger(FIELD_LORIOT_FUNCTION_PORT));
        }

        if (loraMessage.containsKey(FIELD_LORIOT_FRAME_COUNT)) {
            returnMap.put(LoraConstants.FRAME_COUNT, loraMessage.getInteger(FIELD_LORIOT_FRAME_COUNT));
        }

        if (loraMessage.containsKey(FIELD_LORIOT_FREQUENCY)) {
            returnMap.put(LoraConstants.FREQUENCY, loraMessage.getDouble(FIELD_LORIOT_FREQUENCY));
        }

        if (loraMessage.containsKey(FIELD_LORIOT_GATEWAYS)) {
            final JsonArray gws = loraMessage.getJsonArray(FIELD_LORIOT_GATEWAYS);
            final JsonArray normalizedGatways = new JsonArray();
            for (int i = 0; i < gws.size(); i++) {
                final JsonObject gw = gws.getJsonObject(i);
                final JsonObject normalizedGatway = new JsonObject();
                if (gw.containsKey(FIELD_LORIOT_GATEWAY_EUI)) {
                    normalizedGatway.put(LoraConstants.GATEWAY_ID, gw.getString(FIELD_LORIOT_GATEWAY_EUI));
                }
                if (gw.containsKey(FIELD_LORIOT_RSSI)) {
                    normalizedGatway.put(LoraConstants.APP_PROPERTY_RSS, gw.getInteger(FIELD_LORIOT_RSSI));
                }
                if (gw.containsKey(FIELD_LORIOT_SNR)) {
                    normalizedGatway.put(LoraConstants.APP_PROPERTY_SNR, gw.getDouble(FIELD_LORIOT_SNR));
                }
                normalizedGatways.add(normalizedGatway);
            }
            returnMap.put(LoraConstants.GATEWAYS, normalizedGatways.toString());
        }

        return returnMap;
    }

    @Override
    public JsonObject extractAdditionalData(final JsonObject loraMessage) {
        return null;
    }
}
