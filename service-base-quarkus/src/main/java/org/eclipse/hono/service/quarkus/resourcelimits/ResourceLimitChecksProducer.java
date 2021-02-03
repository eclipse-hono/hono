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
package org.eclipse.hono.service.quarkus.resourcelimits;

import java.time.Duration;

import javax.enterprise.inject.Produces;

import org.eclipse.hono.service.resourcelimits.NoopResourceLimitChecks;
import org.eclipse.hono.service.resourcelimits.PrometheusBasedResourceLimitChecks;
import org.eclipse.hono.service.resourcelimits.ResourceLimitChecks;

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
public class ResourceLimitChecksProducer {

    @Produces
    @IfBuildProperty(name = "hono.metrics", stringValue = "prometheus")
    ResourceLimitChecks prometheusResourceLimitChecks(
            final PrometheusBasedResourceLimitChecksConfig config,
            final Vertx vertx,
            final Tracer tracer) {

        if (config.isHostConfigured()) {
            final WebClientOptions webClientOptions = new WebClientOptions();
            webClientOptions.setDefaultHost(config.getHost());
            webClientOptions.setDefaultPort(config.getPort());
            webClientOptions.setTrustOptions(config.getTrustOptions());
            webClientOptions.setKeyCertOptions(config.getKeyCertOptions());
            webClientOptions.setSsl(config.isTlsEnabled());

            final Caffeine<Object, Object> builder = Caffeine.newBuilder()
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

    @Produces
    @DefaultBean
    ResourceLimitChecks noopResourceLimitChecks() {
       return new NoopResourceLimitChecks();
    }

}
