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
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;

/**
 * Verifies the behavior of {@link LiveObjectsProvider}.
 */
@ExtendWith(VertxExtension.class)
public class LiveObjectsProviderTest extends LoraProviderTestBase<LiveObjectsProvider> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected LiveObjectsProvider newProvider() {
        return new LiveObjectsProvider();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void assertMetaDataForUplinkMessage(final UplinkLoraMessage loraMessage) {

        final LoraMetaData metaData = loraMessage.getMetaData();
        assertThat(metaData.getFrameCount()).isEqualTo(27517);
        assertThat(metaData.getFrequency()).isEqualTo(868.5);
        assertThat(metaData.getFunctionPort()).isEqualTo(10);
        assertThat(metaData.getSpreadingFactor()).isEqualTo(10);

        assertThat(metaData.getLocation().getLongitude()).isEqualTo(2.526197);
        assertThat(metaData.getLocation().getLatitude()).isEqualTo(48.83361);
        assertThat(metaData.getLocation().getAltitude()).isEqualTo(0);
    }
}
