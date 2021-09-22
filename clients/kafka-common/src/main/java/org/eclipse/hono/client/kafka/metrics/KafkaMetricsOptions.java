/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.kafka.metrics;

import java.util.List;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Options for configuring the Kafka client metrics support.
 *
 */
@ConfigMapping(prefix = "hono.kafka.metrics", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface KafkaMetricsOptions {

    /**
     * Gets whether metrics support is enabled.
     *
     * @return {@code true} if metrics support is enabled.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Gets whether the default set of Kafka client metrics should be used.
     *
     * @return {@code true} if default metrics should be used.
     */
    @WithDefault("true")
    boolean useDefaultMetrics();

    /**
     * Gets the list of prefixes of the metrics to be reported for Kafka clients.
     * <p>
     * Metric names have the form <em>kafka.[metric group].[metric name]</em>.
     * The metric group is the name of the Kafka {@link org.apache.kafka.common.MetricName} group minus the
     * "-metrics" suffix. E.g. for a metric with an MBean name containing "kafka.consumer:type=consumer-fetch-manager-metrics",
     * the group is "consumer-fetch-manager".
     * <p>
     * If {@link #useDefaultMetrics()} is {@code true}, the actually reported list of metrics will also
     * include the default metrics.
     *
     * @return The list of metric prefixes.
     */
    Optional<List<String>> metricsPrefixes();
}
