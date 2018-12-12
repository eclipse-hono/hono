/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.service.metric;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Micrometer based metrics implementation.
 */
public abstract class MicrometerBasedMetrics implements Metrics {

    /**
     * The meter registry.
     */
    protected final MeterRegistry registry;

    private final Map<String, AtomicLong> authenticatedConnections = new ConcurrentHashMap<>();
    private final AtomicLong unauthenticatedConnections;

    /**
     * Creates a new metrics instance.
     * 
     * @param registry The meter registry to use.
     * @throws NullPointerException if registry is {@code null}.
     */
    public MicrometerBasedMetrics(final MeterRegistry registry) {

        Objects.requireNonNull(registry);

        this.registry = registry;
        this.unauthenticatedConnections = registry.gauge("hono.connections.unauthenticated", new AtomicLong());
    }

    @Override
    public final void incrementConnections(final String tenantId) {

        gaugeForTenant("hono.connections.authenticated", this.authenticatedConnections, tenantId, AtomicLong::new)
                .incrementAndGet();

    }

    @Override
    public final void decrementConnections(final String tenantId) {

        gaugeForTenant("hono.connections.authenticated", this.authenticatedConnections, tenantId, AtomicLong::new)
                .decrementAndGet();

    }

    @Override
    public final void incrementUnauthenticatedConnections() {
        this.unauthenticatedConnections.incrementAndGet();
    }

    @Override
    public final void decrementUnauthenticatedConnections() {
        this.unauthenticatedConnections.decrementAndGet();
    }

    @Override
    public final void incrementProcessedMessages(final String type, final String tenantId) {

        this.registry.counter("hono.messages.processed",
                Tags.of(MetricsTags.TAG_TENANT, tenantId).and(MetricsTags.TAG_TYPE, type))
                .increment();

    }

    @Override
    public final void incrementUndeliverableMessages(final String type, final String tenantId) {

        this.registry.counter("hono.messages.undeliverable",
                Tags.of(MetricsTags.TAG_TENANT, tenantId).and(MetricsTags.TAG_TYPE, type))
                .increment();

    }

    @Override
    public final void incrementProcessedPayload(final String type, final String tenantId,
            final long payloadSize) {

        if (payloadSize < 0) {
            // A negative size would mess up the metrics
            return;
        }

        this.registry.counter("hono.messages.processed.payload",
                Tags.of(MetricsTags.TAG_TENANT, tenantId).and(MetricsTags.TAG_TYPE, type))
                .increment(payloadSize);
    }

    @Override
    public final void incrementCommandDeliveredToDevice(final String tenantId) {

        this.registry.counter("hono.commands.device.delivered",
                Tags.of(MetricsTags.TAG_TENANT, tenantId))
                .increment();

    }

    @Override
    public final void incrementNoCommandReceivedAndTTDExpired(final String tenantId) {

        this.registry.counter("hono.commands.ttd.expired",
                Tags.of(MetricsTags.TAG_TENANT, tenantId))
                .increment();

    }

    @Override
    public final void incrementCommandResponseDeliveredToApplication(final String tenantId) {

        this.registry.counter("hono.commands.response.delivered",
                Tags.of(MetricsTags.TAG_TENANT, tenantId))
                .increment();

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
     * is created using the given name and the {@link MetricsTags#TAG_TENANT} tag.
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

        return gaugeForKey(name, map, tenant, Tags.of(MetricsTags.TAG_TENANT, tenant), instanceSupplier);

    }
}
