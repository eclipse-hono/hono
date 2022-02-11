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

import java.time.Duration;
import java.time.Instant;
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
 * A Caffeine cache loader that retrieves the overall time that devices of a tenant have been connected to
 * Hono during the current accounting period from a Prometheus server by invoking its HTTP query API.
 */
@RegisterForReflection
public class ConnectionDurationAsyncCacheLoader extends PrometheusBasedAsyncCacheLoader<LimitedResourceKey, LimitedResource<Duration>> {

    private static final String METRIC_NAME_CONNECTIONS_DURATION = String.format("%s_seconds_sum",
            MicrometerBasedMetrics.METER_CONNECTIONS_AUTHENTICATED_DURATION.replace(".", "_"));
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionDurationAsyncCacheLoader.class);

    /**
     * Creates a new loader.
     *
     * @param webClient The client to use for querying the Prometheus server.
     * @param config The PrometheusBasedResourceLimitChecks configuration object.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public ConnectionDurationAsyncCacheLoader(
            final WebClient webClient,
            final PrometheusBasedResourceLimitChecksConfig config,
            final Tracer tracer) {
        super(webClient, config, tracer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<LimitedResource<Duration>> asyncLoad(
            final LimitedResourceKey key,
            final Executor executor) {

        final var span = tracer.buildSpan("determine used connection duration")
                .withTag(Tags.COMPONENT, getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_TENANT_ID.getKey(), key.getTenantId())
                .start();

        final CompletableFuture<LimitedResource<Duration>> result = new CompletableFuture<>();

        key.getTenantInformation(span.context())
            .onFailure(result::completeExceptionally)
            .onSuccess(tenant -> {
                if (tenant.getResourceLimits() == null) {
                    span.log(Map.of(Fields.MESSAGE, "no resource limits configured"));
                    LOG.trace("no resource limits configured for tenant [{}]", tenant.getTenantId());
                    result.complete(new LimitedResource<>(null, Duration.ZERO));
                } else if (tenant.getResourceLimits().getConnectionDuration() == null) {
                    span.log(Map.of(Fields.MESSAGE, "no connection duration limit configured"));
                    LOG.trace("no connection duration limit configured for tenant [{}]", tenant.getTenantId());
                    result.complete(new LimitedResource<>(null, Duration.ZERO));
                } else {

                    final var connectionDuration = tenant.getResourceLimits().getConnectionDuration();

                    LOG.trace("connection duration config for tenant [{}] is [{}:{}, {}:{}, {}:{}, {}:{}]",
                            tenant.getTenantId(),
                            TenantConstants.FIELD_MAX_MINUTES, connectionDuration.getMaxMinutes(),
                            TenantConstants.FIELD_EFFECTIVE_SINCE, connectionDuration.getEffectiveSince(),
                            TenantConstants.FIELD_PERIOD_MODE, connectionDuration.getPeriod().getMode(),
                            TenantConstants.FIELD_PERIOD_NO_OF_DAYS, connectionDuration.getPeriod().getNoOfDays());

                    if (connectionDuration.isLimited()) {

                        final var nowUtc = Instant.now(clock);
                        final Duration connectionDurationUsagePeriod = connectionDuration
                                .getElapsedAccountingPeriodDuration(nowUtc);
                        final Duration allowedMaxDuration = Duration.ofMinutes(calculateEffectiveLimit(
                                connectionDuration.getEffectiveSince(),
                                nowUtc,
                                connectionDuration.getPeriod().getMode(),
                                connectionDuration.getMaxMinutes()));

                        if (connectionDurationUsagePeriod.toMinutes() <= 0) {
                            result.complete(new LimitedResource<>(allowedMaxDuration, Duration.ZERO));
                        } else {
                            final String query = String.format("minute( sum( increase( %s {tenant=\"%s\"} [%dm])))",
                                    METRIC_NAME_CONNECTIONS_DURATION,
                                    key.getTenantId(),
                                    connectionDurationUsagePeriod.toMinutes());
                            executeQuery(query, span.context())
                                .onSuccess(minutesConnected -> result.complete(new LimitedResource<>(
                                            allowedMaxDuration,
                                            Duration.ofMinutes(minutesConnected))))
                                .onFailure(result::completeExceptionally);
                        }
                    } else {
                        span.log(Map.of(Fields.MESSAGE, "connection duration is unlimited"));
                        LOG.trace("connection duration is unlimited for tenant [{}]", tenant.getTenantId());
                        result.complete(new LimitedResource<>(null, Duration.ZERO));
                    }
                }
            });

        return result.whenComplete((duration, error) -> {
            span.finish();
        });
    }

}
