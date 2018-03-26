/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;

import io.vertx.core.metrics.MetricsOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;

/**
 * Spring bean definitions required by the metrics reporters.
 */
@Configuration
public class MetricConfig {

    private static final String HONO    = "hono";
    private static final String UNKNOWN = "unknown";

    private static final Logger LOG = LoggerFactory.getLogger(MetricConfig.class);

    private String prefix = HONO;

    private final MetricRegistry metricRegistry;

    /**
     * Create a new metric configuration.
     * 
     * @param metricRegistry The metric registry to use.
     */
    public MetricConfig(final MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    /**
     * Set the prefix to scope values of each service.
     *
     * @param prefix The prefix
     */
    public void setPrefix(final String prefix) {
        this.prefix = prefix;
    }

    /**
     * Gets a gauge instance for the JVM memory.
     * 
     * @return A gauge instance for the JVM memory.
     */
    @Bean
    @ConditionalOnProperty(prefix = "hono.metric.jvm", name = "memory", havingValue = "true")
    public MemoryUsageGaugeSet jvmMetricsMemory() {
        LOG.info("metrics - jvm/memory activated");
        return metricRegistry.register(prefix + ".jvm.memory", new MemoryUsageGaugeSet());
    }

    /**
     * Gets a gauge instance for the JVM thread states.
     * 
     * @return A gauge instance for the JVM thread states.
     */
    @Bean
    @ConditionalOnProperty(prefix = "hono.metric.jvm", name = "thread", havingValue = "true")
    public ThreadStatesGaugeSet jvmMetricsThreads() {
        LOG.info("metrics - jvm/threads activated");
        return metricRegistry.register(prefix + ".jvm.thread", new ThreadStatesGaugeSet());
    }

    /**
     * Gets the vertx metrics options bean.
     * 
     * @return A new metrics options instance for vertx.
     */
    @Bean
    @ConditionalOnProperty(prefix = "hono.metric", name = "vertx", havingValue = "true")
    public MetricsOptions vertxMetricsOptions() {
        LOG.info("metrics - vertx activated");
        SharedMetricRegistries.add(HONO, metricRegistry);
        SharedMetricRegistries.setDefault(HONO, metricRegistry);
        return new DropwizardMetricsOptions().setEnabled(true).setRegistryName(HONO)
                .setBaseName(prefix + ".vertx").setJmxEnabled(true);
    }

    /**
     * Gets a new instance for a console reporter.
     * 
     * @param period The period to update the state on console in milliseconds.
     * @return The new console reporter instance.
     */
    @Bean
    @ConditionalOnProperty(prefix = "hono.metric.reporter.console", name = "active", havingValue = "true")
    public ConsoleReporter consoleMetricReporter(
            @Value("${hono.metric.reporter.console.period:5000}") final Long period) {
        LOG.info("metrics - console reporter activated");
        final ConsoleReporter consoleReporter = ConsoleReporter.forRegistry(metricRegistry)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build();
        consoleReporter.start(period, TimeUnit.MILLISECONDS);
        return consoleReporter;
    }

    /**
     * Gets a new instance for a graphite reporter.
     * 
     * @param period The period to publish the state in milliseconds.
     * @param host The host to report to.
     * @param port The port to report to.
     * @param prefix The prefix used when reporting.
     * 
     * @return The new graphite reporter instance.
     */
    @Bean
    @ConditionalOnProperty(prefix = "hono.metric.reporter.graphite", name = "active", havingValue = "true")
    public GraphiteReporter graphiteReporter(
            @Value("${hono.metric.reporter.graphite.period:5000}") final Long period,
            @Value("${hono.metric.reporter.graphite.host:localhost}") final String host,
            @Value("${hono.metric.reporter.graphite.port:2003}") final Integer port,
            @Value("${hono.metric.reporter.graphite.prefix:}") final String prefix) {
        final Graphite graphite = new Graphite(new InetSocketAddress(host, port));
        String processedPrefix = prefix;
        if (processedPrefix.isEmpty()) {
            try {
                processedPrefix = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException exception) {
                processedPrefix = UNKNOWN;
            }
        }
        LOG.info("metrics - graphite reporter activated: {}:{}  prefix: {}  period: {}", host, port, processedPrefix, period);
        final GraphiteReporter reporter = GraphiteReporter.forRegistry(metricRegistry)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .prefixedWith(processedPrefix)
                .build(graphite);
        reporter.start(period, TimeUnit.MILLISECONDS);
        return reporter;
    }

}
