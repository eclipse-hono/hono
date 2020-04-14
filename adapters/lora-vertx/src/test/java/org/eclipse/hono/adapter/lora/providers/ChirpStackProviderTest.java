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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link ChirpStackProvider}.
 */
public class ChirpStackProviderTest {

    private final ChirpStackProvider provider = new ChirpStackProvider();

    /**
     * Verifies that the extraction of the device id from a message is successful.
     */
    @Test
    public void extractDeviceIdFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("chirpStack.uplink");
        final String deviceId = provider.extractDeviceId(loraMessage);

        assertEquals("0202020202020202", deviceId);
    }

    /**
     * Verifies the extraction of a payload from a message is successful.
     */
    @Test
    public void extractPayloadFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("chirpStack.uplink");
        final String payload = provider.extractPayload(loraMessage);

        assertEquals("data", payload);
    }

    /**
     * Verifies that the extracted message type matches uplink.
     */
    @Test
    public void extractTypeFromLoraUplinkMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("chirpStack.uplink");
        final LoraMessageType type = provider.extractMessageType(loraMessage);
        assertEquals(LoraMessageType.UPLINK, type);
    }

    /**
     * Verifies that the extracted function port is correct.
     */
    @Test
    public void extractFunctionPortFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("chirpStack.uplink");
        final Map<String, Object> map = provider.extractNormalizedData(loraMessage);
        assertTrue(map.containsKey(LoraConstants.APP_PROPERTY_FUNCTION_PORT));
        assertEquals(5, map.get(LoraConstants.APP_PROPERTY_FUNCTION_PORT));
    }

    /**
     * Verifies that the extracted frame count is correct.
     */
    @Test
    public void extractFrameCountFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("chirpStack.uplink");
        final Map<String, Object> map = provider.extractNormalizedData(loraMessage);
        assertTrue(map.containsKey(LoraConstants.FRAME_COUNT));
        assertEquals(10, map.get(LoraConstants.FRAME_COUNT));
    }

    /**
     * Verifies that the spreadingFactor is correct.
     */
    @Test
    public void extractSpreadingFactorFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("chirpStack.uplink");
        final Map<String, Object> map = provider.extractNormalizedData(loraMessage);
        assertTrue(map.containsKey(LoraConstants.APP_PROPERTY_SPREADING_FACTOR));
        assertEquals(11, map.get(LoraConstants.APP_PROPERTY_SPREADING_FACTOR));
    }

    /**
     * Verifies that the bandWidth is correct.
     */
    @Test
    public void extractBandwidthFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("chirpStack.uplink");
        final Map<String, Object> map = provider.extractNormalizedData(loraMessage);
        assertTrue(map.containsKey(LoraConstants.APP_PROPERTY_BANDWIDTH));
        assertEquals(125, map.get(LoraConstants.APP_PROPERTY_BANDWIDTH));
    }

    /**
     * Verifies that the codingRate is correct.
     */
    @Test
    public void extractCodeRateFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("chirpStack.uplink");
        final Map<String, Object> map = provider.extractNormalizedData(loraMessage);
        assertTrue(map.containsKey(LoraConstants.CODING_RATE));
        assertEquals("4/5", map.get(LoraConstants.CODING_RATE));
    }

    /**
     * Verifies that the frequency is correct.
     */
    @Test
    public void extractFrequencyFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("chirpStack.uplink");
        final Map<String, Object> map = provider.extractNormalizedData(loraMessage);
        assertTrue(map.containsKey(LoraConstants.FREQUENCY));
        assertEquals(868100000, map.get(LoraConstants.FREQUENCY));
    }

    /**
     * Verifies that the gateway is correct.
     */
    @Test
    public void extractGatewayFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("chirpStack.uplink");
        final Map<String, Object> map = provider.extractNormalizedData(loraMessage);
        assertTrue(map.containsKey(LoraConstants.GATEWAYS));
        final String gatewaysString = (String) map.get(LoraConstants.GATEWAYS);
        final JsonArray gateways = (JsonArray) Json.decodeValue(gatewaysString);
        final JsonObject gateway = gateways.getJsonObject(0);

        assertEquals(4.9144401, gateway.getDouble(LoraConstants.APP_PROPERTY_FUNCTION_LONGITUDE));
        assertEquals(52.3740364, gateway.getDouble(LoraConstants.APP_PROPERTY_FUNCTION_LATITUDE));
        assertEquals("0303030303030303", gateway.getString(LoraConstants.GATEWAY_ID));
        assertEquals(9, gateway.getInteger(LoraConstants.APP_PROPERTY_SNR));
        assertEquals(5, gateway.getInteger(LoraConstants.APP_PROPERTY_CHANNEL));
        assertEquals(-48, gateway.getInteger(LoraConstants.APP_PROPERTY_RSS));
    }
}
