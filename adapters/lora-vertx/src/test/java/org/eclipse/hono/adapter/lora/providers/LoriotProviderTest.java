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

import org.eclipse.hono.adapter.lora.LoraMetaData;
import org.eclipse.hono.adapter.lora.UplinkLoraMessage;

/**
 * Verifies behavior of {@link LoriotProvider}.
 */
public class LoriotProviderTest extends LoraProviderTestBase<LoriotProvider> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected LoriotProvider newProvider() {
        return new LoriotProvider();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void assertMetaDataForUplinkMessage(final UplinkLoraMessage loraMessage) {

        final LoraMetaData metaData = loraMessage.getMetaData();
        assertThat(metaData.getBandwidth()).isEqualTo(125);
        assertThat(metaData.getCodingRateIdentifier()).isEqualTo("4/5");
        assertThat(metaData.getFrameCount()).isEqualTo(135);
        assertThat(metaData.getFrequency()).isEqualTo(868.3);
        assertThat(metaData.getFunctionPort()).isEqualTo(2);
        assertThat(metaData.getSpreadingFactor()).isEqualTo(7);
        assertThat(metaData.getGatewayInfo()).hasSize(1);
        assertThat(metaData.getGatewayInfo().get(0).getGatewayId()).isEqualTo("0101010101010101");
        assertThat(metaData.getGatewayInfo().get(0).getRssi()).isEqualTo(-63);
        assertThat(metaData.getGatewayInfo().get(0).getSnr()).isEqualTo(10);
        assertThat(metaData.getGatewayInfo().get(0).getLocation().getLongitude()).isEqualTo(4.4007817);
        assertThat(metaData.getGatewayInfo().get(0).getLocation().getLatitude()).isEqualTo(12);
    }
}
