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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.stream.Stream;

import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.QoS;
import org.eclipse.hono.service.metric.MetricsTags.TtdStatus;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;


/**
 * Verifies behavior of {@link MicrometerBasedMetrics}.
 *
 */
public class MicrometerBasedMetricsTest {

    /**
     * Gets the Micrometer registries that the tests should be run against.
     * 
     * @return The registries.
     */
    public static Stream<MeterRegistry> registries() {
        return Stream.of(new MeterRegistry[] {
                                new PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
                                new GraphiteMeterRegistry(GraphiteConfig.DEFAULT, Clock.SYSTEM)
                                });
    }

    /**
     * Verifies that arbitrary telemetry messages with or without a QoS
     * can be reported successfully.
     *
     * @param registry : the registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testReportTelemetryWithOptionalQos(final MeterRegistry registry) {

        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry);

        // GIVEN a sample
        final Sample sample = metrics.startTimer();

        // WHEN reporting a telemetry message with a QoS of AT_LEAST_ONCE
        // and no TTD
        metrics.reportTelemetry(
                MetricsTags.EndpointType.TELEMETRY,
                "tenant",
                TenantObject.from("tenant", true),
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
                TenantObject.from("tenant", true),
                MetricsTags.ProcessingOutcome.FORWARDED,
                MetricsTags.QoS.UNKNOWN,
                1024,
                MetricsTags.TtdStatus.EXPIRED,
                otherSample);
    }

    /**
     * Verifies that when reporting a downstream message no tags for
     * {@link QoS#UNKNOWN} nor {@link TtdStatus#NONE} are included.
     *
     * @param registry : the registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testReportTelemetryWithUnknownTagValues(final MeterRegistry registry) {

        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry);

        metrics.reportTelemetry(
                MetricsTags.EndpointType.TELEMETRY,
                "tenant",
                TenantObject.from("tenant", true),
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
     *
     * @param registry : the registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testReportTelemetryInvokesLegacyMetrics(final MeterRegistry registry) {

        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry);

        final LegacyMetrics legacyMetrics = mock(LegacyMetrics.class);
        metrics.setLegacyMetrics(legacyMetrics);
        metrics.reportTelemetry(
                MetricsTags.EndpointType.TELEMETRY,
                "tenant",
                TenantObject.from("tenant", true),
                MetricsTags.ProcessingOutcome.FORWARDED,
                MetricsTags.QoS.UNKNOWN,
                1024,
                MetricsTags.TtdStatus.NONE,
                metrics.startTimer());

        verify(legacyMetrics).incrementProcessedMessages(eq(EndpointType.TELEMETRY), eq("tenant"));
    }

    /**
     * Verifies that the payload size is calculated based on the configured minimum message size 
     * when reporting downstream telemetry messages.
     * 
     * @param registry The registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testPayloadSizeForTelemetryMessages(final MeterRegistry registry) {

        final Metrics metrics = new MicrometerBasedMetrics(registry);
        final TenantObject tenantObject = TenantObject.from("TEST_TENANT", true)
                .setMinimumMessageSize(4 * 1024);

        metrics.reportTelemetry(
                MetricsTags.EndpointType.TELEMETRY,
                "tenant",
                tenantObject,
                MetricsTags.ProcessingOutcome.FORWARDED,
                MetricsTags.QoS.UNKNOWN,
                1 * 1024,
                MetricsTags.TtdStatus.NONE,
                metrics.startTimer());

        assertEquals(4 * 1024,
                registry.find(MicrometerBasedMetrics.METER_MESSAGES_PAYLOAD).summary().totalAmount());
    }

    /**
     * Verifies that the payload size is calculated based on the configured minimum message size 
     * when reporting command messages.
     *
     * @param registry The registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testPayloadSizeForCommandMessages(final MeterRegistry registry) {

        final Metrics metrics = new MicrometerBasedMetrics(registry);
        final TenantObject tenantObject = TenantObject.from("TEST_TENANT", true)
                .setMinimumMessageSize(4 * 1024);

        metrics.reportCommand(
                MetricsTags.Direction.REQUEST,
                "tenant",
                tenantObject,
                MetricsTags.ProcessingOutcome.FORWARDED,
                1 * 1024,
                metrics.startTimer());

        assertEquals(4 * 1024,
                registry.find(MicrometerBasedMetrics.METER_COMMANDS_PAYLOAD).summary().totalAmount());
    }

    /**
     * Verifies that the metrics for a tenant are removed when all their connections are closed.
     *
     * @param registry The registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testAuthenticatedConnections(final MeterRegistry registry) {

        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry);

        final String tenant1 = "tenant1";
        final String tenant2 = "tenant2";

        // GIVEN a metrics instance that has recorded one telemetry message and one event per tenant
        metrics.incrementConnections(tenant1);
        metrics.incrementConnections(tenant1);
        metrics.incrementConnections(tenant2);
        metrics.incrementConnections(tenant2);

        final Search search = registry.find(MicrometerBasedMetrics.METER_CONNECTIONS_AUTHENTICATED);
        assertEquals(2, search.meters().size());
        assertEquals(4, metrics.getNumberOfConnections());

        // WHEN closing all connections for one tenant
        metrics.decrementConnections(tenant1);
        assertEquals(2, search.meters().size());
        metrics.decrementConnections(tenant1);

        // THEN the metric for this tenant is removed
        assertEquals(1, search.meters().size());
        assertEquals(2, metrics.getNumberOfConnections());

    }

    /**
     * Verifies the behavior of removeTelemetryMetricsForTenant(), i.e. checks that metrics are removed for the given
     * event type and tenant.
     *
     * @param registry The registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testRemoveTelemetryMetricsForTenant(final MeterRegistry registry) {

        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry);
        final String tenant1 = "tenant1";
        final Tags tenantTag1 = Tags.of(MetricsTags.getTenantTag(tenant1));
        final String tenant2 = "tenant2";
        final Tags tenantTag2 = Tags.of(MetricsTags.getTenantTag(tenant2));

        // GIVEN a metrics instance that has recorded one telemetry message and one event per tenant
        reportTelemetry(metrics, EndpointType.TELEMETRY, tenant1);
        reportTelemetry(metrics, EndpointType.EVENT, tenant1);
        reportTelemetry(metrics, EndpointType.TELEMETRY, tenant2);
        reportTelemetry(metrics, EndpointType.EVENT, tenant2);

        assertEquals(4,
                registry.find(MicrometerBasedMetrics.METER_MESSAGES_RECEIVED).meters().size());
        assertEquals(4,
                registry.find(MicrometerBasedMetrics.METER_MESSAGES_PAYLOAD).meters().size());

        // WHEN removing the telemetry metrics for tenant1
        metrics.removeTelemetryMetricsForTenant(EndpointType.TELEMETRY, tenant1);

        // THEN only the metric for telemetry and this tenant is removed
        assertEquals(1,
                registry.find(MicrometerBasedMetrics.METER_MESSAGES_RECEIVED).tags(tenantTag1).meters().size());
        assertEquals(1,
                registry.find(MicrometerBasedMetrics.METER_MESSAGES_PAYLOAD).tags(tenantTag1).meters().size());

        metrics.removeTelemetryMetricsForTenant(EndpointType.EVENT, tenant1);
        assertEquals(0,
                registry.find(MicrometerBasedMetrics.METER_MESSAGES_RECEIVED).tags(tenantTag1).meters().size());
        assertEquals(0,
                registry.find(MicrometerBasedMetrics.METER_MESSAGES_PAYLOAD).tags(tenantTag1).meters().size());

        // AND the other tenant is untouched
        assertEquals(2,
                registry.find(MicrometerBasedMetrics.METER_MESSAGES_RECEIVED).tags(tenantTag2).meters().size());
        assertEquals(2,
                registry.find(MicrometerBasedMetrics.METER_MESSAGES_PAYLOAD).tags(tenantTag2).meters().size());
    }

    private void reportTelemetry(final MicrometerBasedMetrics metrics, final EndpointType type, final String tenantId) {
        metrics.reportTelemetry(
                type,
                tenantId,
                TenantObject.from(tenantId, true),
                MetricsTags.ProcessingOutcome.FORWARDED,
                QoS.AT_LEAST_ONCE,
                1024,
                TtdStatus.NONE,
                metrics.startTimer());
    }
}
