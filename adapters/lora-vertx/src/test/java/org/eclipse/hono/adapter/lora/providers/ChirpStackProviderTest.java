/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.UplinkLoraMessage;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link ChirpStackProvider}.
 */
public class ChirpStackProviderTest extends LoraProviderTestBase<ChirpStackProvider> {


    /**
     * {@inheritDoc}
     */
    @Override
    protected ChirpStackProvider newProvider() {
        return new ChirpStackProvider();
    }

    /**
     * Verifies that properties are parsed correctly from the lora message.
     */
    @Test
    public void testGetMessageParsesProperties() {

        final UplinkLoraMessage loraMessage = (UplinkLoraMessage) provider.getMessage(uplinkMessageBuffer);

        final Map<String, Object> normalizedData = loraMessage.getNormalizedData();
        assertThat(normalizedData).contains(Map.entry(LoraConstants.APP_PROPERTY_FUNCTION_PORT, 5));
        assertThat(normalizedData).contains(Map.entry(LoraConstants.FRAME_COUNT, 10));
        assertThat(normalizedData).contains(Map.entry(LoraConstants.APP_PROPERTY_SPREADING_FACTOR, 11));
        assertThat(normalizedData).contains(Map.entry(LoraConstants.APP_PROPERTY_BANDWIDTH, 125));
        assertThat(normalizedData).contains(Map.entry(LoraConstants.CODING_RATE, "4/5"));
        assertThat(normalizedData).contains(Map.entry(LoraConstants.FREQUENCY, 868100000));

        final String gatewaysString = (String) normalizedData.get(LoraConstants.GATEWAYS);
        final JsonArray gateways = (JsonArray) Json.decodeValue(gatewaysString);
        final JsonObject gateway = gateways.getJsonObject(0);

        assertEquals(4.9144401, gateway.getDouble(LoraConstants.APP_PROPERTY_FUNCTION_LONGITUDE));
        assertEquals(52.3740364, gateway.getDouble(LoraConstants.APP_PROPERTY_FUNCTION_LATITUDE));
        assertEquals("0303030303030303", gateway.getString(LoraConstants.GATEWAY_ID));
        assertEquals(9, gateway.getDouble(LoraConstants.APP_PROPERTY_SNR));
        assertEquals(5, gateway.getInteger(LoraConstants.APP_PROPERTY_CHANNEL));
        assertEquals(-48, gateway.getInteger(LoraConstants.APP_PROPERTY_RSS));
    }
}
