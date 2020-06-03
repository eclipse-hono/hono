/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
 * A LoRaWAN provider with API for ChirpStack.
 */
@Component
public class ChirpStackProvider implements LoraProvider {

    private static final String FIELD_CHIRPSTACK_PAYLOAD = "data";
    private static final String FIELD_CHIRPSTACK_DEVICE = "devEUI";
    private static final String FIELD_CHIRPSTACK_TX_INFO = "txInfo";
    private static final String FIELD_CHIRPSTACK_SPREADING_FACTOR = "spreadingFactor";
    private static final String FIELD_CHIRPSTACK_BANDWIDTH = "bandwidth";
    private static final String FIELD_CHIRPSTACK_FUNCTION_PORT = "fPort";
    private static final String FIELD_CHIRPSTACK_CODE_RATE = "codeRate";
    private static final String FIELD_CHIRPSTACK_LORA_MODULATION_INFO = "loRaModulationInfo";
    private static final String FIELD_CHIRPSTACK_FREQUENCY = "frequency";
    private static final String FIELD_CHIRPSTACK_FRAME_COUNT = "fCnt";
    private static final String FIELD_CHIRPSTACK_RX_INFO = "rxInfo";
    private static final String FIELD_CHIRPSTACK_GATEWAY_ID = "gatewayID";
    private static final String FIELD_CHIRPSTACK_RSSI = "rssi";
    private static final String FIELD_CHIRPSTACK_LSNR = "loRaSNR";
    private static final String FIELD_CHIRPSTACK_CHANNEL = "channel";
    private static final String FIELD_CHIRPSTACK_LOCATION = "location";
    private static final String FIELD_CHIRPSTACK_LATITUDE = "latitude";
    private static final String FIELD_CHIRPSTACK_LONGITUDE = "longitude";

    @Override
    public String getProviderName() {
        return "chirpStack";
    }

    @Override
    public String pathPrefix() {
        return "/chirpstack";
    }

    @Override
    public String extractDeviceId(final JsonObject loraMessage) {
        final Object deviceId = loraMessage.getValue(FIELD_CHIRPSTACK_DEVICE);
        if (deviceId == null) {
            throw new LoraProviderMalformedPayloadException("DeviceId could not be extracted from message.", null);
        }
        if (deviceId instanceof String) {
            return LoraUtils.convertFromBase64ToHex((String) deviceId);
        }

        throw new LoraProviderMalformedPayloadException("DeviceId could not be extracted from message. Expected " +
                "string, got " + deviceId.getClass().getName(), null);
    }

    @Override
    public String extractPayload(final JsonObject loraMessage) {
        final Object payload = loraMessage.getValue(FIELD_CHIRPSTACK_PAYLOAD);
        if (payload == null) {
            throw new LoraProviderMalformedPayloadException("Payload could not be extracted from message.", null);
        }

        if (payload instanceof String) {
            return LoraUtils.convertFromBase64ToHex((String) payload);
        }

        throw new LoraProviderMalformedPayloadException("Payload could not be extracted from message. Expected " +
                "string, got " + payload.getClass().getName(), null);
    }

    @Override
    public LoraMessageType extractMessageType(final JsonObject loraMessage) {
        if (loraMessage.containsKey(FIELD_CHIRPSTACK_PAYLOAD)) {
            return LoraMessageType.UPLINK;
        }
        return LoraMessageType.UNKNOWN;
    }

    @Override
    public Map<String, Object> extractNormalizedData(final JsonObject loraMessage) {
        final Map<String, Object> returnMap = new HashMap<>();
        final JsonObject txInfo = loraMessage.getJsonObject(FIELD_CHIRPSTACK_TX_INFO);
        if (txInfo != null) {
            final JsonObject loraModulationInfo = txInfo.getJsonObject(FIELD_CHIRPSTACK_LORA_MODULATION_INFO);
            if (loraModulationInfo != null) {
                returnMap.put(LoraConstants.APP_PROPERTY_SPREADING_FACTOR,
                        loraModulationInfo.getInteger(FIELD_CHIRPSTACK_SPREADING_FACTOR));
                returnMap.put(LoraConstants.APP_PROPERTY_BANDWIDTH, loraModulationInfo.getInteger(FIELD_CHIRPSTACK_BANDWIDTH));
                returnMap.put(LoraConstants.CODING_RATE, loraModulationInfo.getString(FIELD_CHIRPSTACK_CODE_RATE));
            }
            returnMap.put(LoraConstants.FREQUENCY, txInfo.getInteger(FIELD_CHIRPSTACK_FREQUENCY));
        }

        returnMap.put(LoraConstants.APP_PROPERTY_FUNCTION_PORT, loraMessage.getInteger(FIELD_CHIRPSTACK_FUNCTION_PORT));
        returnMap.put(LoraConstants.FRAME_COUNT, loraMessage.getInteger(FIELD_CHIRPSTACK_FRAME_COUNT));

        final JsonArray rxInfos = loraMessage.getJsonArray(FIELD_CHIRPSTACK_RX_INFO);
        if (rxInfos != null) {
            final JsonArray normalizedGatways = new JsonArray();
            for (int i = 0; i < rxInfos.size(); i++) {
                final JsonObject rxInfo = rxInfos.getJsonObject(i);
                final JsonObject normalizedGatway = new JsonObject();
                String gatewayId = rxInfo.getString(FIELD_CHIRPSTACK_GATEWAY_ID);
                if (gatewayId != null) {
                    gatewayId = LoraUtils.convertFromBase64ToHex(gatewayId);
                }
                normalizedGatway.put(LoraConstants.GATEWAY_ID, gatewayId);
                normalizedGatway.put(LoraConstants.APP_PROPERTY_RSS, rxInfo.getInteger(FIELD_CHIRPSTACK_RSSI));
                normalizedGatway.put(LoraConstants.APP_PROPERTY_SNR, rxInfo.getDouble(FIELD_CHIRPSTACK_LSNR));
                normalizedGatway.put(LoraConstants.APP_PROPERTY_CHANNEL, rxInfo.getDouble(FIELD_CHIRPSTACK_CHANNEL));
                final JsonObject location = rxInfo.getJsonObject(FIELD_CHIRPSTACK_LOCATION);
                if (location != null) {
                    normalizedGatway.put(LoraConstants.APP_PROPERTY_FUNCTION_LATITUDE,
                            location.getDouble(FIELD_CHIRPSTACK_LATITUDE));
                    normalizedGatway.put(LoraConstants.APP_PROPERTY_FUNCTION_LONGITUDE,
                            location.getDouble(FIELD_CHIRPSTACK_LONGITUDE));
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
