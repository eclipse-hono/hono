/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.metric;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.GaugeService;

import java.util.Objects;

/**
 * A metrics collector that is based on Spring Boot Actuator and Dropwizard metrics.
 */
abstract public class DropwizardBasedMetrics implements Metrics {

    /**
     * Special prefixes used by spring boot actuator together with dropwizard metrics.
     *
     * @see <a href=
     *      "https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-metrics.html#production-ready-dropwizard-metrics">Spring
     *      Boot</a>
     */
    protected static final String METER_PREFIX = "meter.";
    protected static final String TIMER_PREFIX = "timer.";
    protected static final String HISTOGRAM_PREFIX = "histogram.";

    /** metric parts for messages - usable for AMQP, MQTT, etc. */
    protected static final String MESSAGES = ".messages.";
    protected static final String PROCESSED = ".processed";
    protected static final String DISCARDED = ".discarded";
    protected static final String UNDELIVERABLE = ".undeliverable";
    protected static final String CONNECTIONS = ".connections.";
    protected static final String UNAUTHENTICATED_CONNECTIONS = ".unauthenticatedConnections.";
    protected static final String COMMANDS = ".commands";

    protected static final String PAYLOAD = ".payload.";

    protected GaugeService gaugeService = NullGaugeService.getInstance();
    protected CounterService counterService = NullCounterService.getInstance();

    /**
     * It is needed to set the specific service prefix; if no config is given it is not needed and will never be used.
     *
     * @param metricConfig The metrics config
     */
    @Autowired(required = false)
    public final void setMetricConfig(final MetricConfig metricConfig) {
        metricConfig.setPrefix(getPrefix());
    }

    /**
     * Deriving classes need to provide a prefix to scope the metrics of the service.
     *
     * @return The Prefix
     */
    protected abstract String getPrefix();

    /**
     * Sets the service to use for managing gauges.
     * <p>
     * Spring Boot will inject a concrete implementation that is available on the class path.
     *
     * @param gaugeService The gauge service.
     */
    @Autowired(required = false)
    public final void setGaugeService(final GaugeService gaugeService) {
        this.gaugeService = gaugeService;
    }

    /**
     * Sets the service to use for managing counters.
     * <p>
     * Spring Boot will inject a concrete implementation that is available on the class path.
     *
     * @param counterService The counter service.
     */
    @Autowired(required = false)
    public final void setCounterService(final CounterService counterService) {
        this.counterService = counterService;
    }

    /**
     * Replaces '/' with '.' to transform e.g. <code>telemetry/DEFAULT_TENANT</code> to
     * <code>telemetry.DEFAULT_TENANT</code>
     *
     * @param address The address with slashes to transform in an address with points
     * @return The address with points
     */
    protected final String normalizeAddress(final String address) {
        Objects.requireNonNull(address);
        return address.replace('/', '.');
    }

    /**
     * Merge the given address parts as a full string, separated by '.'.
     *
     * @param parts The address parts
     * @return The full address, separated by points
     */
    protected final String mergeAsMetric(final String... parts) {
        return String.join(".", parts);
    }

    /**
     * Increment the number of processes messages by one.
     *
     * @param resourceId The ID of the resource to track.
     * @param tenantId The tenant this resource belongs to.
     */
    @Override
    public final void incrementProcessedMessages(final String resourceId, final String tenantId) {
        counterService.increment(METER_PREFIX + getPrefix() + MESSAGES + mergeAsMetric(resourceId, tenantId) + PROCESSED);
    }

    /**
     * Increment the number of undeliverable messages by one.
     *
     * @param resourceId The ID of the resource to track.
     * @param tenantId The tenant this resource belongs to.
     */
    @Override
    public final void incrementUndeliverableMessages(final String resourceId, final String tenantId) {
        counterService.increment(getPrefix() + MESSAGES + mergeAsMetric(resourceId, tenantId) + UNDELIVERABLE);
    }

    /**
     * Increment the counter for the number of processed bytes.
     *
     * @param resourceId The ID of the resource to track.
     * @param tenantId The tenant this resource belongs to.
     * @param payloadSize The size of the payload in bytes.
     */
    @Override
    public final void incrementProcessedPayload(final String resourceId, final String tenantId, final long payloadSize) {
        if (payloadSize < 0) {
            // A negative size would mess up the metrics
            return;
        }
        counterService
                .increment(METER_PREFIX + getPrefix() + PAYLOAD + mergeAsMetric(resourceId, tenantId) + PROCESSED);
    }

}
