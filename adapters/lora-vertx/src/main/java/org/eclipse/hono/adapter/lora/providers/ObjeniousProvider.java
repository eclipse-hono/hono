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
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Objenious.
 */
@Component
public class ObjeniousProvider implements LoraProvider {

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
    public String extractDeviceId(final JsonObject loraMessage) {
        return loraMessage.getJsonObject(FIELD_DEVICE_PROPERTIES, new JsonObject()).getString(FIELD_DEVICE_ID);
    }

    @Override
    public String extractPayloadEncodedInBase64(final JsonObject loraMessage) {
        final String hexPayload = loraMessage.getString(FIELD_PAYLOAD);

        return LoraUtils.convertFromHexToBase64(hexPayload);
    }

    @Override
    public LoraMessageType extractMessageType(final JsonObject loraMessage) {
        final String type = loraMessage.getString(FIELD_TYPE, StringUtils.EMPTY);

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
    }
}
