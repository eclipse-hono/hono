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
import java.util.Optional;

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.springframework.stereotype.Component;

import com.google.common.io.BaseEncoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Actility.
 */
@Component
public class ActilityProvider extends BaseLoraProvider {

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

    private Optional<JsonObject> getRootObject(final JsonObject loraMessage) {
        return LoraUtils.getChildObject(loraMessage, FIELD_ACTILITY_ROOT_OBJECT, JsonObject.class);
    }

    @Override
    protected String extractDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return getRootObject(loraMessage)
                .map(root -> root.getValue(FIELD_ACTILITY_DEVICE_EUI))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device EUI property"));
    }

    @Override
    protected Buffer extractPayload(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return getRootObject(loraMessage)
                .map(root -> root.getValue(FIELD_ACTILITY_PAYLOAD))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(s -> Buffer.buffer(BaseEncoding.base16().decode(s.toUpperCase())))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued payload property"));
    }

    @Override
    protected LoraMessageType extractMessageType(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return getRootObject(loraMessage)
                .map(root -> LoraMessageType.UPLINK)
                .orElse(LoraMessageType.UNKNOWN);
    }

    @Override
    protected Map<String, Object> extractNormalizedData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        return getRootObject(loraMessage)
            .map(this::getNormalizedData)
            .orElse(Map.of());
    }

    private Map<String, Object> getNormalizedData(final JsonObject rootObject) {

        final Map<String, Object> data = new HashMap<>();
        LoraUtils.addNormalizedValue(
                rootObject,
                FIELD_ACTILITY_LRR_RSSI,
                String.class,
                LoraConstants.APP_PROPERTY_RSS,
                s -> Math.abs(Double.valueOf(s)),
                data);
        LoraUtils.addNormalizedValue(
                rootObject,
                FIELD_ACTILITY_TX_POWER,
                Double.class,
                LoraConstants.APP_PROPERTY_TX_POWER,
                d -> d,
                data);
        LoraUtils.addNormalizedValue(
                rootObject,
                FIELD_ACTILITY_CHANNEL,
                String.class,
                LoraConstants.APP_PROPERTY_CHANNEL,
                s -> s,
                data);
        LoraUtils.addNormalizedValue(
                rootObject,
                FIELD_ACTILITY_SUB_BAND,
                String.class,
                LoraConstants.APP_PROPERTY_SUB_BAND,
                s -> s,
                data);
        LoraUtils.addNormalizedValue(
                rootObject,
                FIELD_ACTILITY_SPREADING_FACTOR,
                String.class,
                LoraConstants.APP_PROPERTY_SPREADING_FACTOR,
                s -> Integer.valueOf(s),
                data);
        LoraUtils.addNormalizedValue(
                rootObject,
                FIELD_ACTILITY_LRR_SNR,
                String.class,
                LoraConstants.APP_PROPERTY_SNR,
                s -> Math.abs(Double.valueOf(s)),
                data);
        LoraUtils.addNormalizedValue(
                rootObject,
                FIELD_ACTILITY_FPORT,
                String.class,
                LoraConstants.APP_PROPERTY_FUNCTION_PORT,
                s -> Integer.valueOf(s),
                data);
        LoraUtils.addNormalizedValue(
                rootObject,
                FIELD_ACTILITY_LATITUTDE,
                String.class,
                LoraConstants.APP_PROPERTY_FUNCTION_LATITUDE,
                s -> Double.valueOf(s),
                data);
        LoraUtils.addNormalizedValue(
                rootObject,
                FIELD_ACTILITY_LONGITUDE,
                String.class,
                LoraConstants.APP_PROPERTY_FUNCTION_LONGITUDE,
                s -> Double.valueOf(s),
                data);

        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_LRRS, JsonObject.class)
            .map(lrrs -> lrrs.getValue(FIELD_ACTILITY_LRR))
            .filter(JsonArray.class::isInstance)
            .map(JsonArray.class::cast)
            .ifPresent(lrrList -> {
                final JsonArray normalizedGateways = lrrList.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .map(lrr -> {
                        final JsonObject normalizedGateway = new JsonObject();
                        LoraUtils.getChildObject(lrr, FIELD_ACTILITY_LRR_ID, String.class)
                            .ifPresent(s -> normalizedGateway.put(LoraConstants.GATEWAY_ID, s));
                        LoraUtils.getChildObject(lrr, FIELD_ACTILITY_LRR_RSSI, String.class)
                            .ifPresent(s -> normalizedGateway.put(LoraConstants.APP_PROPERTY_RSS, Math.abs(Double.valueOf(s))));
                        LoraUtils.getChildObject(lrr, FIELD_ACTILITY_LRR_SNR, String.class)
                        .ifPresent(s -> normalizedGateway.put(LoraConstants.APP_PROPERTY_SNR, Math.abs(Double.valueOf(s))));
                        return normalizedGateway;
                    })
                    .collect(() -> new JsonArray(), (array, value) -> array.add(value), (array1, array2) -> array1.addAll(array2));
                data.put(LoraConstants.GATEWAYS, normalizedGateways.toString());
            });

        return data;
    }

    @Override
    protected JsonObject extractAdditionalData(final JsonObject loraMessage) {
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
