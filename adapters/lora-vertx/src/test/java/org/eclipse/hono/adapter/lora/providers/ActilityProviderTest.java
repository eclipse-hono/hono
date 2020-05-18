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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.UplinkLoraMessage;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link ActilityProvider}.
 */
public class ActilityProviderTest extends LoraProviderTestBase<ActilityProvider> {


    /**
     * {@inheritDoc}
     */
    @Override
    protected ActilityProvider newProvider() {
        return new ActilityProvider();
    }

    /**
     * Verifies that properties are parsed correctly from the lora message.
     */
    @Test
    public void testGetMessageParsesNormalizedProperties() {

        final UplinkLoraMessage loraMessage = (UplinkLoraMessage) provider.getMessage(uplinkMessageBuffer);

        final Map<String, Object> map = loraMessage.getNormalizedData();

        assertThat(map.get(LoraConstants.APP_PROPERTY_RSS)).isEqualTo(48.0);

        final JsonArray expectedArray = new JsonArray();
        expectedArray.add(new JsonObject().put("gateway_id", "18035559").put("rss", 48.0).put("snr", 3.0));
        expectedArray.add(new JsonObject().put("gateway_id", "18035560").put("rss", 49.0).put("snr", 4.0));

        assertThat(new JsonArray((String) map.get(LoraConstants.GATEWAYS))).isEqualTo(expectedArray);
    }
}
