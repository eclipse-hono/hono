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


package org.eclipse.hono.service.metric.spring;

import org.eclipse.hono.service.metric.PrometheusScrapingResource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;

/**
 * Beans that are relevant for Prometheus based metrics only.
 *
 */
@Configuration
@ConditionalOnClass(name = "io.micrometer.prometheus.PrometheusMeterRegistry")
public class PrometheusSupport {

    /**
     * Creates a web resource that can be scraped by a Prometheus server.
     *
     * @param registry The Prometheus specific meter registry to scrape.
     * @return The resource.
     */
    @Qualifier("healthchecks")
    @Bean
    public Handler<Router> prometheusScrapingResource(final PrometheusMeterRegistry registry) {
        return new PrometheusScrapingResource(registry);
    }
}
