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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.Before;
import org.junit.Test;

import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.Tags;
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
    private static final String TENANT = Constants.DEFAULT_TENANT;

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
        defaultTags = Tags.of(MetricsTags.TAG_HOST, HOSTNAME);
    }

    private Id filter(final Id origId) {
        Id result = origId;
        for (MeterFilter filter : meterFilters) {
            result = filter.map(result);
        }
        return result;
    }

    /**
     * Verifies that HTTP adapter metrics are correctly mapped to
     * the legacy format's metric names.
     */
    @Test
    public void testMappingOfHttpAdapterMetrics() {

        final Tags httpTags = defaultTags
                .and(MetricsTags.TAG_COMPONENT_TYPE, MetricsTags.VALUE_COMPONENT_TYPE_ADAPTER)
                .and(MetricsTags.TAG_PROTOCOL, MetricsTags.VALUE_PROTOCOL_HTTP)
                .and(MetricsTags.TAG_TENANT, TENANT);

        assertCommonAdapterMetrics("http", httpTags);
    }

    /**
     * Verifies that MQTT adapter metrics are correctly mapped to
     * the legacy format's metric names.
     */
    @Test
    public void testMappingOfMqttAdapterMetrics() {

        final Tags mqttTags = defaultTags
                .and(MetricsTags.TAG_COMPONENT_TYPE, MetricsTags.VALUE_COMPONENT_TYPE_ADAPTER)
                .and(MetricsTags.TAG_PROTOCOL, MetricsTags.VALUE_PROTOCOL_MQTT);
        assertConnectionMetrics("mqtt", mqttTags);

        final Tags mqttWithTenantTags = mqttTags.and(MetricsTags.TAG_TENANT, TENANT);
        assertCommonAdapterMetrics("mqtt", mqttWithTenantTags);
    }

    private void assertCommonAdapterMetrics(final String protocol, final Tags tags) {

        // Command related meters

        assertMapping(
                new Id(MicrometerBasedMetrics.METER_COMMANDS_DEVICE_DELIVERED, tags, null, null, Type.COUNTER),
                String.format("%s.meter.hono.%s.commands.%s.device.delivered",
                        HOSTNAME_MAPPED, protocol, TENANT));

        assertMapping(
                new Id(MicrometerBasedMetrics.METER_COMMANDS_TTD_EXPIRED, tags, null, null, Type.COUNTER),
                String.format("%s.meter.hono.%s.commands.%s.ttd.expired",
                        HOSTNAME_MAPPED, protocol, TENANT));

        assertMapping(
                new Id(MicrometerBasedMetrics.METER_COMMANDS_RESPONSE_DELIVERED, tags, null, null, Type.COUNTER),
                String.format("%s.meter.hono.%s.commands.%s.response.delivered",
                        HOSTNAME_MAPPED, protocol, TENANT));

        // Telemetry related meters

        final Tags telemetryTags = tags.and(MetricsTags.TAG_TYPE, TelemetryConstants.TELEMETRY_ENDPOINT);

        assertMapping(
                new Id(MicrometerBasedMetrics.METER_MESSAGES_PROCESSED, telemetryTags, null, null, Type.COUNTER),
                String.format("%s.meter.hono.%s.messages.telemetry.%s.processed",
                        HOSTNAME_MAPPED, protocol, TENANT));
        assertMapping(
                new Id(MicrometerBasedMetrics.METER_MESSAGES_UNDELIVERABLE, telemetryTags, null, null, Type.COUNTER),
                String.format("%s.counter.hono.%s.messages.telemetry.%s.undeliverable",
                        HOSTNAME_MAPPED, protocol, TENANT));

        // Event related meters

        final Tags eventTags = tags.and(MetricsTags.TAG_TYPE, EventConstants.EVENT_ENDPOINT);

        assertMapping(
                new Id(MicrometerBasedMetrics.METER_MESSAGES_PROCESSED, eventTags, null, null, Type.COUNTER),
                String.format("%s.meter.hono.%s.messages.event.%s.processed",
                        HOSTNAME_MAPPED, protocol, TENANT));
        assertMapping(
                new Id(MicrometerBasedMetrics.METER_MESSAGES_UNDELIVERABLE, eventTags, null, null, Type.COUNTER),
                String.format("%s.counter.hono.%s.messages.event.%s.undeliverable",
                        HOSTNAME_MAPPED, protocol, TENANT));
    }

    private void assertConnectionMetrics(final String protocol, final Tags tags) {

        final Tags tagsWithTenant = tags.and(MetricsTags.TAG_TENANT, TENANT);

        assertMapping(
                new Id(MicrometerBasedMetrics.METER_CONNECTIONS_UNAUTHENTICATED, tags, null, null, Type.GAUGE),
                String.format("%s.counter.hono.%s.connections.unauthenticated.count",
                        HOSTNAME_MAPPED, protocol));
        assertMapping(
                new Id(MicrometerBasedMetrics.METER_CONNECTIONS_AUTHENTICATED, tagsWithTenant, null, null, Type.GAUGE),
                String.format("%s.counter.hono.%s.connections.authenticated.%s.count",
                        HOSTNAME_MAPPED, protocol, TENANT));

    }

    private void assertMapping(final Id orig, final String expectedMapping) {

        final String metricName = mapper.toHierarchicalName(filter(orig), NamingConvention.dot);
        assertThat(metricName, is(expectedMapping));
    }
}
