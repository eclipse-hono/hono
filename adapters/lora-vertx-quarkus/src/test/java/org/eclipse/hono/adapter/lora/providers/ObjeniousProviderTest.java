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
 * Verifies behavior of {@link ObjeniousProvider}.
 */
public class ObjeniousProviderTest extends LoraProviderTestBase<ObjeniousProvider> {


    /**
     * {@inheritDoc}
     */
    @Override
    protected ObjeniousProvider newProvider() {
        return new ObjeniousProvider();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void assertMetaDataForUplinkMessage(final UplinkLoraMessage loraMessage) {

        final LoraMetaData metaData = loraMessage.getMetaData();
        assertThat(metaData.getFrameCount()).isEqualTo(7);
        assertThat(metaData.getFunctionPort()).isEqualTo(1);
        assertThat(metaData.getLocation().getLatitude()).isEqualTo(53.108805);
        assertThat(metaData.getLocation().getLongitude()).isEqualTo(9.193430);
        assertThat(metaData.getSpreadingFactor()).isEqualTo(11);
        assertThat(metaData.getGatewayInfo()).hasSize(1);
        assertThat(metaData.getGatewayInfo().get(0).getSnr()).isEqualTo(1.0);
        assertThat(metaData.getGatewayInfo().get(0).getRssi()).isEqualTo(-103);
    }
}
