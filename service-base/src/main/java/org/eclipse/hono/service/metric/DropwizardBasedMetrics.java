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
     * Special prefixes used by Spring Boot Actuator together with Dropwizard metrics.
     *
     * @see <a href=
     *      "https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-metrics.html#production-ready-dropwizard-metrics">Spring
     *      Boot</a>
     */
    protected static final String METER_PREFIX = "meter";
    protected static final String TIMER_PREFIX = "timer";
    protected static final String HISTOGRAM_PREFIX = "histogram";

    /** metric parts for messages - usable for AMQP, MQTT, etc. */
    protected static final String MESSAGES = "messages";
    protected static final String PROCESSED = "processed";
    protected static final String DISCARDED = "discarded";
    protected static final String UNDELIVERABLE = "undeliverable";
    protected static final String CONNECTIONS = "connections";
    protected static final String COMMANDS = "commands";

    protected static final String PAYLOAD = "payload";

    /**
     * The service to use for reporting gauge based metrics.
     */
    protected GaugeService gaugeService = NullGaugeService.getInstance();
    /**
     * The service to use for reporting counter based metrics.
     */
    protected CounterService counterService = NullCounterService.getInstance();

    /**
     * It is needed to set the specific service prefix; if no config is given it is not needed and will never be used.
     *
     * @param metricConfig The metrics config
     */
    @Autowired(required = false)
    public final void setMetricConfig(final MetricConfig metricConfig) {
        metricConfig.setPrefix(getScope());
    }

    /**
     * Gets the scope in which the metrics are collected.
     * <p>
     * The value returned by this method is used in all names of metrics collected by this
     * class.
     * <p>
     * Subclasses should return a component specific value.
     *
     * @return The scope.
     */
    protected abstract String getScope();

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
    protected static final String normalizeAddress(final String address) {
        Objects.requireNonNull(address);
        return address.replace('/', '.');
    }

    /**
     * Merge the given address parts as a full string, separated by '.'.
     *
     * @param parts The address parts
     * @return The full address, separated by points
     */
    protected static final String mergeAsMetric(final String... parts) {
        return String.join(".", parts);
    }


    @Override
    public final void incrementConnections(final String tenantId) {
        counterService.increment(mergeAsMetric(getScope(), CONNECTIONS, "authenticated", tenantId));
    }

    @Override
    public final void decrementConnections(final String tenantId) {
        counterService.decrement(mergeAsMetric(getScope(), CONNECTIONS, "authenticated", tenantId));
    }

    @Override
    public final void incrementAnonymousConnections() {
        counterService.increment(mergeAsMetric(getScope(), CONNECTIONS, "anonymous"));
    }

    @Override
    public final void decrementAnonymousConnections() {
        counterService.decrement(mergeAsMetric(getScope(), CONNECTIONS, "anonymous"));
    }

    @Override
    public final void incrementProcessedMessages(final String resourceId, final String tenantId) {
        counterService.increment(mergeAsMetric(METER_PREFIX, getScope(), MESSAGES, resourceId, tenantId, PROCESSED));
    }

    @Override
    public final void incrementUndeliverableMessages(final String resourceId, final String tenantId) {
        counterService.increment(mergeAsMetric(getScope(), MESSAGES, resourceId, tenantId, UNDELIVERABLE));
    }

    @Override
    public final void incrementProcessedPayload(final String resourceId, final String tenantId, final long payloadSize) {
        if (payloadSize < 0) {
            // A negative size would mess up the metrics
            return;
        }
        counterService
                .increment(mergeAsMetric(METER_PREFIX, getScope(), PAYLOAD, resourceId, tenantId, PROCESSED));
    }

    @Override
    public final void incrementCommandDeliveredToDevice(final String tenantId) {
        counterService.increment(mergeAsMetric(METER_PREFIX, getScope(), COMMANDS, tenantId, "device", "delivered"));
    }

    @Override
    public final void incrementNoCommandReceivedAndTTDExpired(final String tenantId) {
        counterService.increment(mergeAsMetric(METER_PREFIX, getScope(), COMMANDS, tenantId, "ttd", "expired"));
    }

    @Override
    public final void incrementCommandResponseDeliveredToApplication(final String tenantId) {
        counterService.increment(mergeAsMetric(METER_PREFIX, getScope(), COMMANDS, tenantId, "response", "delivered"));
    }
}
