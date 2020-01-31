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

import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link EverynetProvider}.
 */
public class EverynetProviderTest {

    private final EverynetProvider providerPost = new EverynetProvider();

    /**
     * Verifies that the extraction of the device id from a message is successful.
     */
    @Test
    public void extractDeviceIdFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("everynet.uplink");
        final String deviceId = this.providerPost.extractDeviceId(loraMessage);

        assertEquals("85f5ab9ebc636142", deviceId);
    }

    /**
     * Verifies the extraction of a payload from a message is successful.
     */
    @Test
    public void extractPayloadFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("everynet.uplink");
        final String payload = this.providerPost.extractPayload(loraMessage);

        assertEquals("YnVtbHV4", payload);
    }

    /**
     * Verifies that the extracted message type matches uplink.
     */
    @Test
    public void extractTypeFromLoraUplinkMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("everynet.uplink");
        final LoraMessageType type = this.providerPost.extractMessageType(loraMessage);
        assertEquals(LoraMessageType.UPLINK, type);
    }

    /**
     * Verifies that an unknown message type defaults to the {@link LoraMessageType#UNKNOWN} type.
     */
    @Test
    public void extractTypeFromLoraUnknownMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("everynet.uplink");
        loraMessage.put("type", "bumlux");
        final LoraMessageType type = this.providerPost.extractMessageType(loraMessage);
        assertEquals(LoraMessageType.UNKNOWN, type);
    }
}
