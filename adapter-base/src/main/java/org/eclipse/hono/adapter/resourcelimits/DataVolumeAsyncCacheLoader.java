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

import org.eclipse.hono.service.metric.MetricsTags;
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
 * A Caffeine cache loader that retrieves the overall data volume that devices of a tenant have sent/received
 * via Hono during the current accounting period from a Prometheus server by invoking its HTTP query API.
 */
@RegisterForReflection
public class DataVolumeAsyncCacheLoader extends PrometheusBasedAsyncCacheLoader<LimitedResourceKey, LimitedResource<Long>> {

    private static final String METRIC_NAME_COMMANDS_PAYLOAD_SIZE = String.format("%s_bytes_sum",
            MicrometerBasedMetrics.METER_COMMAND_PAYLOAD.replace(".", "_"));
    private static final String METRIC_NAME_MESSAGES_PAYLOAD_SIZE = String.format("%s_bytes_sum",
            MicrometerBasedMetrics.METER_TELEMETRY_PAYLOAD.replace(".", "_"));

    private static final String QUERY_TEMPLATE_MESSAGE_LIMIT = String.format(
            "floor(sum(increase(%1$s{status=~\"%3$s|%4$s\", tenant=\"%%1$s\"} [%%2$dm]) or vector(0)) + sum(increase(%2$s{status=~\"%3$s|%4$s\", tenant=\"%%1$s\"} [%%2$dm]) or vector(0)))",
            METRIC_NAME_MESSAGES_PAYLOAD_SIZE,
            METRIC_NAME_COMMANDS_PAYLOAD_SIZE,
            MetricsTags.ProcessingOutcome.FORWARDED.asTag().getValue(),
            MetricsTags.ProcessingOutcome.UNPROCESSABLE.asTag().getValue());
    private static final Logger LOG = LoggerFactory.getLogger(DataVolumeAsyncCacheLoader.class);

    /**
     * Creates a new loader.
     *
     * @param webClient The client to use for querying the Prometheus server.
     * @param config The PrometheusBasedResourceLimitChecks configuration object.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public DataVolumeAsyncCacheLoader(
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

        final var span = tracer.buildSpan("determine used data volume")
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
                } else if (tenant.getResourceLimits().getDataVolume() == null) {
                    span.log(Map.of(Fields.MESSAGE, "no message limits configured"));
                    LOG.trace("no data volume limits configured for tenant [{}]", tenant.getTenantId());
                    result.complete(new LimitedResource<>(null, 0L));
                } else {

                    final var dataVolume = tenant.getResourceLimits().getDataVolume();
                    LOG.trace("data volume limit config for tenant [{}] are [{}:{}, {}:{}, {}:{}, {}:{}]", tenant.getTenantId(),
                            TenantConstants.FIELD_MAX_BYTES, dataVolume.getMaxBytes(),
                            TenantConstants.FIELD_EFFECTIVE_SINCE, dataVolume.getEffectiveSince(),
                            TenantConstants.FIELD_PERIOD_MODE, dataVolume.getPeriod().getMode(),
                            TenantConstants.FIELD_PERIOD_NO_OF_DAYS, dataVolume.getPeriod().getNoOfDays());

                    if (dataVolume.isLimited()) {
                        final var nowUtc = Instant.now(clock);
                        final Duration dataUsagePeriod = dataVolume
                                .getElapsedAccountingPeriodDuration(nowUtc);
                        final Long allowedMaxBytes = calculateEffectiveLimit(
                                dataVolume.getEffectiveSince(),
                                nowUtc,
                                dataVolume.getPeriod().getMode(),
                                dataVolume.getMaxBytes());

                        if (dataUsagePeriod.toMinutes() <= 0) {
                            result.complete(new LimitedResource<>(allowedMaxBytes, 0L));
                        } else {
                            final String queryParams = String.format(
                                    QUERY_TEMPLATE_MESSAGE_LIMIT,
                                    key.getTenantId(),
                                    dataUsagePeriod.toMinutes());
                            executeQuery(queryParams, span.context())
                                .onSuccess(v -> result.complete(new LimitedResource<>(allowedMaxBytes, v)))
                                .onFailure(result::completeExceptionally);
                        }
                    } else {
                        span.log(Map.of(Fields.MESSAGE, "tenant's data volume is unlimited"));
                        LOG.trace("data volume is unlimited for tenant [{}]", tenant.getTenantId());
                        result.complete(new LimitedResource<>(null, 0L));
                    }
                }
            });

        return result.whenComplete((count, error) -> {
            span.finish();
        });
    }
}
