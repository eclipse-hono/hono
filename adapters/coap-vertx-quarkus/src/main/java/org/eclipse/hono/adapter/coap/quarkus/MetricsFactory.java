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
package org.eclipse.hono.adapter.coap.quarkus;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.eclipse.hono.adapter.coap.MicrometerBasedCoapAdapterMetrics;
import org.eclipse.hono.config.ProtocolAdapterProperties;
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
    MicrometerBasedCoapAdapterMetrics metrics(
            final Vertx vertx,
            final MeterRegistry registry,
            final ProtocolAdapterProperties adapterProperties) {
        registry.config().commonTags(MetricsTags.forProtocolAdapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP));
        final var metrics = new MicrometerBasedCoapAdapterMetrics(registry, vertx);
        metrics.setProtocolAdapterProperties(adapterProperties);
        return metrics;
    }
}
