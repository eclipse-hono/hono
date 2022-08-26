/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.lang.NonNull;

/**
 * Micrometer based implementation to provide support for registering Kafka clients from which metrics are fetched.
 */
public final class MicrometerKafkaClientMetricsSupport implements KafkaClientMetricsSupport {

    /**
     * Default list of Kafka client metrics to be reported.
     */
    public static final List<String> DEFAULT_METRICS_PREFIXES = List.of(
            "kafka.producer.topic.record.send.rate",
            "kafka.producer.topic.record.error.rate",
            "kafka.producer.node.request.rate",
            "kafka.producer.node.response.rate",
            "kafka.producer.node.request.latency.avg",
            "kafka.producer.node.outgoing.byte.rate",
            "kafka.producer.io.wait.time.ns.avg",
            "kafka.producer.batch.size.avg",
            "kafka.producer.produce.throttle.time.avg",
            "kafka.producer.produce.throttle.time.max",
            "kafka.consumer.fetch.manager.records.lag",
            "kafka.consumer.fetch.manager.records.lag.max",
            "kafka.consumer.fetch.manager.records.lead.min",
            "kafka.consumer.fetch.manager.bytes.consumed.rate",
            "kafka.consumer.fetch.manager.records.consumed.rate",
            "kafka.consumer.fetch.manager.fetch.rate",
            "kafka.consumer.fetch.manager.fetch.throttle.time.avg",
            "kafka.consumer.fetch.manager.fetch.throttle.time.max",
            "kafka.consumer.coordinator.rebalance.total",
            "kafka.consumer.coordinator.failed.rebalance.total",
            "kafka.consumer.coordinator.rebalance.latency.avg"
    );

    private static final Logger LOG = LoggerFactory.getLogger(MicrometerKafkaClientMetricsSupport.class);
    private static final String PREFIX_KAFKA = "kafka.";

    private final List<MeterRegistry> boundMeterRegistries = new ArrayList<>();
    private final Map<Producer<?, ?>, KafkaClientMetrics> producerMetricsMap = new HashMap<>();
    private final Map<Consumer<?, ?>, KafkaClientMetrics> consumerMetricsMap = new HashMap<>();
    private final boolean producerMetricsEnabled;
    private final boolean consumerMetricsEnabled;

    private final List<String> metricsPrefixesToUse;

    /**
     * Creates a new MicrometerKafkaClientMetricsSupport.
     *
     * @param useDefaultMetrics {@code true} if the default metrics should be used.
     * @param metricsPrefixes The list of prefixes for matching the metrics to be reported.
     *                        If useDefaultMetrics is {@code true}, the actually reported list of metrics will also
     *                        include the default metrics.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public MicrometerKafkaClientMetricsSupport(final boolean useDefaultMetrics, final List<String> metricsPrefixes) {
        Objects.requireNonNull(metricsPrefixes);

        metricsPrefixesToUse = new ArrayList<>(useDefaultMetrics ? DEFAULT_METRICS_PREFIXES : List.of());
        metricsPrefixes.stream()
                .map(String::trim)
                .filter(p -> p.startsWith(PREFIX_KAFKA)) // sanity check - only entries with kafka prefix are relevant here
                .forEach(metricsPrefixesToUse::add);
        final boolean reportAllMetrics = isReportAllMetrics(metricsPrefixesToUse);
        this.producerMetricsEnabled = reportAllMetrics || metricsPrefixesToUse.stream().anyMatch(
                prefix -> "kafka.producer".startsWith(prefix) || prefix.startsWith("kafka.producer"));
        this.consumerMetricsEnabled = reportAllMetrics || metricsPrefixesToUse.stream().anyMatch(
                prefix -> "kafka.consumer".startsWith(prefix) || prefix.startsWith("kafka.consumer"));

        if (!this.producerMetricsEnabled && !this.consumerMetricsEnabled) {
            LOG.info("Kafka client metrics are disabled (defaults not used and metrics list empty or without matching entries); given metrics prefixes: {}",
                    metricsPrefixes);
        }
    }

    private static boolean isReportAllMetrics(final List<String> metricsPrefixes) {
        return metricsPrefixes.contains("kafka") || metricsPrefixes.contains(PREFIX_KAFKA);
    }

    /**
     * Checks if producer metrics are enabled either by using the default metrics or via a provided metrics
     * list matching any kind of producer metric.
     *
     * @return {@code true} if producer metrics are enabled.
     */
    public boolean isProducerMetricsEnabled() {
        return producerMetricsEnabled;
    }

