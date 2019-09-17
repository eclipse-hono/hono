/*******************************************************************************
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.service.metric;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantObject;
import org.springframework.beans.factory.annotation.Autowired;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import io.vertx.core.Vertx;

/**
 * Micrometer based metrics implementation.
 * <p>
 * Also checks if tenant {@link ProtocolAdapterProperties#getTenantIdleTimeout()} is exceeded and publishes the tenantId
 * on the event bus address {@link Constants#EVENT_BUS_ADDRESS_TENANT_TIMED_OUT} once a tenant times out.
 * 
 */
public class MicrometerBasedMetrics implements Metrics {

    /**
     * The name of the meter for authenticated connections.
     */
    public static final String METER_CONNECTIONS_AUTHENTICATED = "hono.connections.authenticated";
    /**
     * The name of the meter for unauthenticated connections.
     */
    public static final String METER_CONNECTIONS_UNAUTHENTICATED = "hono.connections.unauthenticated";
    /**
     * The name of the meter for recording message payload size.
     */
    public static final String METER_MESSAGES_PAYLOAD = "hono.messages.payload";
    /**
     * The name of the meter for messages received from devices.
     */
    public static final String METER_MESSAGES_RECEIVED = "hono.messages.received";
    /**
     * The name of the meter for recording command payload size.
     */
    public static final String METER_COMMANDS_PAYLOAD = "hono.commands.payload";
    /**
     * The name of the meter for command messages.
     */
    public static final String METER_COMMANDS_RECEIVED = "hono.commands.received";

    private static final long DEFAULT_TENANT_IDLE_TIMEOUT = ProtocolAdapterProperties.DEFAULT_TENANT_IDLE_TIMEOUT
            .toMillis();

    /**
     * The meter registry.
     */
    protected final MeterRegistry registry;

    private final Map<String, AtomicLong> authenticatedConnections = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSendTimestampsPerTenant = new ConcurrentHashMap<>();
    private final AtomicLong unauthenticatedConnections;
    private final AtomicInteger totalCurrentConnections = new AtomicInteger();
    private final Vertx vertx;
    private long tenantIdleTimeout = DEFAULT_TENANT_IDLE_TIMEOUT;

    /**
     * Creates a new metrics instance.
     * 
     * @param registry The meter registry to use.
     * @param vertx The Vert.x instance to use.
     * @throws NullPointerException if registry or vertx is {@code null}.
     */
    protected MicrometerBasedMetrics(final MeterRegistry registry, final Vertx vertx) {

        Objects.requireNonNull(registry);
        Objects.requireNonNull(vertx);

        this.registry = registry;
        this.vertx = vertx;
        this.unauthenticatedConnections = registry.gauge(METER_CONNECTIONS_UNAUTHENTICATED, new AtomicLong());
    }

    /**
     * Sets the protocol adapter configuration.
     * 
     * @param config The protocol adapter configuration.
     * @throws NullPointerException if config is {@code null}.
     */
    @Autowired(required = false)
    public void setProtocolAdapterProperties(final ProtocolAdapterProperties config) {
        Objects.requireNonNull(config);
        this.tenantIdleTimeout = config.getTenantIdleTimeout().toMillis();
    }

    @Override
    public final void incrementConnections(final String tenantId) {

        Objects.requireNonNull(tenantId);
        gaugeForTenant(METER_CONNECTIONS_AUTHENTICATED, this.authenticatedConnections, tenantId, AtomicLong::new)
                .incrementAndGet();
        this.totalCurrentConnections.incrementAndGet();

    }

    @Override
    public final void decrementConnections(final String tenantId) {

        Objects.requireNonNull(tenantId);
        gaugeForTenant(METER_CONNECTIONS_AUTHENTICATED, this.authenticatedConnections, tenantId, AtomicLong::new)
                .decrementAndGet();
        this.totalCurrentConnections.decrementAndGet();

    }

    @Override
    public final void incrementUnauthenticatedConnections() {
        this.unauthenticatedConnections.incrementAndGet();
        this.totalCurrentConnections.incrementAndGet();
    }

    @Override
    public final void decrementUnauthenticatedConnections() {
        this.unauthenticatedConnections.decrementAndGet();
        this.totalCurrentConnections.decrementAndGet();
    }

    @Override
    public int getNumberOfConnections() {
        return this.totalCurrentConnections.get();
    }

    @Override
    public Sample startTimer() {
        return Timer.start(registry);
    }

    @Override
    public void reportTelemetry(
            final MetricsTags.EndpointType type,
            final String tenantId,
            final TenantObject tenantObject,
            final ProcessingOutcome outcome,
            final MetricsTags.QoS qos,
            final int payloadSize,
            final Sample timer) {
        reportTelemetry(type, tenantId, tenantObject, outcome, qos, payloadSize, MetricsTags.TtdStatus.NONE, timer);
    }

    @Override
    public final void reportTelemetry(
            final MetricsTags.EndpointType type,
            final String tenantId,
            final TenantObject tenantObject,
            final MetricsTags.ProcessingOutcome outcome,
            final MetricsTags.QoS qos,
            final int payloadSize,
            final MetricsTags.TtdStatus ttdStatus,
            final Sample timer) {

        Objects.requireNonNull(type);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(qos);
        Objects.requireNonNull(ttdStatus);
        Objects.requireNonNull(timer);

        if (type != MetricsTags.EndpointType.TELEMETRY && type != MetricsTags.EndpointType.EVENT) {
            throw new IllegalArgumentException("invalid type, must be either telemetry or event");
        } else if (payloadSize < 0) {
            throw new IllegalArgumentException("payload size must not be negative");
        }

        final Tags tags = Tags.of(type.asTag())
                .and(MetricsTags.getTenantTag(tenantId))
                .and(outcome.asTag())
                .and(qos.asTag())
                .and(ttdStatus.asTag());

        timer.stop(this.registry.timer(METER_MESSAGES_RECEIVED, tags));

        // record payload size
        DistributionSummary.builder(METER_MESSAGES_PAYLOAD)
            .baseUnit("bytes")
            .minimumExpectedValue(0L)
            .tags(tags)
            .register(this.registry)
            .record(calculatePayloadSize(payloadSize, tenantObject));

        updateLastSendForTenant(tenantId);
    }

