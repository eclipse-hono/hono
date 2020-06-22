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
import java.util.Objects;

import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.springframework.stereotype.Component;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Kerlink.
 */
@Component
public class KerlinkProvider extends JsonBasedLoraProvider {

    static final String FIELD_KERLINK_CLUSTER_ID = "cluster-id";
    static final String FIELD_KERLINK_CUSTOMER_ID = "customer-id";

    private static final String HEADER_CONTENT_TYPE_KERLINK_JSON = "application/vnd.kerlink.iot-v1+json";

    private static final String FIELD_UPLINK_DEVICE_EUI = "devEui";
    private static final String FIELD_UPLINK_USER_DATA = "userdata";
    private static final String FIELD_UPLINK_PAYLOAD = "payload";

    @Override
    public String getProviderName() {
        return "kerlink";
    }

    @Override
    public String pathPrefix() {
        return "/kerlink/rxmessage";
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
    protected String getDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_UPLINK_DEVICE_EUI, String.class)
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
