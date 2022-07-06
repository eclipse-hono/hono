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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Verifies the behavior of {@link MicrometerKafkaClientMetricsSupport}.
 */
public class MicrometerKafkaClientMetricsSupportTest {

    private MeterRegistry meterRegistry;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        this.meterRegistry = mock(MeterRegistry.class);
        final MeterRegistry.Config meterRegistryConfig = mock(MeterRegistry.Config.class);
        when(meterRegistry.config()).thenReturn(meterRegistryConfig);
    }

    /**
     * Verifies that metrics are disabled if given metrics list is empty.
     */
    @Test
    public void testMetricsDisabledOnEmptyMetricsList() {
        final List<String> metricsPrefixes = List.of();
        final MicrometerKafkaClientMetricsSupport metricsSupport = new MicrometerKafkaClientMetricsSupport(
                false, metricsPrefixes);
        metricsSupport.bindTo(meterRegistry);
        assertThat(metricsSupport.isProducerMetricsEnabled()).isFalse();
        assertThat(metricsSupport.isConsumerMetricsEnabled()).isFalse();
    }

    /**
     * Verifies that producer and consumer metrics are enabled if the default metrics are used.
     */
    @Test
    public void testProducerConsumerMetricsEnabledWhenUsingDefaultMetrics() {
        final MicrometerKafkaClientMetricsSupport metricsSupport = new MicrometerKafkaClientMetricsSupport(
                true, List.of());
        metricsSupport.bindTo(meterRegistry);
        assertThat(metricsSupport.isProducerMetricsEnabled()).isTrue();
        assertThat(metricsSupport.isConsumerMetricsEnabled()).isTrue();
    }

    /**
     * Verifies that consumer metrics are disabled if given metrics list doesn't contain matching entries
     * and default metrics aren't used.
     */
    @Test
    public void testConsumerMetricsDisabledOnMetricsPrefixesListWithoutConsumerMetricPrefix() {
        final List<String> metricsPrefixes = List.of("kafka.prod");
        final MicrometerKafkaClientMetricsSupport metricsSupport = new MicrometerKafkaClientMetricsSupport(
                false, metricsPrefixes);
        metricsSupport.bindTo(meterRegistry);
        assertThat(metricsSupport.isProducerMetricsEnabled()).isTrue();
        assertThat(metricsSupport.isConsumerMetricsEnabled()).isFalse();
    }

    /**
     * Verifies that producer metrics are disabled if given metrics list doesn't contain matching entries
     * and default metrics aren't used.
     */
    @Test
    public void testProducerMetricsDisabledOnMetricsPrefixesListWithoutProducerMetricPrefix() {
        final List<String> metricsPrefixes = List.of("kafka.con");
        final MicrometerKafkaClientMetricsSupport metricsSupport = new MicrometerKafkaClientMetricsSupport(
                false, metricsPrefixes);
        metricsSupport.bindTo(meterRegistry);
        assertThat(metricsSupport.isProducerMetricsEnabled()).isFalse();
        assertThat(metricsSupport.isConsumerMetricsEnabled()).isTrue();
    }
}
