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
 * Verifies behavior of {@link ProximusProvider}.
 */
public class ProximusProviderTest extends LoraProviderTestBase<ProximusProvider> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected ProximusProvider newProvider() {
        return new ProximusProvider();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void assertMetaDataForUplinkMessage(final UplinkLoraMessage loraMessage) {

        final LoraMetaData metaData = loraMessage.getMetaData();
        assertThat(metaData.getFrameCount()).isEqualTo(23);
        assertThat(metaData.getFunctionPort()).isEqualTo(6);
        assertThat(metaData.getLocation()).isNull();
        assertThat(metaData.getSpreadingFactor()).isEqualTo(11);
        assertThat(metaData.getGatewayInfo()).hasSize(1);
        assertThat(metaData.getGatewayInfo().get(0).getSnr()).isEqualTo(2.0);
        assertThat(metaData.getGatewayInfo().get(0).getRssi()).isEqualTo(-54);
    }
}
