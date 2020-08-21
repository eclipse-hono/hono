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
package org.eclipse.hono.adapter.http.quarkus;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.properties.IfBuildProperty;

/**
 * A factory class that creates micrometer registry based on the profile.
 */
@ApplicationScoped
public class MetricsRegistryFactory {

    @Produces
    @IfBuildProperty(name = "hono.metrics", stringValue = "prometheus")
    MeterRegistry prometheusRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    @Produces
    @DefaultBean
    MeterRegistry simpleRegistry() {
        return new SimpleMeterRegistry();
    }


}
