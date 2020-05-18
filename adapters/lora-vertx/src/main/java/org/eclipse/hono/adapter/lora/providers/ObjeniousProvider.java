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

import java.util.Objects;

import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.springframework.stereotype.Component;

import com.google.common.io.BaseEncoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Objenious.
 * <p>
 * This provider supports Objenious
 * <a href="https://api.objenious.com/doc/doc.html#section/Integration-with-external-applications/Messages-format">
 * uplink messages</a> only.
 */
@Component
public class ObjeniousProvider extends BaseLoraProvider {

    private static final String FIELD_DEVICE_PROPERTIES = "device_properties";
    private static final String FIELD_DEVICE_ID = "deveui";
    private static final String FIELD_PAYLOAD = "payload_cleartext";
    private static final String FIELD_TYPE = "type";

    @Override
    public String getProviderName() {
        return "objenious";
    }

    @Override
    public String pathPrefix() {
        return "/objenious";
    }

    @Override
    protected String extractDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_DEVICE_PROPERTIES, JsonObject.class)
                .map(props -> props.getValue(FIELD_DEVICE_ID))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer extractPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_PAYLOAD, String.class)
                .map(s -> Buffer.buffer(BaseEncoding.base16().decode(s.toUpperCase())))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain HEX encoded payload property"));
    }

    @Override
    protected LoraMessageType extractMessageType(final JsonObject loraMessage) {

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
}
