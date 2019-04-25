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

import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.junit.Assert;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link ObjeniousProvider}.
 */
public class ObjeniousProviderTest {

    private final ObjeniousProvider provider = new ObjeniousProvider();

    /**
     * Verifies that the extraction of the device id from a message is successful.
     */
    @Test
    public void extractDeviceIdFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("objenious.uplink");
        final String deviceId = provider.extractDeviceId(loraMessage);

        Assert.assertEquals("2032e013597bde6b", deviceId);
    }

    /**
     * Verifies the extraction of a payload from a message is successful.
     */
    @Test
    public void extractPayloadFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("objenious.uplink");
        final String payload = provider.extractPayload(loraMessage);

        Assert.assertEquals("00000000004000000000000000000000000000000000000000000000000000000000", payload);
    }

    /**
     * Verifies that the extracted message type matches uplink.
     */
    @Test
    public void extractTypeFromLoraUplinkMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("objenious.uplink");
        final LoraMessageType type = provider.extractMessageType(loraMessage);
        Assert.assertEquals(LoraMessageType.UPLINK, type);
    }

    /**
     * Verifies that the extracted message type matches join.
     */
    @Test
    public void extractTypeFromLoraJoinMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("objenious.join");
        final LoraMessageType type = provider.extractMessageType(loraMessage);
        Assert.assertEquals(LoraMessageType.JOIN, type);
    }

    /**
     * Verifies that the extracted message type matches downlink.
     */
    @Test
    public void extractTypeFromLoraDownlinkMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("objenious.downlink");
        final LoraMessageType type = provider.extractMessageType(loraMessage);
        Assert.assertEquals(LoraMessageType.DOWNLINK, type);
    }

    /**
     * Verifies that an unknown message type defaults to the {@link LoraMessageType#UNKNOWN} type.
     */
    @Test
    public void extractTypeFromLoraUnknownMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("objenious.uplink");
        loraMessage.put("type", "bumlux");
        final LoraMessageType type = provider.extractMessageType(loraMessage);
        Assert.assertEquals(LoraMessageType.UNKNOWN, type);
    }
}
