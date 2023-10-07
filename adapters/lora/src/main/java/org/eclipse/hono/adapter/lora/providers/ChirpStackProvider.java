/*******************************************************************************
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A LoRaWAN provider with API for ChirpStack.
 * <p>
 * This provider supports uplink messages only and expects the messages
 * to comply with the <a href="https://www.chirpstack.io/application-server/integrations/events/">
 * Protobuf based JSON format</a>.
 */
@ApplicationScoped
public class ChirpStackProvider extends JsonBasedLoraProvider {

    private static final String FIELD_CHIRPSTACK_ADR = "adr";
    private static final String FIELD_CHIRPSTACK_ALTITUDE = "altitude";
    private static final String FIELD_CHIRPSTACK_BANDWIDTH = "bandwidth";
    private static final String FIELD_CHIRPSTACK_CHANNEL = "channel";
    private static final String FIELD_CHIRPSTACK_CODE_RATE = "codeRate";
    private static final String FIELD_CHIRPSTACK_DEVICE = "devEUI";
    private static final String FIELD_CHIRPSTACK_FRAME_COUNT = "fCnt";
    private static final String FIELD_CHIRPSTACK_FREQUENCY = "frequency";
    private static final String FIELD_CHIRPSTACK_FUNCTION_PORT = "fPort";
    private static final String FIELD_CHIRPSTACK_GATEWAY_ID = "gatewayID";
    private static final String FIELD_CHIRPSTACK_LATITUDE = "latitude";
    private static final String FIELD_CHIRPSTACK_LOCATION = "location";
    private static final String FIELD_CHIRPSTACK_LONGITUDE = "longitude";
    private static final String FIELD_CHIRPSTACK_LORA_MODULATION_INFO = "loRaModulationInfo";
    private static final String FIELD_CHIRPSTACK_LSNR = "loRaSNR";
    private static final String FIELD_CHIRPSTACK_PAYLOAD = "data";
    private static final String FIELD_CHIRPSTACK_RSSI = "rssi";
    private static final String FIELD_CHIRPSTACK_RX_INFO = "rxInfo";
    private static final String FIELD_CHIRPSTACK_SPREADING_FACTOR = "spreadingFactor";
    private static final String FIELD_CHIRPSTACK_TX_INFO = "txInfo";

    private static final String COMMAND_FIELD_CHIRPSTACK_CONFIRMED = "confirmed";
    private static final String COMMAND_FIELD_CHIRPSTACK_DATA = "data";
    private static final String COMMAND_FIELD_CHIRPSTACK_DEVICE_QUEUE_ITEM = "deviceQueueItem";
    private static final String COMMAND_FIELD_CHIRPSTACK_PORT = "fPort";

    @Override
    public String getProviderName() {
        return "chirpStack";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/chirpstack");
    }

