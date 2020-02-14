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
import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Verifies behavior of {@link FireflyProvider}.
 */
public class FireflyProviderTest {

    private final FireflyProvider provider = new FireflyProvider();

    /**
     * Verifies that the extraction of the device id from a message is successful.
     */
    @Test
    public void extractDeviceIdFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("firefly.uplink");
        final String deviceId = provider.extractDeviceId(loraMessage);

        assertEquals("firefly-device", deviceId);
    }

    /**
     * Verifies the extraction of a payload from a message is successful.
     */
    @Test
    public void extractPayloadFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("firefly.uplink");
        final String payload = provider.extractPayload(loraMessage);

        assertEquals("payload", payload);
    }

    /**
     * Verifies that the extracted message type matches uplink.
     */
    @Test
    public void extractTypeFromLoraUplinkMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("firefly.uplink");
        final LoraMessageType type = provider.extractMessageType(loraMessage);
        assertEquals(LoraMessageType.UPLINK, type);
    }

    /**
     * Verifies that the extracted mic is true.
     */
    @Test
    public void extractMicFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("firefly.uplink");
        final Map<String, Object> map = provider.extractNormalizedData(loraMessage);
        assertTrue(map.containsKey(LoraConstants.APP_PROPERTY_MIC));
        assertTrue((boolean) map.getOrDefault(LoraConstants.APP_PROPERTY_MIC, null));
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

}
