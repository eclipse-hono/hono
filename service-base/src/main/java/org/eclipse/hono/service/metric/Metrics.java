/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */

package org.eclipse.hono.service.metric;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.GaugeService;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Base metrics collector.
 */
@Component
abstract public class Metrics {

    /**
     * Special prefixes used by spring boot actuator together with dropwizard metrics.
     * @see <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-metrics.html#production-ready-dropwizard-metrics">Spring Boot</a>
     */
    protected static final String METER_PREFIX     = "meter.";
    protected static final String TIMER_PREFIX     = "timer.";
    protected static final String HISTOGRAM_PREFIX = "histogram.";

    /** metric parts for messages - useable for AMQP, MQTT, etc. */
    protected static final String MESSAGES      = ".messages.";
    protected static final String PROCESSED     = ".processed";
    protected static final String DISCARDED     = ".discarded";
    protected static final String UNDELIVERABLE = ".undeliverable";
    protected static final String CONNECTIONS   = ".connections.";
    protected static final String UNAUTHENTICATED_CONNECTIONS   = ".unauthenticatedConnections.";

    protected GaugeService   gaugeService   = NullGaugeService.getInstance();
    protected CounterService counterService = NullCounterService.getInstance();

    /**
     * It is needed to set the specific service prefix; if no config is given it is not needed and will never be used.
     *
     * @param metricConfig The metrics config
     */
    @Autowired(required = false)
    public void setMetricConfig(final MetricConfig metricConfig) {
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
     * Replaces '/' with '.' to transform e.g. <code>telemetry/DEFAULT_TENANT</code> to <code>telemetry.DEFAULT_TENANT</code>
     *
     * @param address The address with slashes to transform in an address with points
     * @return The address with points
     */
    protected String normalizeAddress(final String address) {
        Objects.requireNonNull(address);
        return address.replace('/', '.');
    }

    /**
     * Merge the given address parts as a full string, separated by '.'.
     * @param parts The address parts
     * @return The full address, separated by points
     */
    protected String mergeAsMetric(final String... parts ) {
        return String.join(".",parts);
    }

}
