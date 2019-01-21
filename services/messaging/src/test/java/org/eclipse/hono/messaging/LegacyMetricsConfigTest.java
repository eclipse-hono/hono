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


package org.eclipse.hono.messaging;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MicrometerBasedMetrics;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.Before;
import org.junit.Test;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.graphite.GraphiteHierarchicalNameMapper;


/**
 * Tests verifying behavior of {@link LegacyMetricsConfig}.
 *
 */
public class LegacyMetricsConfigTest {

    private static final String HOSTNAME = "the.node";
    private static final String HOSTNAME_MAPPED = "the_node";
    private static final String COMPONENT_NAME = "messaging";
    private static final String TENANT_NAME = Constants.DEFAULT_TENANT;

    private LegacyMetricsConfig config;
    private Tags defaultTags;
    private MeterFilter[] meterFilters;
    private HierarchicalNameMapper mapper;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        config = new LegacyMetricsConfig();
        mapper = config.legacyGraphiteFormatMapper(new GraphiteHierarchicalNameMapper(MetricsTags.TAG_HOST));
        meterFilters = config.getMeterFilters();
        defaultTags = Tags.of(MetricsTags.TAG_HOST, HOSTNAME)
                .and(MetricsTags.TAG_COMPONENT, MetricsTags.VALUE_COMPONENT_SERVICE)
                .and(MetricsTags.TAG_PROTOCOL, COMPONENT_NAME)
                .and(MetricsTags.TAG_TENANT, TENANT_NAME);
    }

    private Id filter(final Id origId) {
        Id result = origId;
        for (MeterFilter filter : meterFilters) {
            result = filter.map(result);
        }
        return result;
    }

    /**
     * Verifies that messaging metrics are correctly mapped to
     * the legacy format's metric names.
     */
    @Test
    public void testMappingOfHonoMessagingMetrics() {

        // Telemetry related meters
        String messageType = TelemetryConstants.TELEMETRY_ENDPOINT;
        Tags tags = defaultTags.and(MetricsTags.TAG_TYPE, messageType);
        assertMappings(messageType, tags);


        // Event related meters
        messageType = EventConstants.EVENT_ENDPOINT;
        tags = defaultTags.and(MetricsTags.TAG_TYPE, messageType);
        assertMappings(messageType, tags);
    }

    private void assertMappings(final String messageType, final Tags tags) {

        assertMapping(
                new Id(MicrometerBasedMessagingMetrics.METER_UPSTREAM_LINKS, tags, null, null, Type.GAUGE),
                String.format("%s.counter.hono.messaging.receivers.upstream.links.%s.%s.count",
                        HOSTNAME_MAPPED, messageType, TENANT_NAME));
        assertMapping(
                new Id(MicrometerBasedMessagingMetrics.METER_DOWNSTREAM_SENDERS, tags, null, null, Type.GAUGE),
                String.format("%s.counter.hono.messaging.senders.downstream.%s.%s.count",
                        HOSTNAME_MAPPED, messageType, TENANT_NAME));
        assertMapping(
                new Id(MicrometerBasedMessagingMetrics.METER_DOWNSTREAM_LINK_CREDITS, tags, null, null, Type.GAUGE),
                String.format("%s.gauge.hono.messaging.link.downstream.credits.%s.%s",
                        HOSTNAME_MAPPED, messageType, TENANT_NAME));
        assertMapping(
                new Id(MicrometerBasedMessagingMetrics.METER_MESSAGES_DISCARDED, tags, null, null, Type.COUNTER),
                String.format("%s.counter.hono.messaging.messages.%s.%s.discarded",
                        HOSTNAME_MAPPED, messageType, TENANT_NAME));
        assertMapping(
                new Id(MicrometerBasedMessagingMetrics.METER_MESSAGES_UNDELIVERABLE, tags, null, null, Type.COUNTER),
                String.format("%s.counter.hono.messaging.messages.%s.%s.undeliverable",
                        HOSTNAME_MAPPED, messageType, TENANT_NAME));
        assertMapping(
                new Id(MicrometerBasedMetrics.METER_MESSAGES_PROCESSED, tags, null, null, Type.COUNTER),
                String.format("%s.meter.hono.messaging.messages.%s.%s.processed",
                        HOSTNAME_MAPPED, messageType, TENANT_NAME));
    }

    private void assertMapping(final Id orig, final String expectedMapping) {

        final Id mappedId = filter(orig);
        final String metricName = mapper.toHierarchicalName(mappedId, NamingConvention.dot);
        assertThat(metricName, is(expectedMapping));
    }
}
