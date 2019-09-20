/*******************************************************************************
 * Copyright (c) 2019, 2019 Contributors to the Eclipse Foundation
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

import io.vertx.core.json.JsonArray;
import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * A LoRaWAN provider with API for Actility.
 */
@Component
public class ActilityProvider implements LoraProvider {

    private static final String FIELD_ACTILITY_ROOT_OBJECT = "DevEUI_uplink";
    private static final String FIELD_ACTILITY_DEVICE_EUI = "DevEUI";
    private static final String FIELD_ACTILITY_PAYLOAD = "payload_hex";
    private static final String FIELD_ACTILITY_LRR_RSSI = "LrrRSSI";
    private static final String FIELD_ACTILITY_TX_POWER = "TxPower";
    private static final String FIELD_ACTILITY_CHANNEL = "Channel";
    private static final String FIELD_ACTILITY_SUB_BAND = "SubBand";
    private static final String FIELD_ACTILITY_SPREADING_FACTOR = "SpFact";
    private static final String FIELD_ACTILITY_LRR_SNR = "LrrSNR";
    private static final String FIELD_ACTILITY_FPORT = "FPort";
    private static final String FIELD_ACTILITY_LATITUTDE = "LrrLAT";
    private static final String FIELD_ACTILITY_LONGITUDE = "LrrLON";
    private static final String FIELD_ACTILITY_LRRS = "Lrrs";
    private static final String FIELD_ACTILITY_LRR = "Lrr";
    private static final String FIELD_ACTILITY_LRR_ID = "Lrrid";

    @Override
    public String getProviderName() {
        return "actility";
    }

    @Override
    public String pathPrefix() {
        return "/actility";
    }

    @Override
    public String extractDeviceId(final JsonObject loraMessage) {
        return loraMessage.getJsonObject(FIELD_ACTILITY_ROOT_OBJECT, new JsonObject())
                .getString(FIELD_ACTILITY_DEVICE_EUI);
    }

    @Override
    public String extractPayload(final JsonObject loraMessage) {
        return loraMessage.getJsonObject(FIELD_ACTILITY_ROOT_OBJECT, new JsonObject())
                .getString(FIELD_ACTILITY_PAYLOAD);
    }

    @Override
    public LoraMessageType extractMessageType(final JsonObject loraMessage) {
        final String[] messageKeys = loraMessage.getMap().keySet().toArray(new String[0]);
        if (messageKeys.length > 0 && FIELD_ACTILITY_ROOT_OBJECT.equals(messageKeys[0])) {
            return LoraMessageType.UPLINK;
        }
        return LoraMessageType.UNKNOWN;
    }

