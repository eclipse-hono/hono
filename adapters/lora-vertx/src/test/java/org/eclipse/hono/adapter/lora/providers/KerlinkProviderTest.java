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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;

/**
 * Verifies the behavior of {@link KerlinkProvider}.
 */
@ExtendWith(VertxExtension.class)
public class KerlinkProviderTest {

    private static final Logger LOG = LoggerFactory.getLogger(KerlinkProviderTest.class);

    private KerlinkProvider provider;

    /**
     * Sets up the fixture.
     * 
     * @param testInfo The test meta data.
     */
    @BeforeEach
    public void before(final TestInfo testInfo) {

        LOG.info("running test: {}", testInfo.getDisplayName());
        provider = new KerlinkProvider();
    }

    /**
     * Verifies that the extraction of the device id from a message is successful.
     */
    @Test
    public void extractDeviceIdFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("kerlink.uplink");
        final String deviceId = provider.extractDeviceId(loraMessage);

        assertEquals("myBumluxDevice", deviceId);
    }

    /**
     * Verifies the extraction of a payload from a message is successful.
     */
    @Test
    public void extractPayloadFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("kerlink.uplink");
        final String payload = provider.extractPayload(loraMessage);

        assertEquals("YnVtbHV4", payload);
    }

    /**
     * Verifies that the extracted message type matches uplink.
     */
    @Test
    public void extractTypeFromLoraUplinkMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("kerlink.uplink");
        final LoraMessageType type = provider.extractMessageType(loraMessage);
        assertEquals(LoraMessageType.UPLINK, type);
    }
}
