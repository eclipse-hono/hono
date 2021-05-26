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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class calculates the recommended limit of concurrent connections based on the runtime's memory.
 *
 */
public class MemoryBasedConnectionLimitStrategy implements ConnectionLimitStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryBasedConnectionLimitStrategy.class);

    private final long memoryRequiredToStart;
    private final long memoryRequiredPerConnection;
    private final long maxMemory;

    /**
     * Creates an instance that calculates the recommended limit dependent on the runtime's memory and the amount of
     * memory required by the protocol adapter.
     *
     * @param memoryRequiredToStart The minimum amount of memory that the adapter requires to run in bytes.
     * @param memoryRequiredPerConnection The amount of memory required for each connection in bytes.
     */
    public MemoryBasedConnectionLimitStrategy(final long memoryRequiredToStart, final long memoryRequiredPerConnection) {
        this(memoryRequiredToStart, memoryRequiredPerConnection, Runtime.getRuntime().maxMemory());
    }

    /**
     * Constructor for tests.
     *
     * @param maxMemory The amount of memory to test against.
     * @param memoryRequiredToStart The minimum amount of memory that the adapter requires to run in bytes.
     * @param memoryRequiredPerConnection The amount of memory required for each connection in bytes.
     */
    MemoryBasedConnectionLimitStrategy(final long memoryRequiredToStart, final long memoryRequiredPerConnection,
            final long maxMemory) {
        this.memoryRequiredToStart = memoryRequiredToStart;
        this.memoryRequiredPerConnection = memoryRequiredPerConnection;
        this.maxMemory = maxMemory;
    }

    /**
     * Returns a recommended limit of concurrent connections for the given maximum amount of memory.
     *
     * @return The recommended maximum connection limit between 0 and {@link Integer#MAX_VALUE}.
     */
    @Override
    public int getRecommendedLimit() {

        final long recommendedLimit = (maxMemory - memoryRequiredToStart) / memoryRequiredPerConnection;

        if (recommendedLimit <= 0) {
            LOG.warn("Not enough memory. It is recommended to provide more than {} MB (currently {}).",
                    (memoryRequiredToStart + memoryRequiredPerConnection) / 1_000_000, getResourcesDescription());
            return 0;
        } else if (recommendedLimit > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        } else {
            return (int) recommendedLimit;
        }
    }

    @Override
    public String getResourcesDescription() {
        return String.format("max. available memory: %dMB, memory required to start: %dMB",
                maxMemory / 1_000_000, memoryRequiredToStart / 1_000_000);
    }

}
