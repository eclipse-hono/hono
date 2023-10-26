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

import java.util.Arrays;
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
 * A LoRaWAN provider with API for Orbiwise.
 */
@ApplicationScoped
public class OrbiwiseProvider extends JsonBasedLoraProvider {

    private static final String FIELD_ORBIWISE_CODING_RATE = "cr_used";
    private static final String FIELD_ORBIWISE_DEVICE_EUI = "deveui";
    private static final String FIELD_ORBIWISE_DR = "dr_used";
    private static final String FIELD_ORBIWISE_FRAME_COUNT = "fcnt";
    private static final String FIELD_ORBIWISE_FREQUENCY = "freq";
    private static final String FIELD_ORBIWISE_FUNCTION_PORT = "port";
    private static final String FIELD_ORBIWISE_GATEWAY_EUI = "gtw_id";
    private static final String FIELD_ORBIWISE_LSNR = "snr";
    private static final String FIELD_ORBIWISE_PAYLOAD = "dataFrame";
    private static final String FIELD_ORBIWISE_RSSI = "rssi";
    private static final String FIELD_ORBIWISE_SPREADING_FACTOR = "sf_used";
    private static final String FIELD_REGEX_BANDWIDTH = "BW";

    private static final String OBJECT_ORBIWISE_GATEWAY_INFO = "gtw_info";

    @Override
    public String getProviderName() {
        return "orbiwise";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/orbiwise/*");
    }

    @Override
    protected byte[] getDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_ORBIWISE_DEVICE_EUI, String.class)
                .map(LoraUtils::convertFromHexToBytes)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_ORBIWISE_PAYLOAD, String.class)
                .map(s -> Buffer.buffer(BaseEncoding.base16().decode(s.toUpperCase())))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued payload property"));
    }

    @Override
    protected LoraMessageType getMessageType(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraMessageType.UPLINK;
    }

    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        final LoraMetaData data = new LoraMetaData();

        LoraUtils.getChildObject(loraMessage, FIELD_ORBIWISE_SPREADING_FACTOR, Integer.class)
            .ifPresent(data::setSpreadingFactor);
        LoraUtils.getChildObject(loraMessage, FIELD_ORBIWISE_FUNCTION_PORT, Integer.class)
            .ifPresent(data::setFunctionPort);
        LoraUtils.getChildObject(loraMessage, FIELD_ORBIWISE_FRAME_COUNT, String.class)
            .map(Integer::parseInt)
            .ifPresent(data::setFrameCount);
        LoraUtils.getChildObject(loraMessage, FIELD_ORBIWISE_DR, String.class)
            .map(dr -> Arrays.stream(dr.split(FIELD_REGEX_BANDWIDTH)))
            .flatMap(stream -> stream.skip(1).findFirst())
            .map(Integer::parseInt)
            .ifPresent(data::setBandwidth);

        LoraUtils.getChildObject(loraMessage, FIELD_ORBIWISE_FREQUENCY, Long.class)
            .map(l -> l / 1000000000000.0)
            .ifPresent(data::setFrequency);
        LoraUtils.getChildObject(loraMessage, FIELD_ORBIWISE_CODING_RATE, String.class)
                .ifPresent(data::setCodingRate);

        LoraUtils.getChildObject(loraMessage, OBJECT_ORBIWISE_GATEWAY_INFO, JsonArray.class)
            .ifPresent(gwInfos -> {
                gwInfos.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .forEach(gw -> {
                        final GatewayInfo gwInfo = new GatewayInfo();
                        LoraUtils.getChildObject(gw, FIELD_ORBIWISE_GATEWAY_EUI, String.class)
                            .ifPresent(gwInfo::setGatewayId);
                        LoraUtils.getChildObject(gw, FIELD_ORBIWISE_RSSI, Integer.class)
                            .ifPresent(gwInfo::setRssi);
                        LoraUtils.getChildObject(gw, FIELD_ORBIWISE_LSNR, Double.class)
                            .ifPresent(gwInfo::setSnr);
                        data.addGatewayInfo(gwInfo);
                    });
            });

        return data;
    }
}
