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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Things Network.
 */
@Component
public class ThingsNetworkProvider implements LoraProvider {

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

    @Override
    public String extractDeviceId(final JsonObject loraMessage) {
        return loraMessage.getString(FIELD_TTN_DEVICE_EUI);
    }

    @Override
    public String extractPayload(final JsonObject loraMessage) {
        return loraMessage.getString(FIELD_TTN_PAYLOAD_RAW);
    }

    @Override
    public Map<String, Object> extractNormalizedData(final JsonObject loraMessage) {
        final Map<String, Object> returnMap = new HashMap<>();

        final Integer fport = loraMessage.getInteger(FIELD_TTN_FPORT);
        if (fport != null) {
            returnMap.put(LoraConstants.APP_PROPERTY_FUNCTION_PORT, fport);
        }

        return returnMap;
    }

}
