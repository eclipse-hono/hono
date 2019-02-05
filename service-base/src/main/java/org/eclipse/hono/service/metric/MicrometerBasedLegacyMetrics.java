/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.service.metric;

import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Micrometer based legacy metrics implementation.
 */
@Component
@ConditionalOnProperty(name = "hono.metrics.legacy", havingValue = "true")
public final class MicrometerBasedLegacyMetrics implements LegacyMetrics {

    /**
     * The name of the meter for processed messages.
     */
    public static final String METER_MESSAGES_PROCESSED = "hono.messages.processed";

    static final String METER_COMMANDS_DEVICE_DELIVERED = "hono.commands.device.delivered";
    static final String METER_COMMANDS_TTD_EXPIRED = "hono.commands.ttd.expired";
    static final String METER_COMMANDS_RESPONSE_DELIVERED = "hono.commands.response.delivered";
    static final String METER_MESSAGES_UNDELIVERABLE = "hono.messages.undeliverable";

    /**
     * The meter registry.
     */
    private final MeterRegistry registry;

    /**
     * Creates a new metrics instance.
     * 
     * @param registry The meter registry to use.
     * @throws NullPointerException if registry is {@code null}.
     */
    public MicrometerBasedLegacyMetrics(final MeterRegistry registry) {

        this.registry = Objects.requireNonNull(registry);
    }

    @Override
    public void incrementProcessedMessages(final MetricsTags.EndpointType type, final String tenantId) {

        Objects.requireNonNull(type);
        Objects.requireNonNull(tenantId);
        this.registry.counter(METER_MESSAGES_PROCESSED,
                Tags.of(MetricsTags.TAG_TENANT, tenantId).and(type.asTag()))
                .increment();
    }

    @Override
    public void incrementUndeliverableMessages(final MetricsTags.EndpointType type, final String tenantId) {

        Objects.requireNonNull(type);
        Objects.requireNonNull(tenantId);
        this.registry.counter(METER_MESSAGES_UNDELIVERABLE,
                Tags.of(MetricsTags.TAG_TENANT, tenantId).and(type.asTag()))
                .increment();
    }

    @Override
    public void incrementNoCommandReceivedAndTTDExpired(final String tenantId) {

        Objects.requireNonNull(tenantId);
        this.registry.counter(METER_COMMANDS_TTD_EXPIRED,
                Tags.of(MetricsTags.TAG_TENANT, tenantId))
                .increment();
    }

    @Override
    public void incrementCommandResponseDeliveredToApplication(final String tenantId) {

        Objects.requireNonNull(tenantId);
        this.registry.counter(METER_COMMANDS_RESPONSE_DELIVERED,
                Tags.of(MetricsTags.getTenantTag(tenantId)))
                .increment();
    }

    @Override
    public void incrementCommandDeliveredToDevice(final String tenantId) {

        Objects.requireNonNull(tenantId);
        this.registry.counter(METER_COMMANDS_DEVICE_DELIVERED,
                Tags.of(MetricsTags.getTenantTag(tenantId)))
                .increment();
    }
}
