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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Verifies behavior of {@link ActilityProvider}.
 */
public class ActilityProviderTest {

    private final ActilityProvider provider = new ActilityProvider();

    /**
     * Verifies that the extraction of the device id from a message is successful.
     */
    @Test
    public void extractDeviceIdFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("actility.uplink");
        final String deviceId = provider.extractDeviceId(loraMessage);

        assertEquals("actility-device", deviceId);
    }

    /**
     * Verifies the extraction of a payload from a message is successful.
     */
    @Test
    public void extractPayloadFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("actility.uplink");
        final String payload = provider.extractPayload(loraMessage);

        assertEquals("00", payload);
    }

    /**
     * Verifies that the extracted message type matches uplink.
     */
    @Test
    public void extractTypeFromLoraUplinkMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("actility.uplink");
        final LoraMessageType type = provider.extractMessageType(loraMessage);
        assertEquals(LoraMessageType.UPLINK, type);
    }

    /**
     * Verifies that an unknown message type defaults to the {@link LoraMessageType#UNKNOWN} type.
     */
    @Test
    public void extractTypeFromLoraUnknownMessage() {
        final JsonObject loraMessage = new JsonObject();
        loraMessage.put("bumlux", "bumlux");
        final LoraMessageType type = provider.extractMessageType(loraMessage);
        assertEquals(LoraMessageType.UNKNOWN, type);
    }

    /**
     * Verifies that the extracted rssi matches 48.
     */
    @Test
    public void extractRssiFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("actility.uplink");
        final Map<String, Object> map = provider.extractNormalizedData(loraMessage);
        assertTrue(map.containsKey(LoraConstants.APP_PROPERTY_RSS));
        assertEquals(48.0, map.getOrDefault(LoraConstants.APP_PROPERTY_RSS, null));
    }

    /**
     * Verifies that the extracted gateways are correct.
     */
    @Test
    public void extractGatewaysFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("actility.uplink");
        final Map<String, Object> map = provider.extractNormalizedData(loraMessage);
        assertTrue(map.containsKey(LoraConstants.GATEWAYS));

        final JsonArray expectedArray = new JsonArray();
        expectedArray.add(new JsonObject().put("gateway_id", "18035559").put("rss", 48.0).put("snr", 3.0));
        expectedArray.add(new JsonObject().put("gateway_id", "18035560").put("rss", 49.0).put("snr", 4.0));

        final JsonArray gateways = new JsonArray(map.get(LoraConstants.GATEWAYS).toString());
        assertEquals(expectedArray, gateways);
    }

}
