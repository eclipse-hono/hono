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

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.springframework.stereotype.Component;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Things Network.
 */
@Component
public class ThingsNetworkProvider extends BaseLoraProvider {

    private static final String FIELD_TTN_DEVICE_EUI = "hardware_serial";
    private static final String FIELD_TTN_PAYLOAD_RAW = "payload_raw";
    private static final String FIELD_TTN_FPORT = "port";

    @Override
    public String getProviderName() {
        return "ttn";
    }

    @Override
    public String pathPrefix() {
        return "/ttn";
    }

    /**
     * {@inheritDoc}
     *
     * @return Always {@link LoraMessageType#UPLINK}.
     */
    @Override
    protected LoraMessageType extractMessageType(final JsonObject loraMessage) {
        return LoraMessageType.UPLINK;
    }

    @Override
    protected String extractDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_TTN_DEVICE_EUI, String.class)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer extractPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        if (loraMessage.containsKey(FIELD_TTN_PAYLOAD_RAW) && loraMessage.getValue(FIELD_TTN_PAYLOAD_RAW) == null) {
            // ... this is an empty payload message, still valid.
            return Buffer.buffer();
        }

        return LoraUtils.getChildObject(loraMessage, FIELD_TTN_PAYLOAD_RAW, String.class)
                .map(s -> Buffer.buffer(Base64.getDecoder().decode(s)))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain Base64 encoded payload property"));
    }

    @Override
    protected Map<String, Object> extractNormalizedData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        final Map<String, Object> returnMap = new HashMap<>();

        LoraUtils.addNormalizedValue(loraMessage, FIELD_TTN_FPORT, Integer.class, LoraConstants.APP_PROPERTY_FUNCTION_PORT, v -> v, returnMap);

        return returnMap;
    }

}
