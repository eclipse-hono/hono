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

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.junit.Assert;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

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

        Assert.assertEquals("actility-device", deviceId);
    }

    /**
     * Verifies the extraction of a payload from a message is successful.
     */
    @Test
    public void extractPayloadFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("actility.uplink");
        final String payload = provider.extractPayload(loraMessage);

        Assert.assertEquals("00", payload);
    }

    /**
     * Verifies that the extracted message type matches uplink.
     */
    @Test
    public void extractTypeFromLoraUplinkMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("actility.uplink");
        final LoraMessageType type = provider.extractMessageType(loraMessage);
        Assert.assertEquals(LoraMessageType.UPLINK, type);
    }

    /**
     * Verifies that an unknown message type defaults to the {@link LoraMessageType#UNKNOWN} type.
     */
    @Test
    public void extractTypeFromLoraUnknownMessage() {
        final JsonObject loraMessage = new JsonObject();
        loraMessage.put("bumlux", "bumlux");
        final LoraMessageType type = provider.extractMessageType(loraMessage);
        Assert.assertEquals(LoraMessageType.UNKNOWN, type);
    }

    /**
     * Verifies that the extracted rssi matches 48.
     */
    @Test
    public void extractRssiFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("actility.uplink");
        final Map<String, Object> map = provider.extractNormalizedData(loraMessage);
        Assert.assertTrue(map.containsKey(LoraConstants.APP_PROPERTY_RSS));
        Assert.assertEquals(48.0, map.getOrDefault(LoraConstants.APP_PROPERTY_RSS, null));
    }

}
