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
 * Verifies behavior of {@link ActilityProvider}.
 */
public class ActilityProviderTest extends LoraProviderTestBase<ActilityProvider> {


    /**
     * {@inheritDoc}
     */
    @Override
    protected ActilityProvider newProvider() {
        return new ActilityProvider();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void assertMetaDataForUplinkMessage(final UplinkLoraMessage loraMessage) {

        final LoraMetaData data = loraMessage.getMetaData();

        assertThat(data.getGatewayInfo()).hasSize(2);
        assertThat(data.getGatewayInfo().get(0).getGatewayId()).isEqualTo("18035559");
        assertThat(data.getGatewayInfo().get(0).getRssi()).isEqualTo(-48);
        assertThat(data.getGatewayInfo().get(0).getSnr()).isEqualTo(3.0);
        assertThat(data.getGatewayInfo().get(1).getGatewayId()).isEqualTo("18035560");
        assertThat(data.getGatewayInfo().get(1).getRssi()).isEqualTo(-49);
        assertThat(data.getGatewayInfo().get(1).getSnr()).isEqualTo(4.0);
    }
}
