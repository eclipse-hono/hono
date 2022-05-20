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
     * Constructor for tests.
     *
     * @param memoryRequiredToStart The minimum amount of memory that the adapter requires to run in bytes.
     * @param memoryRequiredPerConnection The amount of memory required for each connection in bytes.
     * @param memoryAvailableForLiveDataSet The amount of memory that can be used for the live data-set. This
     *                                      is the total amount of memory available to the JVM minus the head room
     *                                      required by the GC.
     */
    MemoryBasedConnectionLimitStrategy(
            final long memoryRequiredToStart,
            final long memoryRequiredPerConnection,
            final long memoryAvailableForLiveDataSet) {
        this.memoryRequiredToStart = memoryRequiredToStart;
        this.memoryRequiredPerConnection = memoryRequiredPerConnection;
        this.maxMemory = memoryAvailableForLiveDataSet;
    }

    /**
     * Creates an instance that calculates the recommended limit based on the memory available to the JVM and
     * the amount of memory required by the protocol adapter.
     *
     * @param memoryRequiredToStart The minimum amount of memory that the adapter requires to run in bytes.
     * @param memoryRequiredPerConnection The amount of memory required for each connection in bytes.
     * @param gcHeapPercentage The share of heap memory that should not be used by the live-data set but should be
     *                         left to be used by the garbage collector.
     * @return The instance.
     * @throws IllegalArgumentException if the GC heap percentage is &lt; 0 or &gt; 100.
     */
    public static MemoryBasedConnectionLimitStrategy forParams(
            final long memoryRequiredToStart,
            final long memoryRequiredPerConnection,
            final int gcHeapPercentage) {

        if (gcHeapPercentage < 0 || gcHeapPercentage > 100) {
            throw new IllegalArgumentException("GC heap percentage must be an integer in the range [0,100]");
        }

        final long memoryAvailableForLiveDataSet = Runtime.getRuntime().maxMemory() * (100 - gcHeapPercentage) / 100;
        return new MemoryBasedConnectionLimitStrategy(
                memoryRequiredToStart,
                memoryRequiredPerConnection,
                memoryAvailableForLiveDataSet);
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
        return String.format("max. memory avail. for live data-set: %dMB, memory required to start: %dMB",
                maxMemory / 1_000_000, memoryRequiredToStart / 1_000_000);
    }

}
