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
package org.eclipse.hono.adapter.limiting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link MemoryBasedConnectionLimitStrategy}.
 */
public class MemoryBasedConnectionLimitStrategyTest {

    private static final int MINIMAL_MEMORY = 100_000_000;
    private static final int MEMORY_PER_CONNECTION = 20_000;

    /**
     * Verifies that the recommended connection limit is not negative.
     */
    @Test
    public void testGetRecommendedConnectionLimitLowerLimit() {
        assertEquals(0, new MemoryBasedConnectionLimitStrategy(MINIMAL_MEMORY, MEMORY_PER_CONNECTION,
                MINIMAL_MEMORY * -2).getRecommendedLimit());
    }

    /**
     * Verifies that when the amount necessary for one connection is added to the minimal memory, the recommended
     * connection limit is incremented by 1.
     */
    @Test
    public void testGetRecommendedConnectionLimit() {

        final int requiredFor1Connection = MINIMAL_MEMORY + MEMORY_PER_CONNECTION;

        assertEquals(0, new MemoryBasedConnectionLimitStrategy(MINIMAL_MEMORY, MEMORY_PER_CONNECTION,
                requiredFor1Connection - 1).getRecommendedLimit());

        assertEquals(1, new MemoryBasedConnectionLimitStrategy(MINIMAL_MEMORY, MEMORY_PER_CONNECTION,
                requiredFor1Connection).getRecommendedLimit());

        final int requiredFor2Connections = requiredFor1Connection + MEMORY_PER_CONNECTION;

        assertEquals(1, new MemoryBasedConnectionLimitStrategy(MINIMAL_MEMORY, MEMORY_PER_CONNECTION,
                requiredFor2Connections - 1).getRecommendedLimit());

        assertEquals(2, new MemoryBasedConnectionLimitStrategy(MINIMAL_MEMORY, MEMORY_PER_CONNECTION,
                requiredFor2Connections).getRecommendedLimit());

    }

    /**
     * Verifies that the recommended connection limit does not overflow.
     */
    @Test
    public void testGetRecommendedConnectionLimitUpperLimit() {
        final long biggerThanMaxInt = Long.MAX_VALUE;
        assertEquals(Integer.MAX_VALUE, new MemoryBasedConnectionLimitStrategy(MINIMAL_MEMORY, MEMORY_PER_CONNECTION,
                biggerThanMaxInt).getRecommendedLimit());
    }
}
