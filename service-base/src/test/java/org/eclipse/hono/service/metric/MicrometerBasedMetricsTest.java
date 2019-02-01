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
import static org.junit.Assert.assertNull;

import org.eclipse.hono.service.metric.MetricsTags.QoS;
import org.eclipse.hono.service.metric.MetricsTags.TtdStatus;
import org.junit.Before;
import org.junit.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;


/**
 * Verifies behavior of {@link MicrometerBasedMetrics}.
 *
 */
public class MicrometerBasedMetricsTest {

    private MeterRegistry registry;
    private MicrometerBasedMetrics metrics;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerBasedMetrics(registry);
    }

    /**
     * Verifies that when reporting a downstream message no tags for
     * {@link QoS#UNKNOWN} nor {@link TtdStatus#NONE} are included.
     */
    @Test
    public void testReportTelemetryOmitsOptionalTags() {

        metrics.reportTelemetry(
                MetricsTags.EndpointType.TELEMETRY,
                "tenant",
                MetricsTags.ProcessingOutcome.FORWARDED,
                MetricsTags.QoS.UNKNOWN,
                MetricsTags.TtdStatus.NONE,
                metrics.startTimer());

        final Tags expectedTags = Tags.of(MetricsTags.EndpointType.TELEMETRY.asTag())
                .and(MetricsTags.getTenantTag("tenant"))
                .and(MetricsTags.ProcessingOutcome.FORWARDED.asTag());

        assertNotNull(registry.find(MicrometerBasedMetrics.METER_MESSAGES_RECEIVED).tags(expectedTags).timer());

        assertNull(registry.find(MicrometerBasedMetrics.METER_MESSAGES_RECEIVED)
                .tags(expectedTags).tagKeys(MetricsTags.QoS.TAG_NAME).timer());
        assertNull(registry.find(MicrometerBasedMetrics.METER_MESSAGES_RECEIVED)
                .tags(expectedTags).tagKeys(MetricsTags.TtdStatus.TAG_NAME).timer());
    }
}
