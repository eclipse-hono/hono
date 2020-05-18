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
import java.util.Optional;

import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.service.http.HttpUtils;
import org.springframework.stereotype.Component;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Everynet.
 */
@Component
public class EverynetProvider extends BaseLoraProvider {

    private static final String FIELD_EVERYNET_ROOT_PARAMS_OBJECT = "params";
    private static final String FIELD_EVERYNET_ROOT_META_OBJECT = "meta";
    private static final String FIELD_EVERYNET_DEVICE_EUI = "device";
    private static final String FIELD_EVERYNET_PAYLOAD = "payload";
    private static final String FIELD_EVERYNET_TYPE = "type";

    @Override
    public String getProviderName() {
        return "everynet";
    }

    @Override
    public String pathPrefix() {
        return "/everynet";
    }

    private Optional<JsonObject> getRootObject(final JsonObject loraMessage) {
        return LoraUtils.getChildObject(loraMessage, FIELD_EVERYNET_ROOT_META_OBJECT, JsonObject.class);
    }

    /**
     * {@inheritDoc}
     *
     * @return Always {@value HttpUtils#CONTENT_TYPE_JSON}.
     */
    @Override
    public String acceptedContentType() {
        return HttpUtils.CONTENT_TYPE_JSON;
    }

    /**
     * {@inheritDoc}
     *
     * @return Always {@link HttpMethod#POST}.
     */
    @Override
    public HttpMethod acceptedHttpMethod() {
        return HttpMethod.POST;
    }

    @Override
    protected String extractDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        return getRootObject(loraMessage)
                .map(root -> root.getValue(FIELD_EVERYNET_DEVICE_EUI))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer extractPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_EVERYNET_ROOT_PARAMS_OBJECT, JsonObject.class)
                .map(root -> root.getValue(FIELD_EVERYNET_PAYLOAD))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(s -> Buffer.buffer(Base64.getDecoder().decode(s)))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain Base64 encoded payload property"));
    }

    @Override
    protected LoraMessageType extractMessageType(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_EVERYNET_TYPE, String.class)
                .map(s -> "uplink".equals(s) ? LoraMessageType.UPLINK : LoraMessageType.UNKNOWN)
                .orElse(LoraMessageType.UNKNOWN);
    }
}
