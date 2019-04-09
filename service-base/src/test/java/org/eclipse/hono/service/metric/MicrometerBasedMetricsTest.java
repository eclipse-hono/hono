/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.metric;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.QoS;
import org.eclipse.hono.service.metric.MetricsTags.TtdStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;


/**
 * Verifies behavior of {@link MicrometerBasedMetrics}.
 *
 */
@RunWith(Parameterized.class)
public class MicrometerBasedMetricsTest {

    /**
     * The Micrometer registry to run the tests against.
     */
    @Parameter
    public MeterRegistry registry;
    private MicrometerBasedMetrics metrics;

    /**
     * Gets the Micrometer registries that the tests should be run against.
     * 
     * @return The registries.
     */
    @Parameters(name = "{0}")
    public static Collection<MeterRegistry> registries() {
        return Arrays.asList(new MeterRegistry[] {
                                new PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
                                new GraphiteMeterRegistry(GraphiteConfig.DEFAULT, Clock.SYSTEM)
                                });
    }

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        metrics = new MicrometerBasedMetrics(registry);
    }

    /**
     * Verifies that arbitrary telemetry messages with or without a QoS
     * can be reported successfully.
     */
    @Test
    public void testReportTelemetryWithOptionalQos() {

        // GIVEN a sample
        final Sample sample = metrics.startTimer();

        // WHEN reporting a telemetry message with a QoS of AT_LEAST_ONCE
        // and no TTD
        metrics.reportTelemetry(
                MetricsTags.EndpointType.TELEMETRY,
                "tenant",
                MetricsTags.ProcessingOutcome.FORWARDED,
                MetricsTags.QoS.AT_LEAST_ONCE,
                1024,
                MetricsTags.TtdStatus.NONE,
                sample);

        // THEN the meter can be found in the registry with the tags that have a known value
        final Tags expectedTags = Tags.of(MetricsTags.EndpointType.TELEMETRY.asTag())
                .and(MetricsTags.getTenantTag("tenant"))
                .and(MetricsTags.ProcessingOutcome.FORWARDED.asTag())
                .and(MetricsTags.QoS.AT_LEAST_ONCE.asTag());
        assertNotNull(registry.find(MicrometerBasedMetrics.METER_MESSAGES_RECEIVED).tags(expectedTags).timer());

        // and reporting another telemetry message with no QoS but with a TTD status succeeds

        final Sample otherSample = metrics.startTimer();

        metrics.reportTelemetry(
                MetricsTags.EndpointType.TELEMETRY,
                "tenant",
                MetricsTags.ProcessingOutcome.FORWARDED,
                MetricsTags.QoS.UNKNOWN,
                1024,
                MetricsTags.TtdStatus.EXPIRED,
                otherSample);
    }

    /**
     * Verifies that when reporting a downstream message no tags for
     * {@link QoS#UNKNOWN} nor {@link TtdStatus#NONE} are included.
     */
    @Test
    public void testReportTelemetryWithUnknownTagValues() {

        metrics.reportTelemetry(
                MetricsTags.EndpointType.TELEMETRY,
                "tenant",
                MetricsTags.ProcessingOutcome.FORWARDED,
                MetricsTags.QoS.UNKNOWN,
                1024,
                MetricsTags.TtdStatus.NONE,
                metrics.startTimer());

        final Tags expectedTags = Tags.of(MetricsTags.EndpointType.TELEMETRY.asTag())
                .and(MetricsTags.getTenantTag("tenant"))
                .and(MetricsTags.ProcessingOutcome.FORWARDED.asTag())
                .and(MetricsTags.QoS.UNKNOWN.asTag())
                .and(MetricsTags.TtdStatus.NONE.asTag());

        assertNotNull(registry.find(MicrometerBasedMetrics.METER_MESSAGES_RECEIVED).tags(expectedTags).timer());
    }

    /**
     * Verifies that when reporting a downstream message the legacy metrics
     * are also reported, if set.
     */
    @Test
    public void testReportTelemetryInvokesLegacyMetrics() {

        final LegacyMetrics legacyMetrics = mock(LegacyMetrics.class);
        metrics.setLegacyMetrics(legacyMetrics);
        metrics.reportTelemetry(
                MetricsTags.EndpointType.TELEMETRY,
                "tenant",
                MetricsTags.ProcessingOutcome.FORWARDED,
                MetricsTags.QoS.UNKNOWN,
                1024,
                MetricsTags.TtdStatus.NONE,
                metrics.startTimer());

        verify(legacyMetrics).incrementProcessedMessages(eq(EndpointType.TELEMETRY), eq("tenant"));
    }
}
