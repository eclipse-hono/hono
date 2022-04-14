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

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.adapter.lora.LoraMetaData;
import org.eclipse.hono.adapter.lora.UplinkLoraMessage;

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
     * {@inheritDoc}
     */
    @Override
    protected void assertMetaDataForUplinkMessage(final UplinkLoraMessage loraMessage) {

        final LoraMetaData metaData = loraMessage.getMetaData();
        assertThat(metaData.getAdaptiveDataRateEnabled()).isTrue();
        assertThat(metaData.getBandwidth()).isEqualTo(125);
        assertThat(metaData.getCodingRate()).isEqualTo("4/5");
        assertThat(metaData.getFrameCount()).isEqualTo(2602);
        assertThat(metaData.getFrequency()).isEqualTo(868.3);
        assertThat(metaData.getFunctionPort()).isEqualTo(2);
        assertThat(metaData.getSpreadingFactor()).isEqualTo(7);
        assertThat(metaData.getGatewayInfo()).hasSize(2);
        assertThat(metaData.getGatewayInfo().get(0).getGatewayId()).isEqualTo("0101010101010101");
        assertThat(metaData.getGatewayInfo().get(0).getRssi()).isEqualTo(-84);
        assertThat(metaData.getGatewayInfo().get(0).getSnr()).isEqualTo(9.8);
        assertThat(metaData.getGatewayInfo().get(1).getGatewayId()).isEqualTo("0202020202020202");
        assertThat(metaData.getGatewayInfo().get(1).getRssi()).isEqualTo(-116);
        assertThat(metaData.getGatewayInfo().get(1).getSnr()).isEqualTo(-3.2);
    }
}
