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

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.properties.IfBuildProperty;

/**
 * A producer for a Micrometer meter registry based on the profile.
 */
public class MeterRegistryProducer {

    private final Logger LOG = LoggerFactory.getLogger(MeterRegistryProducer.class);

    @Singleton
    @Produces
    @IfBuildProperty(name = "hono.metrics", stringValue = "prometheus")
    MeterRegistry prometheusRegistry() {
        LOG.info("creating PrometheusMeterRegistry");
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    @Singleton
    @Produces
    @DefaultBean
    MeterRegistry simpleRegistry() {
        LOG.info("creating SimpleMeterRegistry");
        return new SimpleMeterRegistry();
    }
}
