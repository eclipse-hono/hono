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

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.springframework.stereotype.Component;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for ChirpStack.
 * <p>
 * This provider supports uplink messages only and expects the messages
 * to comply with the <a href="https://www.chirpstack.io/application-server/integrate/sending-receiving/">
 * Protobuf based JSON format</a>.
 */
@Component
public class ChirpStackProvider extends BaseLoraProvider {

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
    protected String extractDevEui(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_CHIRPSTACK_DEVICE, String.class)
                .map(s -> LoraUtils.convertFromBase64ToHex(s))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain Base64 encoded device ID property"));
    }

    @Override
    protected Buffer extractPayload(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_CHIRPSTACK_PAYLOAD, String.class)
                .map(s -> Buffer.buffer(Base64.getDecoder().decode(s)))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain Base64 encoded payload property"));
    }

    @Override
    protected LoraMessageType extractMessageType(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        if (loraMessage.containsKey(FIELD_CHIRPSTACK_PAYLOAD)) {
            return LoraMessageType.UPLINK;
        } else {
            return LoraMessageType.UNKNOWN;
        }
    }

    @Override
    protected Map<String, Object> extractNormalizedData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        final Map<String, Object> data = new HashMap<>();

        LoraUtils.addNormalizedValue(
                loraMessage,
                FIELD_CHIRPSTACK_FUNCTION_PORT,
                Integer.class,
                LoraConstants.APP_PROPERTY_FUNCTION_PORT,
                n -> n,
                data);
        LoraUtils.addNormalizedValue(
                loraMessage,
                FIELD_CHIRPSTACK_FRAME_COUNT,
                Integer.class,
                LoraConstants.FRAME_COUNT,
                n -> n,
                data);

        LoraUtils.getChildObject(loraMessage, FIELD_CHIRPSTACK_TX_INFO, JsonObject.class)
            .map(txInfo -> {
                LoraUtils.addNormalizedValue(
                        txInfo,
                        FIELD_CHIRPSTACK_FREQUENCY,
                        Integer.class,
                        LoraConstants.FREQUENCY,
                        v -> v,
                        data);
                return txInfo.getValue(FIELD_CHIRPSTACK_LORA_MODULATION_INFO);
            })
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .ifPresent(modulationInfo -> {
                LoraUtils.addNormalizedValue(
                        modulationInfo,
                        FIELD_CHIRPSTACK_SPREADING_FACTOR,
                        Integer.class,
                        LoraConstants.APP_PROPERTY_SPREADING_FACTOR,
                        v -> v,
                        data);
                LoraUtils.addNormalizedValue(
                        modulationInfo,
                        FIELD_CHIRPSTACK_BANDWIDTH,
                        Integer.class,
                        LoraConstants.APP_PROPERTY_BANDWIDTH,
                        v -> v,
                        data);
                LoraUtils.addNormalizedValue(
                        modulationInfo,
                        FIELD_CHIRPSTACK_CODE_RATE,
                        String.class,
                        LoraConstants.CODING_RATE,
                        v -> v,
                        data);
            });

        LoraUtils.getChildObject(loraMessage, FIELD_CHIRPSTACK_RX_INFO, JsonArray.class)
            .ifPresent(rxInfoList -> {
                final JsonArray normalizedGateways = rxInfoList.stream()
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast)
                        .map(rxInfo -> {
                            final JsonObject normalizedGateway = new JsonObject();
                            LoraUtils.getChildObject(rxInfo, FIELD_CHIRPSTACK_GATEWAY_ID, String.class)
                                .ifPresent(v -> normalizedGateway.put(LoraConstants.GATEWAY_ID, LoraUtils.convertFromBase64ToHex(v)));
                            LoraUtils.getChildObject(rxInfo, FIELD_CHIRPSTACK_RSSI, Integer.class)
                                .ifPresent(v -> normalizedGateway.put(LoraConstants.APP_PROPERTY_RSS, v));
                            LoraUtils.getChildObject(rxInfo, FIELD_CHIRPSTACK_LSNR, Double.class)
                                .ifPresent(v -> normalizedGateway.put(LoraConstants.APP_PROPERTY_SNR, v));
                            LoraUtils.getChildObject(rxInfo, FIELD_CHIRPSTACK_CHANNEL, Integer.class)
                                .ifPresent(v -> normalizedGateway.put(LoraConstants.APP_PROPERTY_CHANNEL, v));

                            LoraUtils.getChildObject(rxInfo, FIELD_CHIRPSTACK_LOCATION, JsonObject.class)
                                .ifPresent(loc -> {
                                    LoraUtils.getChildObject(loc, FIELD_CHIRPSTACK_LATITUDE, Double.class)
                                        .ifPresent(v -> normalizedGateway.put(LoraConstants.APP_PROPERTY_FUNCTION_LATITUDE, v));
                                    LoraUtils.getChildObject(loc, FIELD_CHIRPSTACK_LONGITUDE, Double.class)
                                        .ifPresent(v -> normalizedGateway.put(LoraConstants.APP_PROPERTY_FUNCTION_LONGITUDE, v));
                                });
                            return normalizedGateway;
                        })
                        .collect(() -> new JsonArray(), (array, value) -> array.add(value), (array1, array2) -> array1.addAll(array2));
                data.put(LoraConstants.GATEWAYS, normalizedGateways.toString());
            });

        return data;
    }

    /**
     * {@inheritDoc}
     *
     * @return Always {@code null}.
     */
    @Override
    protected JsonObject extractAdditionalData(final JsonObject loraMessage) {
        return null;
    }
}
