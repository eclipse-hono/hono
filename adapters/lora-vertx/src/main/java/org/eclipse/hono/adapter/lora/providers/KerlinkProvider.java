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

import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Kerlink.
 */
@Component
public class KerlinkProvider implements LoraProvider {

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

    @Override
    public String acceptedContentType() {
        return HEADER_CONTENT_TYPE_KERLINK_JSON;
    }

    @Override
    public String extractDeviceId(final JsonObject loraMessage) {
        return loraMessage.getString(FIELD_UPLINK_DEVICE_EUI);
    }

    @Override
    public String extractPayload(final JsonObject loraMessage) {
        return loraMessage.getJsonObject(FIELD_UPLINK_USER_DATA, new JsonObject()).getString(FIELD_UPLINK_PAYLOAD);
    }
}
