/*******************************************************************************
 * Copyright (c) 2019, 2019 Contributors to the Eclipse Foundation
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

import io.vertx.core.json.JsonObject;
import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * A LoRaWAN provider with API for Firefly.
 */
@Component
public class FireflyProvider implements LoraProvider {

    private static final String FIELD_FIREFLY_SPREADING_FACTOR = "spreading_factor";
    private static final String FIELD_FIREFLY_FUNCTION_PORT = "port";
    private static final String FIELD_FIREFLY_PAYLOAD = "payload";
    private static final String FIELD_FIREFLY_SERVER_DATA = "server_data";
    private static final String FIELD_FIREFLY_MESSAGE_TYPE = "mtype";
    private static final String FIELD_FIREFLY_MESSAGE_TYPE_UPLINK = "confirmed_data_up";

    private static final String FIELD_FIREFLY_DEVICE = "device";
    private static final String FIELD_FIREFLY_DEVICE_EUI = "eui";

    @Override
    public String getProviderName() {
        return "firefly";
    }

    @Override
    public String pathPrefix() {
        return "/firefly";
    }

    @Override
    public String extractDeviceId(final JsonObject loraMessage) {
        return loraMessage.getJsonObject(FIELD_FIREFLY_DEVICE, new JsonObject())
                .getString(FIELD_FIREFLY_DEVICE_EUI);
    }

    @Override
    public String extractPayload(final JsonObject loraMessage) {
        return loraMessage.getString(FIELD_FIREFLY_PAYLOAD);
    }

    @Override
    public LoraMessageType extractMessageType(final JsonObject loraMessage) {
        if (FIELD_FIREFLY_MESSAGE_TYPE_UPLINK.equals(loraMessage.getJsonObject(FIELD_FIREFLY_SERVER_DATA, new JsonObject())
                .getString(FIELD_FIREFLY_MESSAGE_TYPE))) {
            return LoraMessageType.UPLINK;
        }
        return LoraMessageType.UNKNOWN;
    }

    @Override
    public Map<String, Object> extractNormalizedData(final JsonObject loraMessage) {
        final Map<String, Object> returnMap = new HashMap<>();
        if (loraMessage.containsKey(FIELD_FIREFLY_SPREADING_FACTOR)) {
            returnMap.put(LoraConstants.APP_PROPERTY_SPREADING_FACTOR, loraMessage.getInteger(FIELD_FIREFLY_SPREADING_FACTOR));
        }
        if (loraMessage.containsKey(FIELD_FIREFLY_FUNCTION_PORT)) {
            returnMap.put(LoraConstants.APP_PROPERTY_FUNCTION_PORT, loraMessage.getInteger(FIELD_FIREFLY_FUNCTION_PORT));
        }
        return returnMap;
    }

    @Override
    public JsonObject extractAdditionalData(final JsonObject loraMessage) {
        final JsonObject returnMessage = loraMessage.copy();
        if (returnMessage.containsKey(FIELD_FIREFLY_SPREADING_FACTOR)) {
            returnMessage.remove(LoraConstants.APP_PROPERTY_SPREADING_FACTOR);
        }
        if (returnMessage.containsKey(FIELD_FIREFLY_FUNCTION_PORT)) {
            returnMessage.remove(LoraConstants.APP_PROPERTY_FUNCTION_PORT);
        }
        return null;
    }
}
