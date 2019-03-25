/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Verifies the behavior of {@link KerlinkProvider}.
 */
@RunWith(VertxUnitRunner.class)
public class KerlinkProviderTest {

    private KerlinkProvider provider;


    private final Vertx vertx = Vertx.vertx();

    /**
     * Sets up the fixture.
     */
    @Before
    public void before() {
        provider = new KerlinkProvider(vertx, new ConcurrentMapCacheManager());
        // Use very small value here to avoid long running unit tests.
        provider.setTokenPreemptiveInvalidationTimeInMs(100);
    }

    /**
     * Verifies that the extraction of the device id from a message is successful.
     */
    @Test
    public void extractDeviceIdFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("kerlink.uplink");
        final String deviceId = provider.extractDeviceId(loraMessage);

        Assert.assertEquals("myBumluxDevice", deviceId);
    }

    /**
     * Verifies the extraction of a payload from a message is successful.
     */
    @Test
    public void extractPayloadFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("kerlink.uplink");
        final String payload = provider.extractPayloadEncodedInBase64(loraMessage);

        Assert.assertEquals("YnVtbHV4", payload);
    }

    /**
     * Verifies that the extracted message type matches uplink.
     */
    @Test
    public void extractTypeFromLoraUplinkMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("kerlink.uplink");
        final LoraMessageType type = provider.extractMessageType(loraMessage);
        Assert.assertEquals(LoraMessageType.UPLINK, type);
    }
}
