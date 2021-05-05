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
package org.eclipse.hono.adapter.resourcelimits.quarkus;

import java.time.Duration;
import java.util.concurrent.Executors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import org.eclipse.hono.adapter.resourcelimits.NoopResourceLimitChecks;
import org.eclipse.hono.adapter.resourcelimits.PrometheusBasedResourceLimitChecks;
import org.eclipse.hono.adapter.resourcelimits.ResourceLimitChecks;

import com.github.benmanes.caffeine.cache.Caffeine;

import io.opentracing.Tracer;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.properties.IfBuildProperty;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * A producer for resource limit checks based on the profile.
 */
@ApplicationScoped
public class ResourceLimitChecksProducer {

    /**
     * Creates resource limit checks based on data retrieved from a Prometheus server
     * via its HTTP API.
     *
     * @param config The configuration properties.
     * @param vertx The vert.x instance to run the web client on.
     * @param tracer The Open Tracing tracer to track the processing of requests.
     * @return The checks.
     */
    @Produces
    @IfBuildProperty(name = "hono.metrics", stringValue = "prometheus")
    public ResourceLimitChecks prometheusResourceLimitChecks(
            final PrometheusBasedResourceLimitChecksConfig config,
            final Vertx vertx,
            final Tracer tracer) {

        if (config.isHostConfigured()) {
            final WebClientOptions webClientOptions = new WebClientOptions();
            webClientOptions.setConnectTimeout(config.getConnectTimeout());
            webClientOptions.setDefaultHost(config.getHost());
            webClientOptions.setDefaultPort(config.getPort());
            webClientOptions.setTrustOptions(config.getTrustOptions());
            webClientOptions.setKeyCertOptions(config.getKeyCertOptions());
            webClientOptions.setSsl(config.isTlsEnabled());

            final Caffeine<Object, Object> builder = Caffeine.newBuilder()
                    // make sure we run one Prometheus query at a time
                    .executor(Executors.newSingleThreadExecutor())
                    .initialCapacity(config.getCacheMinSize())
                    .maximumSize(config.getCacheMaxSize())
                    .expireAfterWrite(Duration.ofSeconds(config.getCacheTimeout()));

            return new PrometheusBasedResourceLimitChecks(
                    WebClient.create(vertx, webClientOptions),
                    config,
                    builder.buildAsync(),
                    builder.buildAsync(),
                    builder.buildAsync(),
                    tracer);
        } else {
            return new NoopResourceLimitChecks();
        }
    }

    /**
     * Creates resource limit checks which always succeed.
     *
     * @return The checks.
     */
    @Produces
    @DefaultBean
    public ResourceLimitChecks noopResourceLimitChecks() {
       return new NoopResourceLimitChecks();
    }

}
