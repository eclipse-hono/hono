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

/**
 * Configuration properties for the Kafka client metrics support.
 */
public class KafkaMetricsConfig {

    private boolean enabled = true;
    private boolean useDefaultMetrics = true;
    private List<String> metricsPrefixes = MicrometerKafkaClientMetricsSupport.DEFAULT_METRICS_PREFIXES;

    /**
     * Gets whether metrics support is enabled.
     *
     * @return {@code true} if metrics support is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether metrics support is enabled.
     *
     * @param enabled  {@code true} if metrics support is enabled.
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets whether the default set of Kafka client metrics should be used.
     *
     * @return {@code true} if default metrics should be used.
     */
    public boolean isUseDefaultMetrics() {
        return useDefaultMetrics;
    }

    /**
     * Sets whether the default set of Kafka client metrics should be used.
     *
     * @param useDefaultMetrics {@code true} if default metrics should be used.
     */
    public void setUseDefaultMetrics(final boolean useDefaultMetrics) {
        this.useDefaultMetrics = useDefaultMetrics;
    }

    /**
     * Gets the list of prefixes of the metrics to be reported for Kafka clients.
     * <p>
     * Metric names have the form <em>kafka.[metric group].[metric name]</em>.
     * The metric group is the name of the Kafka {@link org.apache.kafka.common.MetricName} group minus the
     * "-metrics" suffix. E.g. for a metric with an MBean name containing "kafka.consumer:type=consumer-fetch-manager-metrics",
     * the group is "consumer-fetch-manager".
     * <p>
     * If {@link #isUseDefaultMetrics()} is {@code true}, the actually reported list of metrics will also
     * include the default metrics.
     *
     * @return The list of metric prefixes.
     */
    public List<String> getMetricsPrefixes() {
        return metricsPrefixes;
    }

    /**
     * Sets the list of prefixes of the metrics to be reported for Kafka clients.
     * <p>
     * Metric names have the form <em>kafka.[metric group].[metric name]</em>.
     * The metric group is the name of the Kafka {@link org.apache.kafka.common.MetricName} group minus the
     * "-metrics" suffix. E.g. for a metric with an MBean name containing "kafka.consumer:type=consumer-fetch-manager-metrics",
     * the group is "consumer-fetch-manager".
     *
     * @param metricsPrefixes The list of metric prefixes.
     */
    public void setMetricsPrefixes(final List<String> metricsPrefixes) {
        this.metricsPrefixes = metricsPrefixes;
    }
}
