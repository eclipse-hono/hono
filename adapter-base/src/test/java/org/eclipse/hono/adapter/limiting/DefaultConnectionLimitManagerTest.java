/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.limiting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;

import org.eclipse.hono.adapter.ProtocolAdapterProperties;
import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link DefaultConnectionLimitManager}.
 */
public class DefaultConnectionLimitManagerTest {

    private ConnectionLimitStrategy strategy = mock(ConnectionLimitStrategy.class);

    /**
     * Verifies that the connection limit is exceeded when it equals the number of connections.
     */
    @Test
    public void testLimitIsEqual() {

        // GIVEN a connection limit of 1
        final ProtocolAdapterProperties config = new ProtocolAdapterProperties();
        config.setMaxConnections(1);

        // WHEN there is one connection
        final Supplier<Integer> currentConnections = () -> 1;

        // THEN the limit is exceeded
        assertTrue(new DefaultConnectionLimitManager(strategy, currentConnections, config).isLimitExceeded());
    }

    /**
     * Verifies that the connection limit is exceeded when it is lower than the number of connections.
     */
    @Test
    public void testLimitIsLower() {
        // GIVEN a connection limit of 1
        final ProtocolAdapterProperties config = new ProtocolAdapterProperties();
        config.setMaxConnections(1);

        // WHEN there are 2 connections
        final Supplier<Integer> currentConnections = () -> 2;

        // THEN the limit is exceeded
        assertTrue(new DefaultConnectionLimitManager(strategy, currentConnections, config).isLimitExceeded());
    }

    /**
     * Verifies that the connection limit is not exceeded when it is higher than the number of connections.
     */
    @Test
    public void testLimitIsHigher() {

        // GIVEN a connection limit of 2
        final ProtocolAdapterProperties config = new ProtocolAdapterProperties();
        config.setMaxConnections(2);

        // WHEN there is one connection
        final Supplier<Integer> currentConnections = () -> 1;

        // THEN the limit is not exceeded
        assertFalse(new DefaultConnectionLimitManager(strategy, currentConnections, config).isLimitExceeded());
    }

    /**
     * Verifies that the recommended value is used as connection limit if no limit has been configured.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testAutoconfigIfNotConfigured() {

        final Supplier<Integer> currentConnections = mock(Supplier.class);

        // GIVEN a DefaultConnectionLimitManager with no limit set and the recommended limit is 2
        final ProtocolAdapterProperties config = new ProtocolAdapterProperties();
        when(strategy.getRecommendedLimit()).thenReturn(2);
        final ConnectionLimitManager connectionLimitManager = new DefaultConnectionLimitManager(strategy, currentConnections,
                config);

        // WHEN there is one connection
        when(currentConnections.get()).thenReturn(1);
        // THEN the limit is not exceeded
        assertFalse(connectionLimitManager.isLimitExceeded());

        // WHEN there are 2 connections
        when(currentConnections.get()).thenReturn(2);
        // THEN the limit is exceeded
        assertTrue(connectionLimitManager.isLimitExceeded());
    }

    /**
     * Verifies that the recommended value is used as connection limit if no config is provided.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testAutoconfigIfConfigIsNull() {

        final Supplier<Integer> currentConnections = mock(Supplier.class);

        // GIVEN a DefaultConnectionLimitManager with no config and the recommended limit is 2
        final ProtocolAdapterProperties config = null;
        when(strategy.getRecommendedLimit()).thenReturn(2);
        final ConnectionLimitManager connectionLimitManager = new DefaultConnectionLimitManager(strategy, currentConnections,
                config);

        // WHEN there is one connection
        when(currentConnections.get()).thenReturn(1);
        // THEN the limit is not exceeded
        assertFalse(connectionLimitManager.isLimitExceeded());

        // WHEN there are 2 connections
        when(currentConnections.get()).thenReturn(2);
        // THEN the limit is exceeded
        assertTrue(connectionLimitManager.isLimitExceeded());
    }
}
