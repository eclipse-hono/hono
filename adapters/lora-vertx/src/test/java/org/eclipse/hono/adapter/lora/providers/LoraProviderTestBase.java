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

import java.nio.charset.StandardCharsets;

import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.UplinkLoraMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

/**
 * Base class for implementing tests for {@link LoraProvider} implementations.
 *
 * @param <T> The type of provider to test.
 */
public abstract class LoraProviderTestBase<T extends LoraProvider> {

    /**
     * The provider under test.
     */
    protected T provider;

    /**
     * The buffer containing the uplink message to use for testing.
     */
    protected Buffer uplinkMessageBuffer;

    /**
     * Creates a new instance of the provider under test.
     *
     * @return The instance to run tests against.
     */
    protected abstract T newProvider();

    /**
     * Sets up the fixture.
     *
     * @throws Exception if the example message file(s) cannot be read.
     */
    @BeforeEach
    public void setUp() throws Exception {
        provider = newProvider();
        uplinkMessageBuffer = LoraTestUtil.loadTestFile(provider.getProviderName(), LoraMessageType.UPLINK);
    }

    /**
     * Verifies that common properties are parsed correctly from the lora message.
     */
    @Test
    public void testGetMessageParsesCommonUplinkMessageProperties() {

        final UplinkLoraMessage loraMessage = (UplinkLoraMessage) provider.getMessage(uplinkMessageBuffer);

        assertThat(loraMessage.getDevEUIAsString()).isEqualTo("0102030405060708");
        assertThat(loraMessage.getPayload().getBytes()).isEqualTo("bumlux".getBytes(StandardCharsets.UTF_8));
    }
}
