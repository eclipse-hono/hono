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
package org.eclipse.hono.adapter.mqtt.quarkus;

import java.util.Collections;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.eclipse.hono.adapter.mqtt.MicrometerBasedMqttAdapterMetrics;
import org.eclipse.hono.service.metric.PrometheusScrapingResource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.properties.IfBuildProperty;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

/**
 * A factory class that creates a proper metrics based on the profile.
 */
@ApplicationScoped
public class MetricsFactory {

    @Inject
    Vertx vertx;

    @Inject
    MeterRegistry registry;

    @Produces
    @IfBuildProperty(name = "hono.metrics", stringValue = "prometheus")
    List<Handler<Router>> scrapingResource() {
        return Collections.singletonList(new PrometheusScrapingResource((PrometheusMeterRegistry) registry));
    }

    @Produces
    @DefaultBean
    List<Handler<Router>> emptyResources() {
        return Collections.EMPTY_LIST;
    }

    @Produces
    MicrometerBasedMqttAdapterMetrics metrics() {
        return new MicrometerBasedMqttAdapterMetrics(registry, vertx);
    }

}

