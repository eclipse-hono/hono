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

import java.util.Objects;
import java.util.function.Supplier;

import org.eclipse.hono.adapter.ProtocolAdapterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces a limit of concurrent connections.
 * <p>
 * The limit can be configured in {@link ProtocolAdapterProperties#setMaxConnections(int)}.
 * If no value is configured explicitly, the limit is determined based on the given strategy.
 */
public class DefaultConnectionLimitManager implements ConnectionLimitManager {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultConnectionLimitManager.class);

    private final ConnectionLimitStrategy strategy;
    private final Supplier<Integer> currentConnections;
    private final int limit;

    /**
     * Creates an instance configured with a limiting strategy, the adapter's metrics to get the current number of
     * connections and the adapter's properties for the configured limit.
     * <p>
     * If the protocol adapter properties are null or no limit is configured, the recommended limit of the strategy will
     * be set as the limit.
     *
     * @param strategy The strategy to configure the connection limit.
     * @param currentConnections The supplier to invoke for getting the current number of connections.
     * @param config The configuration of the adapter or {@code null}.
     * @throws NullPointerException if strategy or currentConnections are {@code null}.
     * @throws ConnectionLimitAutoConfigException if no connection limit is configured and the auto-configuration
     *             calculates a limit of 0.
     */
    public DefaultConnectionLimitManager(final ConnectionLimitStrategy strategy, final Supplier<Integer> currentConnections,
            final ProtocolAdapterProperties config) {
        this.strategy = Objects.requireNonNull(strategy);
        this.currentConnections = Objects.requireNonNull(currentConnections);

        if (config == null || !config.isConnectionLimitConfigured()) {
            limit = autoconfigureConnectionLimit();
        } else {
            limit = checkConnectionLimit(config.getMaxConnections());
        }
    }

    private int autoconfigureConnectionLimit() {

        final int recommendedLimit = strategy.getRecommendedLimit();

        if (recommendedLimit == 0) {
            throw new ConnectionLimitAutoConfigException("The connection limit would be auto-configured to 0 (based on "
                    + strategy.getResourcesDescription() + "). To override this check, configure a connection limit.");
        }

        LOG.info("Setting connection limit to {} (based on {})", recommendedLimit, strategy.getResourcesDescription());

        return recommendedLimit;
    }

    private int checkConnectionLimit(final int configuredLimit) {
        final int recommendedLimit = strategy.getRecommendedLimit();

        if (configuredLimit > recommendedLimit) {
            LOG.warn("Configured connection limit {} is too high: Recommended is maximum {} (based on {})",
                    configuredLimit, recommendedLimit, strategy.getResourcesDescription());
        } else {
            LOG.debug("Configured connection limit: {}", configuredLimit);
        }

        return configuredLimit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLimitExceeded() {
        final boolean exceeded = currentConnections.get() >= limit;
        if (exceeded) {
            LOG.debug("Connection limit ({}) exceeded", limit);
        }
        return exceeded;
    }

}
