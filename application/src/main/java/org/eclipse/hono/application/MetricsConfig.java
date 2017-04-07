package org.eclipse.hono.application;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * Spring bean definitions required by the metrics reporters.
 */
@Configuration
public class MetricsConfig {

    protected final Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Bean
    @Autowired
    @ConditionalOnProperty(prefix = "hono.metric.jvm", name = "memory", havingValue = "true")
    public MemoryUsageGaugeSet jvmMetricsMemory(MetricRegistry metricRegistry) {
        return metricRegistry.register("hono.server.jvm.memory", new MemoryUsageGaugeSet());
    }

    @Bean
    @Autowired
    @ConditionalOnProperty(prefix = "hono.metric.jvm", name = "thread", havingValue = "true")
    public ThreadStatesGaugeSet jvmMetricsThreads(MetricRegistry metricRegistry) {
        return metricRegistry.register("hono.server.jvm.thread", new ThreadStatesGaugeSet());
    }

    @Bean
    @Autowired
    @ConditionalOnProperty(prefix = "hono.metric.reporter.console", name = "active", havingValue = "true")
    public ConsoleReporter consoleMetricReporter(MetricRegistry metricRegistry,
            @Value("${hono.metric.reporter.console.period:5000}") Long period) {
        ConsoleReporter consoleReporter = ConsoleReporter.forRegistry(metricRegistry)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build();
        consoleReporter.start(period, TimeUnit.MILLISECONDS);
        return consoleReporter;
    }

    @Bean
    @Autowired
    @ConditionalOnProperty(prefix = "hono.metric.reporter.graphite", name = "active", havingValue = "true")
    public GraphiteReporter graphiteReporter(MetricRegistry metricRegistry,
            @Value("${hono.metric.reporter.graphite.period:5000}") Long period,
            @Value("${hono.metric.reporter.graphite.host:localhost}") String host,
            @Value("${hono.metric.reporter.graphite.port:2003}") Integer port) {
        final Graphite graphite = new Graphite(new InetSocketAddress(host, port));
        String prefix;
        try {
            prefix = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            prefix = "unknown";
        }
        final GraphiteReporter reporter = GraphiteReporter.forRegistry(metricRegistry)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .prefixedWith(prefix)
                .build(graphite);
        reporter.start(period, TimeUnit.MILLISECONDS);
        return reporter;
    }
}
