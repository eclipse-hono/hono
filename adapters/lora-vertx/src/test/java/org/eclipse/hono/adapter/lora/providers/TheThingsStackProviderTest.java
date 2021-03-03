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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.hono.adapter.lora.LoraMetaData;
import org.eclipse.hono.adapter.lora.UplinkLoraMessage;

/**
 * Verifies behavior of {@link TheThingsStackProvider}.
 */
public class TheThingsStackProviderTest extends LoraProviderTestBase<TheThingsStackProvider> {


    /**
     * {@inheritDoc}
     */
    @Override
    protected TheThingsStackProvider newProvider() {
        return new TheThingsStackProvider();
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
        assertThat(metaData.getFrequency()).isEqualTo(868.3);
        assertThat(metaData.getCodingRate()).isEqualTo("4/6");
        assertThat(metaData.getLocation().getLatitude()).isEqualTo(37.97155556731436);
        assertThat(metaData.getLocation().getLongitude()).isEqualTo(23.72678801175413);
        assertThat(metaData.getLocation().getAltitude()).isEqualTo(10);

        assertThat(metaData.getGatewayInfo()).hasSize(1);
        assertThat(metaData.getGatewayInfo().get(0).getGatewayId()).isEqualTo("0203040506070809");
        assertThat(metaData.getGatewayInfo().get(0).getSnr()).isEqualTo(5);
        assertThat(metaData.getGatewayInfo().get(0).getRssi()).isEqualTo(-35);
    }
}
