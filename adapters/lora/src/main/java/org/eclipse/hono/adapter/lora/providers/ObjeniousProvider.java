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
import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.adapter.lora.GatewayInfo;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;

import com.google.common.io.BaseEncoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A LoRaWAN provider with API for Objenious.
 * <p>
 * This provider supports Objenious
 * <a href="https://api.objenious.com/doc/doc.html#section/Integration-with-external-applications/Messages-format">
 * uplink messages</a> only.
 */
@ApplicationScoped
public class ObjeniousProvider extends JsonBasedLoraProvider {

    private static final String FIELD_DEVICE_ID = "deveui";
    private static final String FIELD_FRAME_COUNT = "count";
    private static final String FIELD_LATITUDE = "lat";
    private static final String FIELD_LONGITUDE = "lng";
    private static final String FIELD_PAYLOAD = "payload_cleartext";
    private static final String FIELD_PORT = "port";
    private static final String FIELD_RSSI = "rssi";
    private static final String FIELD_SNR = "snr";
    private static final String FIELD_SPREADING_FACTOR = "sf";
    private static final String FIELD_TYPE = "type";

    private static final String OBJECT_DEVICE_PROPERTIES = "device_properties";
    private static final String OBJECT_PROTOCOL_DATA = "protocol_data";

    @Override
    public String getProviderName() {
        return "objenious";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/objenious");
    }

    @Override
    protected byte[] getDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, OBJECT_DEVICE_PROPERTIES, JsonObject.class)
                .map(props -> props.getValue(FIELD_DEVICE_ID))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(LoraUtils::convertFromHexToBytes)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_PAYLOAD, String.class)
                .map(s -> Buffer.buffer(BaseEncoding.base16().decode(s.toUpperCase())))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain HEX encoded payload property"));
    }

    @Override
    protected LoraMessageType getMessageType(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_TYPE, String.class)
                .map(type -> {
                    switch (type) {
                    case "join":
                        return LoraMessageType.JOIN;
                    case "uplink":
                        return LoraMessageType.UPLINK;
                    case "downlink":
                        return LoraMessageType.DOWNLINK;
                    default:
                        return LoraMessageType.UNKNOWN;
                    }
                })
                .orElse(LoraMessageType.UNKNOWN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        final LoraMetaData data = new LoraMetaData();

        LoraUtils.getChildObject(loraMessage, FIELD_FRAME_COUNT, Integer.class)
            .ifPresent(data::setFrameCount);
        Optional.ofNullable(LoraUtils.newLocation(
                LoraUtils.getChildObject(loraMessage, FIELD_LONGITUDE, Double.class),
                LoraUtils.getChildObject(loraMessage, FIELD_LATITUDE, Double.class),
                Optional.empty()))
            .ifPresent(data::setLocation);

        LoraUtils.getChildObject(loraMessage, OBJECT_PROTOCOL_DATA, JsonObject.class)
            .map(prot -> {
                LoraUtils.getChildObject(prot, FIELD_PORT, Integer.class)
                    .ifPresent(data::setFunctionPort);
                LoraUtils.getChildObject(prot, FIELD_SPREADING_FACTOR, Integer.class)
                    .ifPresent(data::setSpreadingFactor);
                final GatewayInfo gwInfo = new GatewayInfo();
                LoraUtils.getChildObject(prot, FIELD_RSSI, Integer.class)
                    .ifPresent(gwInfo::setRssi);
                LoraUtils.getChildObject(prot, FIELD_SNR, Double.class)
                    .ifPresent(gwInfo::setSnr);
                return gwInfo;
            })
            .ifPresent(data::addGatewayInfo);

        return data;
    }
}
