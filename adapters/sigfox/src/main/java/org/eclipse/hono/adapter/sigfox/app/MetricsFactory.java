/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.sigfox.app;

import org.eclipse.hono.adapter.http.MicrometerBasedHttpAdapterMetrics;
import org.eclipse.hono.adapter.sigfox.impl.SigfoxProtocolAdapterOptions;
import org.eclipse.hono.adapter.sigfox.impl.SigfoxProtocolAdapterProperties;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * A factory class that creates protocol adapter specific metrics.
 */
@ApplicationScoped
public class MetricsFactory {

    @Singleton
    @Produces
    SigfoxProtocolAdapterProperties adapterProperties(final SigfoxProtocolAdapterOptions adapterOptions) {
        if (!adapterOptions.httpAdapterOptions().adapterOptions().authenticationRequired()) {
            throw new IllegalStateException(
                    "SigFox Protocol Adapter does not support unauthenticated mode. Please change your configuration accordingly.");
        }
        return new SigfoxProtocolAdapterProperties(adapterOptions);
    }

    @Produces
    @Singleton
    MeterFilter commonTags() {
        return MeterFilter.commonTags(MetricsTags.forProtocolAdapter(Constants.PROTOCOL_ADAPTER_TYPE_SIGFOX));
    }

    @Singleton
    @Produces
    MicrometerBasedHttpAdapterMetrics metrics(
            final Vertx vertx,
            final MeterRegistry registry,
            final SigfoxProtocolAdapterProperties adapterProperties) {
        return new MicrometerBasedHttpAdapterMetrics(registry, vertx, adapterProperties);
    }
}