    @Override
    public void reportCommand(
            final Direction direction,
            final String tenantId,
            final TenantObject tenantObject,
            final ProcessingOutcome outcome,
            final int payloadSize,
            final Sample timer) {

        Objects.requireNonNull(direction);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(timer);

        if (payloadSize < 0) {
            throw new IllegalArgumentException("payload size must not be negative");
        }

        final Tags tags = Tags.of(direction.asTag())
                .and(MetricsTags.getTenantTag(tenantId))
                .and(outcome.asTag());

        timer.stop(this.registry.timer(METER_COMMANDS_RECEIVED, tags));

        // record payload size
        DistributionSummary.builder(METER_COMMANDS_PAYLOAD)
            .baseUnit("bytes")
            .minimumExpectedValue(0L)
            .tags(tags)
            .register(this.registry)
            .record(calculatePayloadSize(payloadSize, tenantObject));

        updateLastSendForTenant(tenantId);
    }

    /**
     * Gets a gauge value for a specific key.
     * <p>
     * If no gauge value exists for the given key yet, a new value
     * is created using the given name and tags.
     * 
     * @param <K> The type of the key.
     * @param <V> The type of the gauge's value.
     * @param name The metric name.
     * @param map The map that holds the metric's gauge values.
     * @param key The key to use for looking up the gauge value.
     * @param tags The tags to use for creating the gauge if it does not exist yet.
     * @param instanceSupplier A supplier for creating a new instance of the gauge's value.
     * @return The gauge value.
     */
    protected <K, V extends Number> V gaugeForKey(final String name, final Map<K, V> map, final K key,
            final Tags tags, final Supplier<V> instanceSupplier) {

        return map.computeIfAbsent(key, a -> {

            return this.registry.gauge(name, tags, instanceSupplier.get());

        });
    }

    /**
     * Gets a gauge value for a tenant.
     * <p>
     * If no gauge value exists for the given tenant yet, a new value
     * is created using the given name and a tag for the tenant.
     * 
     * @param <V> The type of the gauge's value.
     * @param name The metric name.
     * @param map The map that holds the metric's gauge values.
     * @param tenant The tenant to use for looking up the gauge value.
     * @param instanceSupplier A supplier for creating a new instance of the gauge's value.
     * @return The gauge value.
     */
    protected <V extends Number> V gaugeForTenant(final String name, final Map<String, V> map, final String tenant,
            final Supplier<V> instanceSupplier) {

        return gaugeForKey(name, map, tenant, Tags.of(MetricsTags.getTenantTag(tenant)), instanceSupplier);
    }

    /**
     * Calculates the payload size based on the configured minimum message size.
     * <p>
     * If no minimum message size is configured for a tenant then the actual
     * payload size of the message is returned.
     * <p>
     * Example: The minimum message size for a tenant is configured as 4096 bytes (4KB).
     * So the payload size of a message of size 1KB is calculated as 4KB and for
     * message of size 10KB is calculated as 12KB.
     *
     * @param payloadSize The size of the message payload in bytes.
     * @param tenantObject The TenantObject.
     *
     * @return The calculated payload size.
     */
    protected final int calculatePayloadSize(final int payloadSize, final TenantObject tenantObject) {

        if (tenantObject == null) {
            return payloadSize;
        }

        final int minimumMessageSize = tenantObject.getMinimumMessageSize();
        if (minimumMessageSize > 0 && payloadSize > 0) {
            final int modValue = payloadSize % minimumMessageSize;
            if (modValue == 0) {
                return payloadSize;
            } else {
                return payloadSize + (minimumMessageSize - modValue);
            }
        }
        return payloadSize;
    }

    // visible for testing
    Map<String, Long> getLastSendTimestampsPerTenant() {
        return lastSendTimestampsPerTenant;
    }

    private void updateLastSendForTenant(final String tenantId) {
        if (tenantIdleTimeout == DEFAULT_TENANT_IDLE_TIMEOUT) {
            return;
        }

        final Long previousVal = lastSendTimestampsPerTenant.put(tenantId, System.currentTimeMillis());
        if (previousVal == null) {
            newTenantTimeoutTimer(tenantId, tenantIdleTimeout);
        }
    }

    private void newTenantTimeoutTimer(final String tenantId, final long delay) {
        vertx.setTimer(delay, id -> {
            final long lastSend = lastSendTimestampsPerTenant.get(tenantId);
            long remaining = tenantIdleTimeout - (System.currentTimeMillis() - lastSend);
            if (remaining <= 0) {
                if (lastSendTimestampsPerTenant.remove(tenantId, lastSend)) {
                    vertx.eventBus().publish(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT, tenantId);
                    return;
                } else { // had been updated since last get -> timeout needs to be reset
                    remaining = tenantIdleTimeout;
                }
            }
            newTenantTimeoutTimer(tenantId, remaining);
        });
    }
}
