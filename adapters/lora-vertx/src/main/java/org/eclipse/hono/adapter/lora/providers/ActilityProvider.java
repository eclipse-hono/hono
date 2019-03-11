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

import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Actility.
 */
@Component
public class ActilityProvider implements LoraProvider {

    private static final String FIELD_ACTILITY_ROOT_OBJECT = "DevEUI_uplink";
    private static final String FIELD_ACTILITY_DEVICE_EUI = "DevEUI";
    private static final String FIELD_ACTILITY_PAYLOAD = "payload_hex";

    @Override
    public String getProviderName() {
        return "actility";
    }

    @Override
    public String pathPrefix() {
        return "/actility";
    }

    @Override
    public String extractDeviceId(final JsonObject loraMessage) {
        return loraMessage.getJsonObject(FIELD_ACTILITY_ROOT_OBJECT, new JsonObject())
                .getString(FIELD_ACTILITY_DEVICE_EUI);
    }

    @Override
    public String extractPayloadEncodedInBase64(final JsonObject loraMessage) {
        final String hexPayload = loraMessage.getJsonObject(FIELD_ACTILITY_ROOT_OBJECT, new JsonObject())
                .getString(FIELD_ACTILITY_PAYLOAD);

        return LoraUtils.convertFromHexToBase64(hexPayload);
    }

    @Override
    public LoraMessageType extractMessageType(final JsonObject loraMessage) {
        final String[] messageKeys = loraMessage.getMap().keySet().toArray(new String[0]);
        if (messageKeys.length > 0 && FIELD_ACTILITY_ROOT_OBJECT.equals(messageKeys[0])) {
            return LoraMessageType.UPLINK;
        }
        return LoraMessageType.UNKNOWN;
    }
}
