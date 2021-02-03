/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.config.quarkus.ServerConfig;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.VertxBasedHealthCheckServer;
import org.eclipse.hono.service.metric.PrometheusScrapingResource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.config.ConfigPrefix;
import io.quarkus.arc.properties.IfBuildProperty;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

/**
 * Produces a {@link HealthCheckServer} exposing resources for checking the component's
 * readiness and liveness status.
 * <p>
 * The server will also expose a resource allowing a Prometheus server to retrieve metrics
 * if the <em>hono.metrics</em> Quarkus build property is set to value <em>prometheus</em>.
 *
 */
public class HealthCheckServerProducer {

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

    /**
     * Creates a new Health Check server.
     *
     * @param vertx The vert.x instance to use.
     * @param healthCheckkServerConfig The configuration properties for the health check server.
     * @param additionalResources Additional resources that the server should expose.
     * @return The server.
     */
    @Singleton
    @Produces
    HealthCheckServer healthCheckServer(
            final Vertx vertx,
            @ConfigPrefix("hono.healthCheck")
            final ServerConfig healthCheckkServerConfig,
            final List<Handler<Router>> additionalResources) {
        final VertxBasedHealthCheckServer server = new VertxBasedHealthCheckServer(vertx, healthCheckkServerConfig);
        server.setAdditionalResources(additionalResources);
        return server;
    }
}
