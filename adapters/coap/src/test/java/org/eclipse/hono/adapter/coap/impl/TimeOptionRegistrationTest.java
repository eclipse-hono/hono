/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.coap.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.eclipse.californium.core.coap.option.StandardOptionRegistry;
import org.eclipse.hono.adapter.coap.CoapAdapterProperties;
import org.eclipse.hono.adapter.coap.option.TimeOption;
import org.junit.jupiter.api.Test;

import io.vertx.core.Vertx;

/**
 * Tests verifying that {@link ConfigBasedCoapEndpointFactory} registers the time option.
 */
class TimeOptionRegistrationTest {

    /**
     * Verifies that more than one factory can be created, e.g. one per adapter verticle instance,
     * and that the time option is registered.
     */
    @Test
    void testFactoryCanBeCreatedMoreThanOnce() {
        final Vertx vertx = mock(Vertx.class);
        final CoapAdapterProperties properties = new CoapAdapterProperties();

        assertDoesNotThrow(() -> new ConfigBasedCoapEndpointFactory(vertx, properties));
        assertDoesNotThrow(() -> new ConfigBasedCoapEndpointFactory(vertx, properties));
        assertTrue(StandardOptionRegistry.getDefaultOptionRegistry().contains(TimeOption.DEFINITION));
    }
}
