/*******************************************************************************
 * Copyright (c) 2019, 2023 Contributors to the Eclipse Foundation
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

import java.io.IOException;

import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;
import org.eclipse.hono.adapter.lora.UplinkLoraMessage;
import org.junit.jupiter.api.Test;

import io.vertx.ext.web.RoutingContext;

/**
 * Verifies behavior of {@link ThingsNetworkProvider}.
 */
public class ThingsNetworkProviderTest extends LoraProviderTestBase<ThingsNetworkProvider> {


    /**
     * {@inheritDoc}
     */
    @Override
    protected ThingsNetworkProvider newProvider() {
        return new ThingsNetworkProvider();
    }

    /**
     * Test an uplink message with a null payload.
     *
     * @throws IOException If the file containing the example message could not be loaded.
     */
    @Test
    public void testGetMessageParsesUplinkMessagePropertiesWithNullPayload() throws IOException {

        final RoutingContext requestContext = getRequestContext(LoraMessageType.UPLINK, "with-null-payload");
        final UplinkLoraMessage loraMessage = (UplinkLoraMessage) provider.getMessage(requestContext);

        assertThat(loraMessage.getDevEUIAsString()).isEqualTo("01020304050607AB");
        assertThat(loraMessage.getPayload()).isNotNull();
        assertThat(loraMessage.getPayload().length()).isEqualTo(0);
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
