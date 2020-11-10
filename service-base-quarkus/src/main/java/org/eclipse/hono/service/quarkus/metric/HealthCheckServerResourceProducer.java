/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.quarkus.metric;

import java.util.List;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.eclipse.hono.service.metric.PrometheusScrapingResource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.properties.IfBuildProperty;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;

/**
 * A producer for additional resources to be provided by
 * a {@code HealthCheckServer}.
 *
 */
public class HealthCheckServerResourceProducer {

    @Singleton
    @Produces
    @IfBuildProperty(name = "hono.metrics", stringValue = "prometheus")
    List<Handler<Router>> scrapingResource(final MeterRegistry registry) {
        return List.of(new PrometheusScrapingResource((PrometheusMeterRegistry) registry));
    }

    @Singleton
    @Produces
    @DefaultBean
    List<Handler<Router>> emptyResources() {
        return List.of();
    }
}
