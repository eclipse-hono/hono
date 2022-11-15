/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
 * Verifies behavior of {@link ChirpStackProvider}.
 */
public class ChirpStackV4ProviderTest extends LoraProviderTestBase<ChirpStackV4Provider> {


    /**
     * {@inheritDoc}
     */
    @Override
    protected ChirpStackV4Provider newProvider() {
        return new ChirpStackV4Provider();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void assertMetaDataForUplinkMessage(final UplinkLoraMessage loraMessage) {

        final LoraMetaData metaData = loraMessage.getMetaData();
        assertThat(metaData.getFunctionPort()).isEqualTo(5);
        assertThat(metaData.getFrameCount()).isEqualTo(10);
        assertThat(metaData.getSpreadingFactor()).isEqualTo(11);
        assertThat(metaData.getBandwidth()).isEqualTo(125);
        assertThat(metaData.getFrequency()).isEqualTo(868.1);
        assertThat(metaData.getCodingRate()).isEqualTo("4/5");

        assertThat(metaData.getGatewayInfo()).hasSize(1);
        assertThat(metaData.getGatewayInfo().get(0).getGatewayId()).isEqualTo("0303030303030303");
        assertThat(metaData.getGatewayInfo().get(0).getSnr()).isEqualTo(9.0);
        assertThat(metaData.getGatewayInfo().get(0).getRssi()).isEqualTo(-48);
        assertThat(metaData.getGatewayInfo().get(0).getLocation().getLongitude()).isEqualTo(4.9144401);
        assertThat(metaData.getGatewayInfo().get(0).getLocation().getLatitude()).isEqualTo(52.3740364);
        assertThat(metaData.getGatewayInfo().get(0).getLocation().getAltitude()).isEqualTo(10.5);
    }
}
