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

/**
 * Verifies behavior of {@link FireflyProvider}.
 */
public class FireflyProviderTest extends LoraProviderTestBase<FireflyProvider> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected FireflyProvider newProvider() {
        return new FireflyProvider();
    }

    /**
     * Verifies that properties are parsed correctly from the lora message.
     */
    @Test
    public void testGetMessageParsesNormalizedData() {

        final UplinkLoraMessage loraMessage = (UplinkLoraMessage) provider.getMessage(uplinkMessageBuffer);

        final Map<String, Object> map = loraMessage.getNormalizedData();
        assertThat(map.get(LoraConstants.APP_PROPERTY_MIC)).isEqualTo(Boolean.TRUE);
    }
}