    @Override
    protected byte[] getDevEui(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_CHIRPSTACK_DEVICE, String.class)
                .map(s -> LoraUtils.convertFromBase64ToBytes(s))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain Base64 encoded device ID property"));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_CHIRPSTACK_PAYLOAD, String.class)
                .map(s -> Buffer.buffer(Base64.getDecoder().decode(s)))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain Base64 encoded payload property"));
    }

    @Override
    protected LoraMessageType getMessageType(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        if (loraMessage.containsKey(FIELD_CHIRPSTACK_PAYLOAD)) {
            return LoraMessageType.UPLINK;
        } else {
            return LoraMessageType.UNKNOWN;
        }
    }

    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        final LoraMetaData data = new LoraMetaData();

        LoraUtils.getChildObject(loraMessage, FIELD_CHIRPSTACK_FUNCTION_PORT, Integer.class)
            .ifPresent(data::setFunctionPort);
        LoraUtils.getChildObject(loraMessage, FIELD_CHIRPSTACK_FRAME_COUNT, Integer.class)
            .ifPresent(data::setFrameCount);
        LoraUtils.getChildObject(loraMessage, FIELD_CHIRPSTACK_ADR, Boolean.class)
            .ifPresent(data::setAdaptiveDataRateEnabled);

        LoraUtils.getChildObject(loraMessage, FIELD_CHIRPSTACK_TX_INFO, JsonObject.class)
            .map(txInfo -> {
                LoraUtils.getChildObject(txInfo, FIELD_CHIRPSTACK_FREQUENCY, Integer.class)
                    .ifPresent(v -> data.setFrequency(v.doubleValue() / 1_000_000));
                return txInfo.getValue(FIELD_CHIRPSTACK_LORA_MODULATION_INFO);
            })
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .ifPresent(modulationInfo -> {
                LoraUtils.getChildObject(modulationInfo, FIELD_CHIRPSTACK_SPREADING_FACTOR, Integer.class)
                    .ifPresent(data::setSpreadingFactor);
                LoraUtils.getChildObject(modulationInfo, FIELD_CHIRPSTACK_BANDWIDTH, Integer.class)
                    .ifPresent(data::setBandwidth);
                LoraUtils.getChildObject(modulationInfo, FIELD_CHIRPSTACK_CODE_RATE, String.class)
                    .ifPresent(data::setCodingRate);
            });

        LoraUtils.getChildObject(loraMessage, FIELD_CHIRPSTACK_RX_INFO, JsonArray.class)
            .ifPresent(rxInfoList -> {
                rxInfoList.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .forEach(rxInfo -> {
                        final GatewayInfo gateway = new GatewayInfo();
                        LoraUtils.getChildObject(rxInfo, FIELD_CHIRPSTACK_GATEWAY_ID, String.class)
                            .ifPresent(v -> gateway.setGatewayId(LoraUtils.convertFromBase64ToHex(v)));
                        LoraUtils.getChildObject(rxInfo, FIELD_CHIRPSTACK_RSSI, Integer.class)
                            .ifPresent(gateway::setRssi);
                        LoraUtils.getChildObject(rxInfo, FIELD_CHIRPSTACK_LSNR, Double.class)
                            .ifPresent(gateway::setSnr);
                        LoraUtils.getChildObject(rxInfo, FIELD_CHIRPSTACK_CHANNEL, Integer.class)
                            .ifPresent(gateway::setChannel);

                        LoraUtils.getChildObject(rxInfo, FIELD_CHIRPSTACK_LOCATION, JsonObject.class)
                            .map(loc -> LoraUtils.newLocation(
                                    LoraUtils.getChildObject(loc, FIELD_CHIRPSTACK_LONGITUDE, Double.class),
                                    LoraUtils.getChildObject(loc, FIELD_CHIRPSTACK_LATITUDE, Double.class),
                                    LoraUtils.getChildObject(loc, FIELD_CHIRPSTACK_ALTITUDE, Double.class)))
                            .ifPresent(gateway::setLocation);
                        data.addGatewayInfo(gateway);
                    });
            });

        return data;
    }

    @Override
    public Map<String, String> getDefaultHeaders() {
        return Map.of(
            HttpHeaders.CONTENT_TYPE.toString(), MessageHelper.CONTENT_TYPE_APPLICATION_JSON,
            HttpHeaders.ACCEPT.toString(), MessageHelper.CONTENT_TYPE_APPLICATION_JSON
        );
    }

    @Override
    protected JsonObject getCommandPayload(final Buffer payload, final String deviceId, final String subject) {
        final JsonObject deviceQueueItem = new JsonObject();
        deviceQueueItem.put(COMMAND_FIELD_CHIRPSTACK_CONFIRMED, false);
        deviceQueueItem.put(COMMAND_FIELD_CHIRPSTACK_DATA, payload.getBytes());
        try {
            deviceQueueItem.put(COMMAND_FIELD_CHIRPSTACK_PORT, Integer.parseInt(subject));
        } catch (final NumberFormatException ignored) {
            // port is not mandatory
        }

        final JsonObject json = new JsonObject();
        json.put(COMMAND_FIELD_CHIRPSTACK_DEVICE_QUEUE_ITEM, deviceQueueItem);
        return json;
    }
}
