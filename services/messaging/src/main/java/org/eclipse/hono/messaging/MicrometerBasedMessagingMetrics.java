/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.messaging;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MicrometerBasedMetrics;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.stereotype.Component;

import com.google.common.util.concurrent.AtomicDouble;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Micrometer based metrics for Hono Messaging.
 */
@Component
public class MicrometerBasedMessagingMetrics extends MicrometerBasedMetrics implements MessagingMetrics {

    static final String METER_CONNECTIONS_DOWNSTREAM = "hono.connections.downstream";
    static final String METER_DOWNSTREAM_SENDERS = "hono.senders.downstream";
    static final String METER_DOWNSTREAM_LINK_CREDITS = "hono.link.downstream.credits";
    static final String METER_UPSTREAM_LINKS = "hono.receivers.upstream.links";

    static final String METER_MESSAGES_DISCARDED = "hono.messages.discarded";
    static final String METER_MESSAGES_UNDELIVERABLE = "hono.messages.undeliverable";

    private final Map<ResourceIdentifier, AtomicDouble> downstreamLinkCredits = new ConcurrentHashMap<>();
    private final Map<ResourceIdentifier, AtomicLong> downstreamSenders = new ConcurrentHashMap<>();
    private final Map<ResourceIdentifier, AtomicLong> upstreamLinks = new ConcurrentHashMap<>();

    private final AtomicLong downstreamConnections;

    /**
     * Create a new metrics instance for messaging services.
     * 
     * @param registry The meter registry to use.
     * 
     * @throws NullPointerException if either parameter is {@code null}.
     */
    public MicrometerBasedMessagingMetrics(final MeterRegistry registry) {
        super(registry);

        Objects.requireNonNull(registry);

        this.downstreamConnections = registry.gauge(METER_CONNECTIONS_DOWNSTREAM, new AtomicLong());
    }

    @Override
    public final void incrementDownStreamConnections() {
        this.downstreamConnections.incrementAndGet();
    }

    @Override
    public final void decrementDownStreamConnections() {
        this.downstreamConnections.decrementAndGet();
    }

    @Override
    public final void submitDownstreamLinkCredits(final ResourceIdentifier address, final double credits) {

        gaugeForAddress(METER_DOWNSTREAM_LINK_CREDITS, this.downstreamLinkCredits, address, AtomicDouble::new)
                .set(credits);

    }

    @Override
    public final void incrementDownstreamSenders(final ResourceIdentifier address) {

        gaugeForAddress(METER_DOWNSTREAM_SENDERS, this.downstreamSenders, address, AtomicLong::new)
                .incrementAndGet();

    }

    @Override
    public final void decrementDownstreamSenders(final ResourceIdentifier address) {

        gaugeForAddress(METER_DOWNSTREAM_SENDERS, this.downstreamSenders, address, AtomicLong::new)
                .decrementAndGet();

    }

    @Override
    public final void incrementUpstreamLinks(final ResourceIdentifier address) {

        gaugeForAddress(METER_UPSTREAM_LINKS, this.upstreamLinks, address, AtomicLong::new)
                .incrementAndGet();

    }

    @Override
    public final void decrementUpstreamLinks(final ResourceIdentifier address) {

        gaugeForAddress(METER_UPSTREAM_LINKS, this.upstreamLinks, address, AtomicLong::new)
                .decrementAndGet();

    }

    @Override
    public final void incrementDiscardedMessages(final ResourceIdentifier address) {

        this.registry.counter(
                METER_MESSAGES_DISCARDED,
                Tags.of(MetricsTags.TAG_TYPE, address.getEndpoint()).and(MetricsTags.TAG_TENANT, address.getTenantId()))
        .increment();
    }

    @Override
    public final void incrementProcessedMessages(final ResourceIdentifier address) {

        incrementProcessedMessages(address.getEndpoint(), address.getTenantId());
    }

    @Override
    public final void incrementUndeliverableMessages(final ResourceIdentifier address) {

        this.registry.counter(
                METER_MESSAGES_UNDELIVERABLE,
                Tags.of(MetricsTags.TAG_TYPE, address.getEndpoint()).and(MetricsTags.TAG_TENANT, address.getTenantId()))
        .increment();
    }

    private <T extends Number> T gaugeForAddress(
            final String name,
            final Map<ResourceIdentifier, T> map,
            final ResourceIdentifier address,
            final Supplier<T> instanceSupplier) {

        final Tags tags = Tags.of(MetricsTags.TAG_TYPE, address.getEndpoint())
                .and(MetricsTags.TAG_TENANT, address.getTenantId());
        return gaugeForKey(name, map, address, tags, instanceSupplier);

    }
}
