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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.stream.Stream;

import org.eclipse.hono.config.ProtocolAdapterProperties;
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
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;


/**
 * Verifies behavior of {@link MicrometerBasedMetrics}.
 *
 */
public class MicrometerBasedMetricsTest {

    private final String tenant = "tenant";

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

        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry, mock(Vertx.class));

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

        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry, mock(Vertx.class));

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

        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry, mock(Vertx.class));

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

        final Metrics metrics = new MicrometerBasedMetrics(registry, mock(Vertx.class));
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

        final Metrics metrics = new MicrometerBasedMetrics(registry, mock(Vertx.class));
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
     * Verifies that collecting the last message send time is disabled by default.
     * 
     * @param registry : the registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testNoTenantTimeoutOnDefault(final MeterRegistry registry) {

        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry, mock(Vertx.class));

        reportTelemetry(metrics);
        assertTrue(metrics.getLastSendTimestampsPerTenant().isEmpty());

        metrics.setProtocolAdapterProperties(new ProtocolAdapterProperties());
        reportTelemetry(metrics);
        assertTrue(metrics.getLastSendTimestampsPerTenant().isEmpty());

        final ProtocolAdapterProperties config = new ProtocolAdapterProperties();
        config.setTenantIdleTimeout(Duration.ZERO);
        metrics.setProtocolAdapterProperties(config);
        reportTelemetry(metrics);
        assertTrue(metrics.getLastSendTimestampsPerTenant().isEmpty());
    }

    /**
     * Verifies that when sending the first message for a tenant the timestamp is recorded and a timer is started to
     * check the timeout.
     * 
     * @param registry : the registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testTimeoutEvent(final MeterRegistry registry) {

        // GIVEN a metrics instance...
        final Vertx vertx = mock(Vertx.class);
        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry, vertx);

        // ... with tenantIdleTimeout configured
        final long timeoutMillis = 10L;
        final ProtocolAdapterProperties config = new ProtocolAdapterProperties();
        config.setTenantIdleTimeout(Duration.ofMillis(timeoutMillis));
        metrics.setProtocolAdapterProperties(config);

        // WHEN sending a message
        reportTelemetry(metrics);

        // THEN the tenant is added to the map that stores the last send time per tenant...
        assertEquals(1, metrics.getLastSendTimestampsPerTenant().size());
        assertNotNull(metrics.getLastSendTimestampsPerTenant().get(tenant));

        // ... and a timer is started
        verify(vertx).setTimer(eq(timeoutMillis), any(Handler.class));
    }

    /**
     * Verifies that sending messages updates the stored timestamp for the tenant.
     * 
     * @param registry : the registry that the tests should be run against.
     * @throws InterruptedException if thread sleep is interrupted.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testTenantLastSendIsUpdated(final MeterRegistry registry) throws InterruptedException {

        // GIVEN a metrics instance...
        final Vertx vertx = mock(Vertx.class);
        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry, vertx);

        // ... with tenantIdleTimeout configured
        final long timeoutMillis = 10L;
        final ProtocolAdapterProperties config = new ProtocolAdapterProperties();
        config.setTenantIdleTimeout(Duration.ofMillis(timeoutMillis));
        metrics.setProtocolAdapterProperties(config);

        // WHEN sending a message...
        reportTelemetry(metrics);
        final long timestampOfFirstMessage = metrics.getLastSendTimestampsPerTenant().get(tenant);

        // and later a second message
        Thread.sleep(1L);
        reportTelemetry(metrics);

        // THEN the timestamp for the tenant has been updated
        assertEquals(1, metrics.getLastSendTimestampsPerTenant().size());
        assertNotEquals(timestampOfFirstMessage, metrics.getLastSendTimestampsPerTenant().get(tenant));
    }

    private void reportTelemetry(final MicrometerBasedMetrics metrics) {
        metrics.reportTelemetry(
                EndpointType.TELEMETRY,
                tenant,
                TenantObject.from(tenant, true),
                MetricsTags.ProcessingOutcome.FORWARDED,
                QoS.UNKNOWN,
                1024,
                TtdStatus.NONE,
                metrics.startTimer());
    }

}
