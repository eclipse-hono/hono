/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.lora.quarkus;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.eclipse.hono.adapter.http.MicrometerBasedHttpAdapterMetrics;
import org.eclipse.hono.adapter.lora.LoraProtocolAdapterOptions;
import org.eclipse.hono.adapter.lora.LoraProtocolAdapterProperties;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;

/**
 * A factory class that creates protocol adapter specific metrics.
 */
@ApplicationScoped
public class MetricsFactory {

    @Singleton
    @Produces
    LoraProtocolAdapterProperties adapterProperties(final LoraProtocolAdapterOptions adapterOptions) {
        return new LoraProtocolAdapterProperties(adapterOptions);
    }

    @Singleton
    @Produces
    MicrometerBasedHttpAdapterMetrics metrics(
            final Vertx vertx,
            final MeterRegistry registry,
            final LoraProtocolAdapterProperties adapterProperties) {
        registry.config().commonTags(MetricsTags.forProtocolAdapter(Constants.PROTOCOL_ADAPTER_TYPE_LORA));
        final var metrics = new MicrometerBasedHttpAdapterMetrics(registry, vertx);
        metrics.setProtocolAdapterProperties(adapterProperties);
        return metrics;
    }
}
