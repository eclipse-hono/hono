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

import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.UplinkLoraMessage;
import org.junit.jupiter.api.Test;

/**
 * Verifies behavior of {@link ThingsNetworkProvider}.
 */
public class ThingsNetworkProviderTest extends LoraProviderTestBase<ThingsNetworkProvider> {


    /**
     * {@inheritDoc}
     */
    @Override
    protected ThingsNetworkProvider newProvider() {
        return new ThingsNetworkProvider();
    }

    /**
     * Test an uplink message with a null payload.
     *
     * @throws Exception if anything goes wrong.
     */
    @Test
    public void testGetMessageParsesUplinkMessagePropertiesWithNullPayload() throws Exception {

        final UplinkLoraMessage loraMessage = (UplinkLoraMessage) this.provider.getMessage(LoraTestUtil.loadTestFile(this.provider.getProviderName(), LoraMessageType.UPLINK, "with-null-payload"));

        assertThat(loraMessage.getDevEUIAsString()).isEqualTo("0102030405060708");
        assertThat(loraMessage.getPayload()).isNotNull();
        assertThat(loraMessage.getPayload().length()).isEqualTo(0);
    }

}