    @Override
    public Map<String, Object> extractNormalizedData(final JsonObject loraMessage) {
        final Map<String, Object> returnMap = new HashMap<>();
        final JsonObject rootObject = loraMessage.getJsonObject(FIELD_ACTILITY_ROOT_OBJECT, new JsonObject());
        if (rootObject.containsKey(FIELD_ACTILITY_LRR_RSSI)) {
            returnMap.put(LoraConstants.APP_PROPERTY_RSS,
                    Math.abs(Double.valueOf(rootObject.getString(FIELD_ACTILITY_LRR_RSSI))));
        }
        if (rootObject.containsKey(FIELD_ACTILITY_TX_POWER)) {
            returnMap.put(LoraConstants.APP_PROPERTY_TX_POWER,
                    rootObject.getDouble(FIELD_ACTILITY_TX_POWER));
        }
        if (rootObject.containsKey(FIELD_ACTILITY_CHANNEL)) {
            returnMap.put(LoraConstants.APP_PROPERTY_CHANNEL, rootObject.getString(FIELD_ACTILITY_CHANNEL));
        }
        if (rootObject.containsKey(FIELD_ACTILITY_SUB_BAND)) {
            returnMap.put(LoraConstants.APP_PROPERTY_SUB_BAND, rootObject.getString(FIELD_ACTILITY_SUB_BAND));
        }
        if (rootObject.containsKey(FIELD_ACTILITY_SPREADING_FACTOR)) {
            returnMap.put(LoraConstants.APP_PROPERTY_SPREADING_FACTOR,
                    Integer.valueOf(rootObject.getString(FIELD_ACTILITY_SPREADING_FACTOR)));
        }
        if (rootObject.containsKey(FIELD_ACTILITY_LRR_SNR)) {
            returnMap.put(LoraConstants.APP_PROPERTY_SNR,
                    Math.abs(Double.valueOf(rootObject.getString(FIELD_ACTILITY_LRR_SNR))));
        }
        if (rootObject.containsKey(FIELD_ACTILITY_FPORT)) {
            returnMap.put(LoraConstants.APP_PROPERTY_FUNCTION_PORT,
                    Integer.valueOf(rootObject.getString(FIELD_ACTILITY_FPORT)));
        }
        if (rootObject.containsKey(FIELD_ACTILITY_LATITUTDE)) {
            returnMap.put(LoraConstants.APP_PROPERTY_FUNCTION_LATITUDE,
                    Double.valueOf(rootObject.getString(FIELD_ACTILITY_LATITUTDE)));
        }
        if (rootObject.containsKey(FIELD_ACTILITY_LONGITUDE)) {
            returnMap.put(LoraConstants.APP_PROPERTY_FUNCTION_LONGITUDE,
                    Double.valueOf(rootObject.getString(FIELD_ACTILITY_LONGITUDE)));
        }

        if (rootObject.containsKey(FIELD_ACTILITY_LRRS) && rootObject.getJsonObject(FIELD_ACTILITY_LRRS).containsKey(FIELD_ACTILITY_LRR)) {
            final JsonArray lrrs = rootObject.getJsonObject(FIELD_ACTILITY_LRRS).getJsonArray(FIELD_ACTILITY_LRR);
            final JsonArray normalizedGatways = new JsonArray();
            for (int i = 0; i < lrrs.size(); i++) {
                final JsonObject lrr = lrrs.getJsonObject(i);
                final JsonObject normalizedGatway = new JsonObject();
                if (lrr.containsKey(FIELD_ACTILITY_LRR_ID)) {
                    normalizedGatway.put(LoraConstants.GATEWAY_ID, lrr.getString(FIELD_ACTILITY_LRR_ID));
                }
                if (lrr.containsKey(FIELD_ACTILITY_LRR_RSSI)) {
                    normalizedGatway.put(LoraConstants.APP_PROPERTY_RSS,
                            Math.abs(Double.valueOf(lrr.getString(FIELD_ACTILITY_LRR_RSSI))));
                }
                if (lrr.containsKey(FIELD_ACTILITY_LRR_SNR)) {
                    normalizedGatway.put(LoraConstants.APP_PROPERTY_SNR,
                            Math.abs(Double.valueOf(lrr.getString(FIELD_ACTILITY_LRR_SNR))));
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
        if (returnMessage.containsKey(FIELD_ACTILITY_LRR_RSSI)) {
            returnMessage.remove(LoraConstants.APP_PROPERTY_RSS);
        }
        if (returnMessage.containsKey(FIELD_ACTILITY_TX_POWER)) {
            returnMessage.remove(LoraConstants.APP_PROPERTY_TX_POWER);
        }
        if (returnMessage.containsKey(FIELD_ACTILITY_CHANNEL)) {
            returnMessage.remove(LoraConstants.APP_PROPERTY_CHANNEL);
        }
        if (returnMessage.containsKey(FIELD_ACTILITY_SUB_BAND)) {
            returnMessage.remove(LoraConstants.APP_PROPERTY_SUB_BAND);
        }
        if (returnMessage.containsKey(FIELD_ACTILITY_SPREADING_FACTOR)) {
            returnMessage.remove(LoraConstants.APP_PROPERTY_SPREADING_FACTOR);
        }
        if (returnMessage.containsKey(FIELD_ACTILITY_LRR_SNR)) {
            returnMessage.remove(LoraConstants.APP_PROPERTY_SNR);
        }
        if (returnMessage.containsKey(FIELD_ACTILITY_FPORT)) {
            returnMessage.remove(LoraConstants.APP_PROPERTY_FUNCTION_PORT);
        }
        if (returnMessage.containsKey(FIELD_ACTILITY_LATITUTDE)) {
            returnMessage.remove(LoraConstants.APP_PROPERTY_FUNCTION_LATITUDE);
        }
        if (returnMessage.containsKey(FIELD_ACTILITY_LONGITUDE)) {
            returnMessage.remove(LoraConstants.APP_PROPERTY_FUNCTION_LONGITUDE);
        }
        return null;
    }
}
