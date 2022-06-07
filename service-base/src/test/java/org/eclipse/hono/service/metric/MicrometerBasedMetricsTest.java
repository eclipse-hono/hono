/**
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.QoS;
import org.eclipse.hono.service.metric.MetricsTags.TtdStatus;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

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
                                new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
                                });
    }

    /**
     * Verifies that arbitrary telemetry messages with or without a QoS
     * can be reported successfully.
     *
     * @param registry The registry that the tests should be run against.
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
        assertNotNull(registry.find(MicrometerBasedMetrics.METER_TELEMETRY_PROCESSING_DURATION).tags(expectedTags).timer());

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
     * @param registry The registry that the tests should be run against.
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

        assertNotNull(registry.find(MicrometerBasedMetrics.METER_TELEMETRY_PROCESSING_DURATION).tags(expectedTags).timer());
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
                registry.find(MicrometerBasedMetrics.METER_TELEMETRY_PAYLOAD).summary().totalAmount());
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
                registry.find(MicrometerBasedMetrics.METER_COMMAND_PAYLOAD).summary().totalAmount());
    }

    /**
     * Verifies that the connection time duration is recorded for the given tenant when devices get connected and
     * disconnected.
     *
     * @param registry The registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testConnectionTimeDuration(final MeterRegistry registry) {
        final MeterRegistry meterRegistry = Mockito.spy(registry);
        final Metrics metrics = new MicrometerBasedMetrics(meterRegistry, mock(Vertx.class));
        metrics.incrementConnections("TEST_TENANT");
        metrics.decrementConnections("TEST_TENANT");
        verify(meterRegistry, times(1)).timer(
                MicrometerBasedMetrics.METER_CONNECTIONS_AUTHENTICATED_DURATION,
                Tags.of(MetricsTags.getTenantTag("TEST_TENANT")));
    }

    /**
     * Verifies that collecting the last message send time is disabled by default.
     *
     * @param registry The registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testNoTenantTimeoutOnDefault(final MeterRegistry registry) {

        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry, mock(Vertx.class));

        reportTelemetry(metrics);
        assertTrue(metrics.getLastSeenTimestampPerTenant().isEmpty());

        metrics.setTenantIdleTimeout(Duration.ZERO);
        reportTelemetry(metrics);
        assertTrue(metrics.getLastSeenTimestampPerTenant().isEmpty());
    }

    /**
     * Verifies that when sending the first message for a tenant the timestamp is recorded and a timer is started to
     * check the timeout.
     *
     * @param registry The registry that the tests should be run against.
     */
    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @MethodSource("registries")
    public void testTimeoutEvent(final MeterRegistry registry) {

        // GIVEN a metrics instance...
        final Vertx vertx = mock(Vertx.class);
        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry, vertx);

        // ... with tenantIdleTimeout configured
        final long timeoutMillis = 10L;
        metrics.setTenantIdleTimeout(Duration.ofMillis(timeoutMillis));

        // WHEN sending a message
        reportTelemetry(metrics);

        // THEN the tenant is added to the map that stores the last send time per tenant...
        assertEquals(1, metrics.getLastSeenTimestampPerTenant().size());
        assertNotNull(metrics.getLastSeenTimestampPerTenant().get(tenant));

        // ... and a timer is started
        verify(vertx).setTimer(eq(timeoutMillis), any(Handler.class));
    }

    /**
     * Verifies that the metrics are removed when the tenant timeout is exceeded.
     *
     * @param registry The registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testTimeoutRemovesMetrics(final MeterRegistry registry) {

        final Tags tenantTags = Tags.of(MetricsTags.getTenantTag(tenant));
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(mock(EventBus.class));
        // a mocked Vert.x timer, that can be fired deliberately later in the test
        final AtomicReference<Handler<Long>> timerHandler = new AtomicReference<>();
        when(vertx.setTimer(anyLong(), any())).thenAnswer(invocation -> {
            final Handler<Long> task = invocation.getArgument(1);
            timerHandler.set(task);
            return 1L;
        });

        // GIVEN a metrics instance with tenantIdleTimeout configured ...
        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry, vertx);
        metrics.setTenantIdleTimeout(Duration.ofMillis(1L));

        // ... with a device connected and a telemetry message and a command recorded
        metrics.incrementConnections(tenant);
        reportTelemetry(metrics);
        reportCommand(metrics);

        assertNotNull(registry.find(MicrometerBasedMetrics.METER_TELEMETRY_PAYLOAD).tags(tenantTags).meter());
        assertNotNull(registry.find(MicrometerBasedMetrics.METER_TELEMETRY_PROCESSING_DURATION).tags(tenantTags).meter());
        assertNotNull(registry.find(MicrometerBasedMetrics.METER_COMMAND_PAYLOAD).tags(tenantTags).meter());
        assertNotNull(registry.find(MicrometerBasedMetrics.METER_COMMAND_PROCESSING_DURATION).tags(tenantTags).meter());
        assertNotNull(registry.find(MicrometerBasedMetrics.METER_CONNECTIONS_AUTHENTICATED).tags(tenantTags).meter());

        // WHEN the device disconnects ...
        metrics.decrementConnections(tenant);
        // ... and the timeout timer fires
        metrics.getLastSeenTimestampPerTenant().put(tenant, 0L); // fake timeout duration exceeded
        timerHandler.get().handle(null);

        // THEN the metrics have been removed
        assertNull(registry.find(MicrometerBasedMetrics.METER_TELEMETRY_PAYLOAD).tags(tenantTags).meter());
        assertNull(registry.find(MicrometerBasedMetrics.METER_TELEMETRY_PROCESSING_DURATION).tags(tenantTags).meter());
        assertNull(registry.find(MicrometerBasedMetrics.METER_COMMAND_PAYLOAD).tags(tenantTags).meter());
        assertNull(registry.find(MicrometerBasedMetrics.METER_COMMAND_PROCESSING_DURATION).tags(tenantTags).meter());
        assertNull(registry.find(MicrometerBasedMetrics.METER_CONNECTIONS_AUTHENTICATED).tags(tenantTags).meter());

    }

    /**
     * Verifies that sending messages updates the stored timestamp for the tenant.
     *
     * @param registry The registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testLastSeenIsUpdatedOnMessageSend(final MeterRegistry registry) {

        // GIVEN a metrics instance with tenantIdleTimeout configured...
        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry, mock(Vertx.class));
        metrics.setTenantIdleTimeout(Duration.ofMillis(10L));
        // ..and last seen timestamp is initialized with low value
        final long timestampBefore = 0L;
        metrics.getLastSeenTimestampPerTenant().put(tenant, timestampBefore);

        // WHEN sending a message
        reportTelemetry(metrics);

        // THEN the timestamp for the tenant has been updated
        assertEquals(1, metrics.getLastSeenTimestampPerTenant().size());
        assertNotEquals(timestampBefore, metrics.getLastSeenTimestampPerTenant().get(tenant));
    }

    /**
     * Verifies that disconnecting updates the stored timestamp for the tenant.
     *
     * @param registry The registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testLastSeenIsUpdatedOnDisconnect(final MeterRegistry registry) {

        // GIVEN a metrics instance with tenantIdleTimeout configured...
        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry, mock(Vertx.class));
        metrics.setTenantIdleTimeout(Duration.ofMillis(10L));
        // ..and last seen timestamp is initialized with low value
        final long timestampBefore = 0L;
        metrics.getLastSeenTimestampPerTenant().put(tenant, timestampBefore);

        // WHEN disconnecting
        metrics.decrementConnections(tenant);

        // THEN the timestamp for the tenant has been updated
        assertEquals(1, metrics.getLastSeenTimestampPerTenant().size());
        assertNotEquals(timestampBefore, metrics.getLastSeenTimestampPerTenant().get(tenant));
    }

    /**
     * Verifies that connecting updates the stored timestamp for the tenant.
     *
     * @param registry The registry that the tests should be run against.
     */
    @ParameterizedTest
    @MethodSource("registries")
    public void testLastSeenIsUpdatedOnConnect(final MeterRegistry registry) {

        // GIVEN a metrics instance with tenantIdleTimeout configured...
        final MicrometerBasedMetrics metrics = new MicrometerBasedMetrics(registry, mock(Vertx.class));
        metrics.setTenantIdleTimeout(Duration.ofMillis(10L));
        // ..and last seen timestamp is initialized with low value
        final long timestampBefore = 0L;
        metrics.getLastSeenTimestampPerTenant().put(tenant, timestampBefore);

        // WHEN connecting
        metrics.incrementConnections(tenant);

        // THEN the timestamp for the tenant has been updated
        assertEquals(1, metrics.getLastSeenTimestampPerTenant().size());
        assertNotEquals(timestampBefore, metrics.getLastSeenTimestampPerTenant().get(tenant));
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

    private void reportCommand(final MicrometerBasedMetrics metrics) {
        metrics.reportCommand(
                MetricsTags.Direction.REQUEST,
                tenant,
                TenantObject.from(tenant, true),
                MetricsTags.ProcessingOutcome.FORWARDED,
                1 * 1024,
                metrics.startTimer());
    }

}
