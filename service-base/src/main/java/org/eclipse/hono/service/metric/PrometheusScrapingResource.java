/**
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
 */


package org.eclipse.hono.service.metric;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * A vert.x HTTP resource for scraping Micrometer's {@code PrometheusMeterRegistry}.
 *
 */
@Component
@Qualifier("healthchecks")
@ConditionalOnClass(name = "io.micrometer.prometheus.PrometheusMeterRegistry")
public class PrometheusScrapingResource implements Handler<Router> {

    private final PrometheusMeterRegistry registry;

    private String uri = "/prometheus";

    /**
     * Creates a new resource for a registry.
     * 
     * @param registry The registry.
     * @throws NullPointerException if the registry is {@code null}.
     */
    public PrometheusScrapingResource(final PrometheusMeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    /**
     * Scrapes the registry.
     * 
     * @param event The request.
     */
    private void scrape(final RoutingContext event) {
        final String stats = registry.scrape();
        event.response().setStatusCode(HttpURLConnection.HTTP_OK).end(stats);
    }

    /**
     * Registers this resource.
     * 
     * @param router The router to register on.
     */
    @Override
    public void handle(final Router router) {
        Objects.requireNonNull(router);
        router.get(uri).handler(this::scrape);
    }

    @Override
    public String toString() {
        return String.format("Prometheus Registry Scraper [endpoint: %s]", uri);
    }
}
