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
 * A LoRaWAN provider with API for Proximus.
 * <p>
 * This provider supports messages that comply with the
 * <a href="https://proximusapi.enco.io/asset/lora4maker/documentation#dataformat">
 * Proximus data format</a>.
 */
@Component
public class ProximusProvider extends BaseLoraProvider {

    private static final String FIELD_PROXIMUS_DEVICE_EUI = "DevEUI";
    private static final String FIELD_PROXIMUS_PAYLOAD = "payload";

    @Override
    public String getProviderName() {
        return "proximus";
    }

    @Override
    public String pathPrefix() {
        return "/proximus";
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
        return LoraUtils.getChildObject(loraMessage, FIELD_PROXIMUS_DEVICE_EUI, String.class)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer extractPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        return LoraUtils.getChildObject(loraMessage, FIELD_PROXIMUS_PAYLOAD, String.class)
                .map(s -> Buffer.buffer(BaseEncoding.base16().decode(s.toUpperCase())))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain HEX encoded payload property"));
    }
}
