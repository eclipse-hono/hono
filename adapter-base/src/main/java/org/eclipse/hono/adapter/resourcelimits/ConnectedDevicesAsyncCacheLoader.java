/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.resourcelimits;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.eclipse.hono.service.metric.MicrometerBasedMetrics;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.TenantConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.ext.web.client.WebClient;

/**
 * A Caffeine cache loader that retrieves the overall number of connected devices for a tenant
 * from a Prometheus server by invoking its HTTP query API.
 */
@RegisterForReflection
public class ConnectedDevicesAsyncCacheLoader extends PrometheusBasedAsyncCacheLoader<LimitedResourceKey, LimitedResource<Long>> {

    private static final String METRIC_NAME_CONNECTIONS = MicrometerBasedMetrics.METER_CONNECTIONS_AUTHENTICATED
            .replace(".", "_");
    private static final Logger LOG = LoggerFactory.getLogger(ConnectedDevicesAsyncCacheLoader.class);

    /**
     * Creates a new loader.
     *
     * @param webClient The client to use for querying the Prometheus server.
     * @param config The PrometheusBasedResourceLimitChecks configuration object.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public ConnectedDevicesAsyncCacheLoader(
            final WebClient webClient,
            final PrometheusBasedResourceLimitChecksConfig config,
            final Tracer tracer) {
        super(webClient, config, tracer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<LimitedResource<Long>> asyncLoad(
            final LimitedResourceKey key,
            final Executor executor) {

        final var span = tracer.buildSpan("determine number of connected devices")
                .withTag(Tags.COMPONENT, getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_TENANT_ID.getKey(), key.getTenantId())
                .start();

        final CompletableFuture<LimitedResource<Long>> result = new CompletableFuture<>();

        key.getTenantInformation(span.context())
            .onFailure(result::completeExceptionally)
            .onSuccess(tenant -> {
                if (tenant.getResourceLimits() == null) {
                    span.log(Map.of(Fields.MESSAGE, "no resource limits configured"));
                    LOG.trace("no resource limits configured for tenant [{}]", tenant.getTenantId());
                    result.complete(new LimitedResource<>(null, 0L));
                } else {
                    final long maxConnections = tenant.getResourceLimits().getMaxConnections();
                    LOG.trace("connection limit for tenant [{}] is [{}]", tenant.getTenantId(), maxConnections);

                    if (maxConnections == TenantConstants.UNLIMITED_CONNECTIONS) {
                        span.log(Map.of(Fields.MESSAGE, "no connection limit configured"));
                        result.complete(new LimitedResource<>(null, 0L));
                    } else {
                        final String queryParams = String.format("sum(%s{tenant=\"%s\"})", METRIC_NAME_CONNECTIONS, key.getTenantId());
                        executeQuery(queryParams, span.context())
                            .onSuccess(v -> result.complete(new LimitedResource<>(maxConnections, v)))
                            .onFailure(result::completeExceptionally);
                    }
                }
            });

        return result.whenComplete((count, error) -> {
            span.finish();
        });
    }

}
