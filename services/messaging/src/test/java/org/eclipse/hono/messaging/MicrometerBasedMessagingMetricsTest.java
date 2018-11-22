/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.messaging;

import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.Before;
import org.junit.Test;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;


/**
 * Tests verifying behavior of {@link LegacyMetricsConfig}.
 *
 */
public class MicrometerBasedMessagingMetricsTest {

    private final ResourceIdentifier event = ResourceIdentifier.from(
            EventConstants.EVENT_ENDPOINT, Constants.DEFAULT_TENANT, null);
    private final ResourceIdentifier telemetry = ResourceIdentifier.from(
            TelemetryConstants.TELEMETRY_ENDPOINT, Constants.DEFAULT_TENANT, null);


    private LegacyMetricsConfig legacyMetricsConfig;
    private CompositeMeterRegistry registry;
    private MicrometerBasedMessagingMetrics metrics;

    /**
     * Creates fixture.
     */
    @Before
    public void setUp() {

        legacyMetricsConfig = new LegacyMetricsConfig();

        final GraphiteMeterRegistry graphiteRegistry = legacyMetricsConfig.graphiteMeterRegistry(GraphiteConfig.DEFAULT, Clock.SYSTEM);
        legacyMetricsConfig.legacyMeterFilters().customize(graphiteRegistry);
        graphiteRegistry.config().commonTags(Tags.of(MetricsTags.TAG_PROTOCOL, "messaging"));

        registry = new CompositeMeterRegistry();
        registry.add(graphiteRegistry);
        metrics = new MicrometerBasedMessagingMetrics(registry);
    }

    /**
     * Verifies that metrics for different target addresses can
     * be reported without naming conflicts.
     */
    @Test
    public void testReportMetricsWithDifferentTargetAddressesSucceeds() {

        metrics.incrementUpstreamLinks(event);
        metrics.incrementUpstreamLinks(telemetry);

        metrics.incrementDownstreamSenders(event);
        metrics.incrementDownstreamSenders(telemetry);

        metrics.submitDownstreamLinkCredits(event, 100);
        metrics.submitDownstreamLinkCredits(telemetry, 100);

        metrics.incrementDiscardedMessages(event);
        metrics.incrementDiscardedMessages(telemetry);

        metrics.incrementProcessedMessages(event);
        metrics.incrementProcessedMessages(telemetry);

        metrics.incrementUndeliverableMessages(event);
        metrics.incrementUndeliverableMessages(telemetry);

        metrics.incrementDownStreamConnections();
    }
}
