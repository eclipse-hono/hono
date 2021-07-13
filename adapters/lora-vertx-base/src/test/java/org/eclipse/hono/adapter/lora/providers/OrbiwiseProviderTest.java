/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.adapter.lora.LoraMetaData;
import org.eclipse.hono.adapter.lora.UplinkLoraMessage;

/**
 * Verifies behavior of {@link OrbiwiseProvider}.
 */
public class OrbiwiseProviderTest extends LoraProviderTestBase<OrbiwiseProvider> {


    /**
     * {@inheritDoc}
     */
    @Override
    protected OrbiwiseProvider newProvider() {
        return new OrbiwiseProvider();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void assertMetaDataForUplinkMessage(final UplinkLoraMessage loraMessage) {

        final LoraMetaData data = loraMessage.getMetaData();

        assertThat(data.getGatewayInfo()).hasSize(1);
        assertThat(data.getGatewayInfo().get(0).getGatewayId()).isEqualTo("10000001");
        assertThat(data.getGatewayInfo().get(0).getRssi()).isEqualTo(-112);
        assertThat(data.getGatewayInfo().get(0).getSnr()).isEqualTo(-7.75);

        assertThat(data.getSpreadingFactor()).isEqualTo(10);
        assertThat(data.getFunctionPort()).isEqualTo(2);
        assertThat(data.getFrameCount()).isEqualTo(57);
        assertThat(data.getFrequency()).isEqualTo(868.5);
    }
}
