package org.eclipse.hono.application;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

}
