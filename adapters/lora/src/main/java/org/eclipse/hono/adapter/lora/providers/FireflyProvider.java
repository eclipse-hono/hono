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

import java.util.Objects;
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
 * A LoRaWAN provider with API for Firefly.
 * <p>
 * This provider supports messages as described by the
 * <a href="https://apidocs.fireflyiot.com/#list-packets">Firefly API</a>.
 */
@ApplicationScoped
public class FireflyProvider extends JsonBasedLoraProvider {

    private static final String FIELD_FIREFLY_ADR = "adr";
    private static final String FIELD_FIREFLY_BANDWIDTH = "bandwidth";
    private static final String FIELD_FIREFLY_CODING_RATE = "codr";
    private static final String FIELD_FIREFLY_DEVICE_EUI = "eui";
    private static final String FIELD_FIREFLY_FRAME_COUNT = "frame_counter";
    private static final String FIELD_FIREFLY_FREQUENCY = "freq";
    private static final String FIELD_FIREFLY_FUNCTION_PORT = "port";
    private static final String FIELD_FIREFLY_GATEWAY_EUI = "gweui";
    private static final String FIELD_FIREFLY_LSNR = "lsnr";
    private static final String FIELD_FIREFLY_MESSAGE_TYPE = "mtype";
    private static final String FIELD_FIREFLY_PAYLOAD = "payload";
    private static final String FIELD_FIREFLY_RSSI = "rssi";
    private static final String FIELD_FIREFLY_SPREADING_FACTOR = "spreading_factor";

    private static final String COMMAND_FIELD_FIREFLY_PAYLOAD = "payload";
    private static final String COMMAND_FIELD_FIREFLY_ENCODING = "encoding";
    private static final String COMMAND_FIELD_FIREFLY_CONFIRMED = "confirmed";
    private static final String COMMAND_FIELD_FIREFLY_PORT = "port";

    private static final String MESSAGE_TYPE_UPLINK = "confirmed_data_up";
    private static final String MESSAGE_TYPE_UPLINK_UNCONFIRMED = "unconfirmed_data_up";

    private static final String OBJECT_FIREFLY_DEVICE = "device";
    private static final String OBJECT_FIREFLY_GATEWAY_RX = "gwrx";
    private static final String OBJECT_FIREFLY_PARSED_PACKET = "parsed_packet";
    private static final String OBJECT_FIREFLY_SERVER_DATA = "server_data";

    @Override
    public String getProviderName() {
        return "firefly";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/firefly");
    }

    @Override
    protected byte[] getDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, OBJECT_FIREFLY_DEVICE, JsonObject.class)
                .map(device -> device.getValue(FIELD_FIREFLY_DEVICE_EUI))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(LoraUtils::convertFromHexToBytes)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_FIREFLY_PAYLOAD, String.class)
                .map(s -> Buffer.buffer(BaseEncoding.base16().decode(s.toUpperCase())))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued payload property"));
    }

    @Override
    protected LoraMessageType getMessageType(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, OBJECT_FIREFLY_SERVER_DATA, JsonObject.class)
                .map(serverData -> serverData.getValue(FIELD_FIREFLY_MESSAGE_TYPE))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(s -> MESSAGE_TYPE_UPLINK.equals(s) || MESSAGE_TYPE_UPLINK_UNCONFIRMED.equals(s))
                .map(type -> LoraMessageType.UPLINK)
                .orElse(LoraMessageType.UNKNOWN);
    }

    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        final LoraMetaData data = new LoraMetaData();

        LoraUtils.getChildObject(loraMessage, FIELD_FIREFLY_BANDWIDTH, Integer.class)
            .ifPresent(data::setBandwidth);
        LoraUtils.getChildObject(loraMessage, FIELD_FIREFLY_SPREADING_FACTOR, Integer.class)
            .ifPresent(data::setSpreadingFactor);
        LoraUtils.getChildObject(loraMessage, FIELD_FIREFLY_FUNCTION_PORT, Integer.class)
            .ifPresent(data::setFunctionPort);
        LoraUtils.getChildObject(loraMessage, FIELD_FIREFLY_FRAME_COUNT, Integer.class)
            .ifPresent(data::setFrameCount);

        LoraUtils.getChildObject(loraMessage, OBJECT_FIREFLY_PARSED_PACKET, JsonObject.class)
            .map(packet -> packet.getValue(FIELD_FIREFLY_ADR))
            .filter(Boolean.class::isInstance)
            .map(Boolean.class::cast)
            .ifPresent(data::setAdaptiveDataRateEnabled);

        LoraUtils.getChildObject(loraMessage, OBJECT_FIREFLY_SERVER_DATA, JsonObject.class)
            .map(serverData -> {

                LoraUtils.getChildObject(serverData, FIELD_FIREFLY_FREQUENCY, Double.class)
                    .ifPresent(data::setFrequency);
                LoraUtils.getChildObject(serverData, FIELD_FIREFLY_CODING_RATE, String.class)
                    .ifPresent(data::setCodingRate);

                return serverData.getValue(OBJECT_FIREFLY_GATEWAY_RX);
            })
            .filter(JsonArray.class::isInstance)
            .map(JsonArray.class::cast)
            .ifPresent(gwRxs -> {
                gwRxs.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .forEach(gwRx -> {
                        final GatewayInfo gwInfo = new GatewayInfo();
                        LoraUtils.getChildObject(gwRx, FIELD_FIREFLY_GATEWAY_EUI, String.class)
                            .ifPresent(gwInfo::setGatewayId);
                        LoraUtils.getChildObject(gwRx, FIELD_FIREFLY_RSSI, Integer.class)
                            .ifPresent(gwInfo::setRssi);
                        LoraUtils.getChildObject(gwRx, FIELD_FIREFLY_LSNR, Double.class)
                            .ifPresent(gwInfo::setSnr);
                        data.addGatewayInfo(gwInfo);
                    });
            });

        return data;
    }

    @Override
    protected JsonObject getCommandPayload(final Buffer payload, final String deviceId, final String subject) {
        final JsonObject json = new JsonObject();
        json.put(COMMAND_FIELD_FIREFLY_PAYLOAD, BaseEncoding.base16().encode(payload.getBytes()));
        json.put(COMMAND_FIELD_FIREFLY_ENCODING, "base16");
        json.put(COMMAND_FIELD_FIREFLY_CONFIRMED, false);
        try {
            json.put(COMMAND_FIELD_FIREFLY_PORT, Integer.parseInt(subject));
        } catch (final NumberFormatException ignored) {
            // port is not mandatory
        }
        return json;
    }
}
