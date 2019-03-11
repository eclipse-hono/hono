/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import org.apache.commons.lang.StringUtils;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.service.http.HttpUtils;
import org.springframework.stereotype.Component;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Everynet.
 */
@Component
public class EverynetProvider implements LoraProvider {

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

    @Override
    public String acceptedContentType() {
        return HttpUtils.CONTENT_TYPE_JSON;
    }

    @Override
    public HttpMethod acceptedHttpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public String extractDeviceId(final JsonObject loraMessage) {
        return loraMessage.getJsonObject(FIELD_EVERYNET_ROOT_META_OBJECT, new JsonObject())
                .getString(FIELD_EVERYNET_DEVICE_EUI);
    }

    @Override
    public String extractPayloadEncodedInBase64(final JsonObject loraMessage) {
        return loraMessage.getJsonObject(FIELD_EVERYNET_ROOT_PARAMS_OBJECT, new JsonObject())
                .getString(FIELD_EVERYNET_PAYLOAD);
    }

    @Override
    public LoraMessageType extractMessageType(final JsonObject loraMessage) {
        final String type = loraMessage.getString(FIELD_EVERYNET_TYPE, StringUtils.EMPTY);
        return "uplink".equals(type) ? LoraMessageType.UPLINK : LoraMessageType.UNKNOWN;
    }
}
