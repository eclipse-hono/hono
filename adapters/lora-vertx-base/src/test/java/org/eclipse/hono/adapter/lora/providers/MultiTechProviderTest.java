/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
 * Verifies behavior of {@link MultiTechProvider}.
 */
public class MultiTechProviderTest extends LoraProviderTestBase<MultiTechProvider> {


    /**
     * {@inheritDoc}
     */
    @Override
    protected MultiTechProvider newProvider() {
        return new MultiTechProvider();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void assertMetaDataForUplinkMessage(final UplinkLoraMessage loraMessage) {

        final LoraMetaData metaData = loraMessage.getMetaData();
        assertThat(metaData.getFunctionPort()).isEqualTo(2);
        assertThat(metaData.getFrameCount()).isEqualTo(2);
        assertThat(metaData.getSpreadingFactor()).isEqualTo(12);
        assertThat(metaData.getBandwidth()).isEqualTo(125);
        assertThat(metaData.getFrequency()).isEqualTo(868.5);
        assertThat(metaData.getCodingRate()).isEqualTo("4/5");
        assertThat(metaData.getAdaptiveDataRateEnabled()).isTrue();

        assertThat(metaData.getGatewayInfo()).hasSize(1);
        assertThat(metaData.getGatewayInfo().get(0).getGatewayId()).isEqualTo("00800000a0004b95");
        assertThat(metaData.getGatewayInfo().get(0).getSnr()).isEqualTo(8);
        assertThat(metaData.getGatewayInfo().get(0).getRssi()).isEqualTo(-35);
        assertThat(metaData.getGatewayInfo().get(0).getChannel()).isEqualTo(2);

    }
}
