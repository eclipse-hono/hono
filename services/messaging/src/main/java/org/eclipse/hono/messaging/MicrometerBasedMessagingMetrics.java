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

import org.eclipse.hono.service.metric.MicrometerBasedMetrics;
import org.springframework.stereotype.Component;

import com.google.common.util.concurrent.AtomicDouble;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Micrometer based metrics for Hono Messaging.
 */
@Component
public class MicrometerBasedMessagingMetrics extends MicrometerBasedMetrics implements MessagingMetrics {

    private final Map<String, AtomicDouble> downstreamLinkCredits = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> downstreamSenders = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> upstreamLinks = new ConcurrentHashMap<>();

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

        this.downstreamConnections = registry.gauge("connections.downstream", new AtomicLong());
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
    public final void submitDownstreamLinkCredits(final String address, final double credits) {

        gaugeForAddress("hono.link.downstream.credits", this.downstreamLinkCredits, address, AtomicDouble::new)
                .set(credits);

    }

    @Override
    public final void incrementDownstreamSenders(final String address) {

        gaugeForAddress("hono.senders.downstream", this.downstreamSenders, address, AtomicLong::new)
                .incrementAndGet();

    }

    @Override
    public final void decrementDownstreamSenders(final String address) {

        gaugeForAddress("hono.senders.downstream", this.downstreamSenders, address, AtomicLong::new)
                .decrementAndGet();

    }

    @Override
    public final void incrementUpstreamLinks(final String address) {

        gaugeForAddress("hono.receivers.upstream.links", this.upstreamLinks, address, AtomicLong::new)
                .incrementAndGet();

    }

    @Override
    public final void decrementUpstreamLinks(final String address) {

        gaugeForAddress("hono.receivers.upstream.links", this.upstreamLinks, address, AtomicLong::new)
                .decrementAndGet();

    }

    @Override
    public final void incrementDiscardedMessages(final String address) {

        this.registry.counter("hono.messages.discarded",
                Tags
                        .of("address", normalizeAddress(address)))
                .increment();

    }

    @Override
    public final void incrementProcessedMessages(final String address) {

        this.registry.counter("hono.messages.processed",
                Tags
                        .of("address", normalizeAddress(address)))
                .increment();

    }

    @Override
    public final void incrementUndeliverableMessages(final String address) {

        this.registry.counter("hono.messages.underliverable",
                Tags
                        .of("address", normalizeAddress(address)))
                .increment();

    }

    protected <T extends Number> T gaugeForAddress(final String name, final Map<String, T> map, final String address,
            final Supplier<T> instanceSupplier) {

        return gaugeForKey(name, map, address, Tags.of("address", normalizeAddress(address)), instanceSupplier);

    }

    private static String normalizeAddress(final String address) {
        return address.replace('/', '.');
    }

}
