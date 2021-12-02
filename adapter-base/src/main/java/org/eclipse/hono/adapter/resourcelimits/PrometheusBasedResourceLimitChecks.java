/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.adapter.resourcelimits;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * Resource limit checks which compare configured limits to live metrics retrieved
 * from a <em>Prometheus</em> server.
 */
public final class PrometheusBasedResourceLimitChecks implements ResourceLimitChecks {

    private static final Logger LOG = LoggerFactory.getLogger(PrometheusBasedResourceLimitChecks.class);
    private static final String EVENT_QUERY_STILL_RUNNING = "query still running, using default value";
    private static final String LABEL_LIMIT_EXCEEDED = "limit exceeded";

    private final Tracer tracer;
    private final TenantClient tenantClient;
    private final AsyncLoadingCache<LimitedResourceKey, LimitedResource<Long>> connectionCountCache;
    private final AsyncLoadingCache<LimitedResourceKey, LimitedResource<Duration>> connectionDurationCache;
    private final AsyncLoadingCache<LimitedResourceKey, LimitedResource<Long>> dataVolumeCache;

    /**
     * Creates new checks.
     *
     * @param connectionCountCache The cache to use for a tenant's overall number of connected devices.
     * @param connectionDurationCache The cache to use for a tenant's devices' overall connection time.
     * @param dataVolumeCache The cache to use for a tenant's devices' overall amount of data transferred.
     * @param tenantClient The client to use for retrieving information from Hono's Tenant service.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public PrometheusBasedResourceLimitChecks(
            final AsyncLoadingCache<LimitedResourceKey, LimitedResource<Long>> connectionCountCache,
            final AsyncLoadingCache<LimitedResourceKey, LimitedResource<Duration>> connectionDurationCache,
            final AsyncLoadingCache<LimitedResourceKey, LimitedResource<Long>> dataVolumeCache,
            final TenantClient tenantClient) {

        this(
                connectionCountCache,
                connectionDurationCache,
                dataVolumeCache,
                tenantClient,
                NoopTracerFactory.create());
    }

    /**
     * Creates new checks.
     *
     * @param connectionCountCache The cache to use for a tenant's overall number of connected devices.
     * @param connectionDurationCache The cache to use for a tenant's devices' overall connection time.
     * @param dataVolumeCache The cache to use for a tenant's devices' overall amount of data transferred.
     * @param tenantClient The client to use for retrieving information from Hono's Tenant service.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public PrometheusBasedResourceLimitChecks(
            final AsyncLoadingCache<LimitedResourceKey, LimitedResource<Long>> connectionCountCache,
            final AsyncLoadingCache<LimitedResourceKey, LimitedResource<Duration>> connectionDurationCache,
            final AsyncLoadingCache<LimitedResourceKey, LimitedResource<Long>> dataVolumeCache,
            final TenantClient tenantClient,
            final Tracer tracer) {

        this.connectionCountCache = Objects.requireNonNull(connectionCountCache);
        this.connectionDurationCache = Objects.requireNonNull(connectionDurationCache);
        this.dataVolumeCache = Objects.requireNonNull(dataVolumeCache);
        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.tracer = Objects.requireNonNull(tracer);
    }

    @Override
    public Future<Boolean> isConnectionLimitReached(final TenantObject tenant, final SpanContext spanContext) {

        Objects.requireNonNull(tenant);

        final var span = TracingHelper.buildChildSpan(tracer, spanContext, "verify connection limit", getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_TENANT_ID.getKey(), tenant.getTenantId())
                .start();
        final Promise<Boolean> result = Promise.promise();

        TracingHelper.TAG_CACHE_HIT.set(span, Boolean.FALSE);

        final var value = connectionCountCache.get(new LimitedResourceKey(tenant.getTenantId(), tenantClient::get));

        if (value.isDone()) {
            try {
                final var limitedResource = value.get();
                TracingHelper.TAG_CACHE_HIT.set(span, Boolean.TRUE);
                span.log(Map.of(
                        TenantConstants.FIELD_MAX_CONNECTIONS, Optional.ofNullable(limitedResource.getCurrentLimit())
                            .map(String::valueOf)
                            .orElse("N/A"),
                        "current-connections", limitedResource.getCurrentValue()));
                final boolean isExceeded = Optional.ofNullable(limitedResource.getCurrentLimit())
                        .map(limit -> limitedResource.getCurrentValue() >= limit)
                        .orElse(false);
                result.complete(isExceeded);
            } catch (InterruptedException | ExecutionException e) {
                // this means that the query could not be run successfully
                TracingHelper.logError(span, e);
                // fall back to default value
                result.complete(Boolean.FALSE);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            LOG.trace("Prometheus query [tenant: {}] still running, using default value", tenant.getTenantId());
            span.log(EVENT_QUERY_STILL_RUNNING);
            result.complete(Boolean.FALSE);
        }

        return result.future()
                .onSuccess(b -> {
                    span.log(Map.of(LABEL_LIMIT_EXCEEDED, b));
                    span.finish();
                });
    }

    /**
     * Checks if the maximum data volume for the messages configured for a tenant
     * have been reached.
     * <p>
     * There are two supported modes of calculation which are <em>days</em> and 
     * <em>monthly</em> and can be set using {@link TenantConstants#FIELD_PERIOD_MODE}. 
     * <p>
     * In the <em>monthly</em> mode, the data usage is calculated from the 
     * beginning till the end of the current (Gregorian) calendar month and 
     * compared with the maxBytes already set using {@link TenantConstants#FIELD_MAX_BYTES}.
     * There is an exception to this calculation in the first month on which this
     * limit becomes effective. In this case, the maxBytes value is recalculated 
     * based on the remaining days in the month from the effectiveSince date.
     * The data usage for these remaining days is then compared with the above 
     * recalculated value.
     * <p>
     * If the mode is set as <em>days</em> then the data usage is calculated 
     * based on the number of days set using {@link TenantConstants#FIELD_PERIOD_NO_OF_DAYS} 
     * and compared with the maxBytes.
     *
     * @param tenant The tenant configuration to check the limit against.
     * @param payloadSize The message payload size in bytes.
     * @param spanContext The currently active OpenTracing span context that is used to
     *                    trace the limits verification or {@code null}
     *                    if no span is currently active.
     * @throws NullPointerException if the tenant object is null.
     * @return A future indicating the outcome of the check.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}
     *         if the check could not be performed.
     */
    @Override
    public Future<Boolean> isMessageLimitReached(
            final TenantObject tenant,
            final long payloadSize,
            final SpanContext spanContext) {

        Objects.requireNonNull(tenant);

        final var span = TracingHelper.buildChildSpan(tracer, spanContext, "verify message limit", getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_TENANT_ID.getKey(), tenant.getTenantId())
                .start();
        span.log(Map.of("payload-size", payloadSize));

        final Promise<Boolean> result = Promise.promise();
        if (payloadSize <= 0) {
            result.complete(Boolean.FALSE);
        } else {
            TracingHelper.TAG_CACHE_HIT.set(span, false);

            final var value = dataVolumeCache.get(new LimitedResourceKey(tenant.getTenantId(), tenantClient::get));

            if (value.isDone()) {
                try {
                    final var limitedResource = value.get();
                    TracingHelper.TAG_CACHE_HIT.set(span, true);
                    span.log(Map.of(
                            "current period bytes limit", Optional.ofNullable(limitedResource.getCurrentLimit())
                            .map(String::valueOf)
                            .orElse("N/A"),
                            "current period bytes consumed", limitedResource.getCurrentValue()));
                    final boolean isExceeded = Optional.ofNullable(limitedResource.getCurrentLimit())
                            .map(limit -> (limitedResource.getCurrentValue() + payloadSize) > limit)
                            .orElse(false);
                    result.complete(isExceeded);
                } catch (InterruptedException | ExecutionException e) {
                    // this means that the query could not be run successfully
                    TracingHelper.logError(span, e);
                    // fall back to default value
                    result.complete(Boolean.FALSE);
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            } else {
                LOG.trace("Prometheus query [tenant: {}] still running, using default value", tenant.getTenantId());
                span.log(Map.of(Fields.MESSAGE, EVENT_QUERY_STILL_RUNNING));
                result.complete(Boolean.FALSE);
            }
        }
        return result.future()
                .onSuccess(b -> {
                    span.log(Map.of(LABEL_LIMIT_EXCEEDED, b));
                    span.finish();
                });
    }

    @Override
    public Future<Boolean> isConnectionDurationLimitReached(
            final TenantObject tenant,
            final SpanContext spanContext) {

        Objects.requireNonNull(tenant);

        final var span = TracingHelper.buildChildSpan(tracer, spanContext, "verify connection duration limit", getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_TENANT_ID.getKey(), tenant.getTenantId())
                .start();

        final Promise<Boolean> result = Promise.promise();
        TracingHelper.TAG_CACHE_HIT.set(span, false);
        final var key = new LimitedResourceKey(tenant.getTenantId(), tenantClient::get);
        final var value = connectionDurationCache.get(key);

        if (value.isDone()) {
            try {
                final var limitedResource = value.get();
                TracingHelper.TAG_CACHE_HIT.set(span, true);
                span.log(Map.of(
                        "current period's connection duration limit", Optional.ofNullable(limitedResource.getCurrentLimit())
                        .map(String::valueOf)
                        .orElse("N/A"),
                        "current period's connection duration consumed", limitedResource.getCurrentValue()));
                final boolean isExceeded = Optional.ofNullable(limitedResource.getCurrentLimit())
                        .map(limit -> limitedResource.getCurrentValue().compareTo(limit) >= 0)
                        .orElse(false);
                result.complete(isExceeded);
            } catch (InterruptedException | ExecutionException e) {
                // this means that the query could not be run successfully
                TracingHelper.logError(span, e);
                // fall back to default value
                result.complete(Boolean.FALSE);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            LOG.trace("Prometheus query [tenant: {}] still running, using default value", tenant.getTenantId());
            span.log(Map.of(Fields.MESSAGE, EVENT_QUERY_STILL_RUNNING));
            result.complete(Boolean.FALSE);
        }
        return result.future()
                .onSuccess(b -> {
                    span.log(Map.of(LABEL_LIMIT_EXCEEDED, b));
                    span.finish();
                });
    }
}
