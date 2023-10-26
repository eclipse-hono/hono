/*******************************************************************************
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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
import java.util.Objects;
import java.util.Set;

import org.eclipse.hono.adapter.lora.LoraMessageType;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A LoRaWAN provider with API for Kerlink.
 * This provider supports the legacy Kerlink LNS with custom content-type.
 */
@ApplicationScoped
public class KerlinkProviderCustomContentType extends JsonBasedLoraProvider {

    /**
     * The content type indicating a custom kerlink message.
     */
    public static final String HEADER_CONTENT_TYPE_KERLINK_JSON = "application/vnd.kerlink.iot-v1+json";

    private static final String FIELD_UPLINK_DEVICE_EUI = "devEui";
    private static final String FIELD_UPLINK_USER_DATA = "userdata";
    private static final String FIELD_UPLINK_PAYLOAD = "payload";

    @Override
    public String getProviderName() {
        return "kerlink-custom-content-type";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/kerlink/rxmessage");
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method always returns {@link LoraMessageType#UPLINK}.
     */
    @Override
    public LoraMessageType getMessageType(final JsonObject loraMessage) {
        return LoraMessageType.UPLINK;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method always returns {@value #HEADER_CONTENT_TYPE_KERLINK_JSON}.
     */
    @Override
    public String acceptedContentType() {
        return HEADER_CONTENT_TYPE_KERLINK_JSON;
    }

    @Override
    protected byte[] getDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_UPLINK_DEVICE_EUI, String.class)
                .map(LoraUtils::convertFromHexToBytes)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_UPLINK_USER_DATA, JsonObject.class)
                .map(userData -> userData.getValue(FIELD_UPLINK_PAYLOAD))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(s -> Buffer.buffer(Base64.getDecoder().decode(s)))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain Base64 encoded payload property"));
    }
}
