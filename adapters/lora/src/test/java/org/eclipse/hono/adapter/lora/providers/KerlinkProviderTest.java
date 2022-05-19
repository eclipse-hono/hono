/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;

/**
 * Verifies the behavior of {@link KerlinkProvider}.
 */
@ExtendWith(VertxExtension.class)
public class KerlinkProviderTest extends LoraProviderTestBase<KerlinkProvider> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected KerlinkProvider newProvider() {
        return new KerlinkProvider();
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
        assertThat(metaData.getLocation().getLongitude()).isEqualTo(53.108805);
        assertThat(metaData.getLocation().getLatitude()).isEqualTo(9.193430);
        assertThat(metaData.getLocation().getAltitude()).isNull();
        assertThat(metaData.getGatewayInfo().get(0).getGatewayId()).isEqualTo("0101010101010101");
        assertThat(metaData.getGatewayInfo().get(0).getRssi()).isEqualTo(-84);
        assertThat(metaData.getGatewayInfo().get(0).getSnr()).isEqualTo(9.8);
        assertThat(metaData.getGatewayInfo().get(0).getChannel()).isEqualTo(3);
        assertThat(metaData.getGatewayInfo().get(0).getLocation().getLongitude()).isEqualTo(53.108804);
        assertThat(metaData.getGatewayInfo().get(0).getLocation().getLatitude()).isEqualTo(9.193431);
        assertThat(metaData.getGatewayInfo().get(0).getLocation().getAltitude()).isNull();
        assertThat(metaData.getGatewayInfo().get(1).getGatewayId()).isEqualTo("0202020202020202");
        assertThat(metaData.getGatewayInfo().get(1).getRssi()).isEqualTo(-116);
        assertThat(metaData.getGatewayInfo().get(1).getSnr()).isEqualTo(-3.2);
        assertThat(metaData.getGatewayInfo().get(1).getChannel()).isEqualTo(4);
        assertThat(metaData.getGatewayInfo().get(1).getLocation().getLongitude()).isEqualTo(53.108806);
        assertThat(metaData.getGatewayInfo().get(1).getLocation().getLatitude()).isEqualTo(9.193429);
        assertThat(metaData.getGatewayInfo().get(1).getLocation().getAltitude()).isNull();
    }
}
