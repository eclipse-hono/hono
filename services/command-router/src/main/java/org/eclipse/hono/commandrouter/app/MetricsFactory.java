/**
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.commandrouter.app;

import org.eclipse.hono.commandrouter.MicrometerBasedCommandRouterMetrics;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * A factory class that creates Command Router specific metrics.
 */
@ApplicationScoped
public class MetricsFactory {

    @Produces
    @Singleton
    MeterFilter commonTags() {
        return MeterFilter.commonTags(MetricsTags.forService(Constants.SERVICE_NAME_COMMAND_ROUTER));
    }

    @Singleton
    @Produces
    MicrometerBasedCommandRouterMetrics metrics(
            final Vertx vertx,
            final MeterRegistry registry) {
        return new MicrometerBasedCommandRouterMetrics(registry, vertx);
    }
}
