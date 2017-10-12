/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.metric;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.GaugeService;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
abstract public class Metrics {

    /**
     * special prefixes used by spring boot actuator together with dropwizard metrics
     * @see <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-metrics.html#production-ready-dropwizard-metrics">Spring Boot</a>
     */
    protected static String METER_PREFIX     = "meter.";
    protected static String TIMER_PREFIX     = "timer.";
    protected static String HISTOGRAM_PREFIX = "histogram.";

    /** metric parts for messages - useable for AMQP, MQTT, etc. */
    protected static final String MESSAGES      = ".messages.";
    protected static final String PROCESSED     = ".processed";
    protected static final String DISCARDED     = ".discarded";
    protected static final String UNDELIVERABLE = ".undeliverable";

    protected GaugeService   gaugeService   = NullGaugeService.getInstance();
    protected CounterService counterService = NullCounterService.getInstance();

    /**
     * It is needed to set the specific service prefix; if no config is given it is not needed and will never be used
     *
     * @param metricConfig The metrics config
     */
    @Autowired(required = false)
    public void setMetricConfig(final MetricConfig metricConfig) {
        metricConfig.setPrefix(getPrefix());
    }

    /**
     * Deriving classes need to provide a prefix to scope the metrics of the service
     *
     * @return The Prefix
     */
    protected abstract String getPrefix();

    /**
     * Sets the spring boot gauge service, will be based on Dropwizard Metrics, if in classpath.
     *
     * @param gaugeService The gauge service.
     */
    @Autowired
    public final void setGaugeService(final GaugeService gaugeService) {
        this.gaugeService = gaugeService;
    }

    /**
     * Sets the spring boot counter service, will be based on Dropwizard Metrics, if in classpath.
     *
     * @param counterService The counter service.
     */
    @Autowired
    public final void setCounterService(final CounterService counterService) {
        this.counterService = counterService;
    }

    protected String normalizeAddress(final String address) {
        Objects.requireNonNull(address);
        return address.replace('/', '.');
    }

    protected String mergeAsMetric(final String... parts ) {
        return String.join(".",parts);
    }

}