    /**
     * Checks if consumer metrics are enabled either by using the default metrics or via a provided metrics
     * list matching any kind of consumer metric.
     *
     * @return {@code true} if consumer metrics are enabled.
     */
    public boolean isConsumerMetricsEnabled() {
        return consumerMetricsEnabled;
    }

    @Override
    public synchronized void registerKafkaProducer(final Producer<?, ?> producer) {
        Objects.requireNonNull(producer);
        if (producerMetricsEnabled && !producerMetricsMap.containsKey(producer)) {
            final KafkaClientMetrics kafkaClientMetrics = new KafkaClientMetrics(producer);
            if (producerMetricsMap.putIfAbsent(producer, kafkaClientMetrics) == null) {
                boundMeterRegistries.forEach(kafkaClientMetrics::bindTo);
                LOG.debug("registered producer ({} producers total)", producerMetricsMap.size());
            }
        }
    }

    @Override
    public synchronized void registerKafkaConsumer(final Consumer<?, ?> consumer) {
        Objects.requireNonNull(consumer);
        if (consumerMetricsEnabled && !consumerMetricsMap.containsKey(consumer)) {
            final KafkaClientMetrics kafkaClientMetrics = new KafkaClientMetrics(consumer);
            if (consumerMetricsMap.putIfAbsent(consumer, kafkaClientMetrics) == null) {
                boundMeterRegistries.forEach(kafkaClientMetrics::bindTo);
                LOG.debug("registered consumer ({} consumers total)", consumerMetricsMap.size());
            }
        }
    }

    @Override
    public synchronized void unregisterKafkaProducer(final Producer<?, ?> producer) {
        Objects.requireNonNull(producer);
        Optional.ofNullable(producerMetricsMap.remove(producer)).ifPresent(KafkaClientMetrics::close);
    }

    @Override
    public synchronized void unregisterKafkaConsumer(final Consumer<?, ?> consumer) {
        Objects.requireNonNull(consumer);
        Optional.ofNullable(consumerMetricsMap.remove(consumer)).ifPresent(KafkaClientMetrics::close);
    }

    @Override
    public synchronized void bindTo(@NonNull final MeterRegistry registry) {
        if (producerMetricsEnabled || consumerMetricsEnabled) {
            if (isReportAllMetrics(metricsPrefixesToUse)) {
                LOG.info("activating Kafka client metrics support; all metrics will be reported "
                        + "- consider configuring individual metrics to reduce the number of reported metrics");
            } else {
                LOG.info("activating Kafka client metrics support; used metrics prefixes: {}", metricsPrefixesToUse);
                registry.config().meterFilter(MeterFilter
                        .accept(id -> metricsPrefixesToUse.stream().anyMatch(prefix -> id.getName().startsWith(prefix))));
                // deny all kafka metrics not previously accepted
                registry.config().meterFilter(MeterFilter.denyNameStartsWith(PREFIX_KAFKA));
            }
            consumerMetricsMap.values().forEach(kafkaClientMetrics -> kafkaClientMetrics.bindTo(registry));
            producerMetricsMap.values().forEach(kafkaClientMetrics -> kafkaClientMetrics.bindTo(registry));
            boundMeterRegistries.add(registry);
        }
    }

}
