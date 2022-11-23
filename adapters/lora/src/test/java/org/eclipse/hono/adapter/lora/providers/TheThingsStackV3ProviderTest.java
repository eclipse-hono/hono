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
 * Verifies behavior of {@link TheThingsStackV3Provider}.
 */
public class TheThingsStackV3ProviderTest extends LoraProviderTestBase<TheThingsStackV3Provider> {


    /**
     * {@inheritDoc}
     */
    @Override
    protected TheThingsStackV3Provider newProvider() {
        return new TheThingsStackV3Provider();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void assertMetaDataForUplinkMessage(final UplinkLoraMessage loraMessage) {

        final LoraMetaData metaData = loraMessage.getMetaData();
        assertThat(metaData.getFunctionPort()).isEqualTo(1);
        assertThat(metaData.getFrameCount()).isEqualTo(9);
        assertThat(metaData.getSpreadingFactor()).isEqualTo(7);
        assertThat(metaData.getBandwidth()).isEqualTo(125);
        assertThat(metaData.getFrequency()).isEqualTo(868.1);
        assertThat(metaData.getCodingRate()).isEqualTo("4/5");

        assertThat(metaData.getGatewayInfo()).hasSize(1);
        assertThat(metaData.getGatewayInfo().get(0).getGatewayId()).isEqualTo("0203040506070809");
        assertThat(metaData.getGatewayInfo().get(0).getChannel()).isEqualTo(0);
        assertThat(metaData.getGatewayInfo().get(0).getSnr()).isEqualTo(5.0);
        assertThat(metaData.getGatewayInfo().get(0).getRssi()).isEqualTo(-25);
        assertThat(metaData.getGatewayInfo().get(0).getLocation().getLongitude()).isEqualTo(9.1934);
        assertThat(metaData.getGatewayInfo().get(0).getLocation().getLatitude()).isEqualTo(53.1088);
        assertThat(metaData.getGatewayInfo().get(0).getLocation().getAltitude()).isEqualTo(90.0);
    }
}
