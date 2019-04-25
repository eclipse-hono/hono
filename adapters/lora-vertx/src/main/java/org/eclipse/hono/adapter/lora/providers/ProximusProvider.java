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

import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Proximus.
 */
@Component
public class ProximusProvider implements LoraProvider {

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

    @Override
    public String extractDeviceId(final JsonObject loraMessage) {
        return loraMessage.getString(FIELD_PROXIMUS_DEVICE_EUI);
    }

    @Override
    public String extractPayload(final JsonObject loraMessage) {
        return loraMessage.getString(FIELD_PROXIMUS_PAYLOAD);
    }
}
