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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.adapter.metric.MicrometerBasedMetrics;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.ConnectionDuration;
import org.eclipse.hono.util.DataVolume;
import org.eclipse.hono.util.ResourceLimitsPeriod;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.AsyncCache;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.tag.Tags;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;

/**
 * Resource limit checks which compare configured limits with live metrics retrieved
 * from a <em>Prometheus</em> server.
 */
public final class PrometheusBasedResourceLimitChecks implements ResourceLimitChecks {

    private static final Logger LOG = LoggerFactory.getLogger(PrometheusBasedResourceLimitChecks.class);

    private static final String METRIC_NAME_COMMANDS_PAYLOAD_SIZE = String.format("%s_bytes_sum",
            MicrometerBasedMetrics.METER_COMMANDS_PAYLOAD.replace(".", "_"));
    private static final String METRIC_NAME_CONNECTIONS = MicrometerBasedMetrics.METER_CONNECTIONS_AUTHENTICATED
            .replace(".", "_");
    private static final String METRIC_NAME_CONNECTIONS_DURATION = String.format("%s_seconds_sum",
            MicrometerBasedMetrics.METER_CONNECTIONS_AUTHENTICATED_DURATION.replace(".", "_"));
    private static final String METRIC_NAME_MESSAGES_PAYLOAD_SIZE = String.format("%s_bytes_sum",
            MicrometerBasedMetrics.METER_MESSAGES_PAYLOAD.replace(".", "_"));

    private static final String QUERY_TEMPLATE_MESSAGE_LIMIT = String.format(
            "floor(sum(increase(%1$s{status=~\"%3$s|%4$s\", tenant=\"%%1$s\"} [%%2$dm:%%3$ds]) or %2$s*0) + sum(increase(%2$s{status=~\"%3$s|%4$s\", tenant=\"%%1$s\"} [%%2$dm:%%3$ds]) or %1$s*0))",
            METRIC_NAME_MESSAGES_PAYLOAD_SIZE,
            METRIC_NAME_COMMANDS_PAYLOAD_SIZE,
            MetricsTags.ProcessingOutcome.FORWARDED.asTag().getValue(),
            MetricsTags.ProcessingOutcome.UNPROCESSABLE.asTag().getValue());
    private static final String QUERY_URI = "/api/v1/query";

    private final Tracer tracer;
    private final WebClient client;
    private final PrometheusBasedResourceLimitChecksConfig config;
    private final AsyncCache<String, LimitedResource<Long>> connectionCountCache;
    private final AsyncCache<String, LimitedResource<Duration>> connectionDurationCache;
    private final AsyncCache<String, LimitedResource<Long>> dataVolumeCache;
    private final String url;

    private Clock clock = Clock.systemUTC();

    /**
     * Creates new checks.
     *
     * @param webClient The client to use for querying the Prometheus server.
     * @param config The PrometheusBasedResourceLimitChecks configuration object.
     * @param connectionCountCache The cache to use for a tenant's overall number of connected devices.
     * @param connectionDurationCache The cache to use for a tenant's devices' overall connection time.
     * @param dataVolumeCache The cache to use for a tenant's devices' overall amount of data transferred.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public PrometheusBasedResourceLimitChecks(
            final WebClient webClient,
            final PrometheusBasedResourceLimitChecksConfig config,
            final AsyncCache<String, LimitedResource<Long>> connectionCountCache,
            final AsyncCache<String, LimitedResource<Duration>> connectionDurationCache,
            final AsyncCache<String, LimitedResource<Long>> dataVolumeCache) {

        this(
                webClient,
                config,
                connectionCountCache,
                connectionDurationCache,
                dataVolumeCache,
                NoopTracerFactory.create());
    }

    /**
     * Creates new checks.
     *
     * @param webClient The client to use for querying the Prometheus server.
     * @param config The PrometheusBasedResourceLimitChecks configuration object.
     * @param connectionCountCache The cache to use for a tenant's overall number of connected devices.
     * @param connectionDurationCache The cache to use for a tenant's devices' overall connection time.
     * @param dataVolumeCache The cache to use for a tenant's devices' overall amount of data transferred.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public PrometheusBasedResourceLimitChecks(
            final WebClient webClient,
            final PrometheusBasedResourceLimitChecksConfig config,
            final AsyncCache<String, LimitedResource<Long>> connectionCountCache,
            final AsyncCache<String, LimitedResource<Duration>> connectionDurationCache,
            final AsyncCache<String, LimitedResource<Long>> dataVolumeCache,
            final Tracer tracer) {

        this.client = Objects.requireNonNull(webClient);
        this.config = Objects.requireNonNull(config);
        this.connectionCountCache = Objects.requireNonNull(connectionCountCache);
        this.connectionDurationCache = Objects.requireNonNull(connectionDurationCache);
        this.dataVolumeCache = Objects.requireNonNull(dataVolumeCache);
        this.tracer = Objects.requireNonNull(tracer);
        this.url = String.format("%s://%s:%d%s",
                config.isTlsEnabled() ? "https" : "http",
                config.getHost(),
                config.getPort(),
                QUERY_URI);
    }

    private Span createSpan(final String name, final SpanContext parent, final TenantObject tenant) {
        return TracingHelper.buildChildSpan(tracer, parent, name, getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(Tags.PEER_HOSTNAME.getKey(), config.getHost())
                .withTag(Tags.PEER_PORT.getKey(), config.getPort())
                .withTag(Tags.HTTP_URL.getKey(), QUERY_URI)
                .withTag(TracingHelper.TAG_TENANT_ID.getKey(), tenant.getTenantId())
                .start();
    }

    /**
     * Sets a clock to use for determining the current system time.
     * <p>
     * The default value of this property is {@link Clock#systemUTC()}.
     * <p>
     * This property should only be set for running tests expecting the current
     * time to be a certain value, e.g. by using {@link Clock#fixed(Instant, java.time.ZoneId)}.
     *
     * @param clock The clock to use.
     */
    void setClock(final Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Future<Boolean> isConnectionLimitReached(final TenantObject tenant, final SpanContext spanContext) {

        Objects.requireNonNull(tenant);

        final Span span = createSpan("verify connection limit", spanContext, tenant);
        final Map<String, Object> traceItems = new HashMap<>();

        final Promise<Boolean> result = Promise.promise();

        if (tenant.getResourceLimits() == null) {
            traceItems.put(Fields.EVENT, "no resource limits configured");
            LOG.trace("no resource limits configured for tenant [{}]", tenant.getTenantId());
            result.complete(Boolean.FALSE);
        } else {
            final long maxConnections = tenant.getResourceLimits().getMaxConnections();
            LOG.trace("connection limit for tenant [{}] is [{}]", tenant.getTenantId(), maxConnections);

            if (maxConnections == TenantConstants.UNLIMITED_CONNECTIONS) {
                traceItems.put(Fields.EVENT, "no connection limit configured");
                result.complete(Boolean.FALSE);
            } else {
                final Context originalContext = Vertx.currentContext();
                final AtomicBoolean cacheHit = new AtomicBoolean(true);
                connectionCountCache.get(tenant.getTenantId(), (tenantId, executor) -> {
                    final CompletableFuture<LimitedResource<Long>> r = new CompletableFuture<>();
                    cacheHit.set(false);
                    final String queryParams = String.format(
                            "sum(%s{tenant=\"%s\"})",
                            METRIC_NAME_CONNECTIONS,
                            tenantId);
                    executeQuery(queryParams, span)
                        .onSuccess(currentConnections -> r.complete(new LimitedResource<>(maxConnections, currentConnections)))
                        .onFailure(r::completeExceptionally);
                    return r;
                })
                .whenComplete((value, error) -> runOnContext(originalContext, v -> {
                    TracingHelper.TAG_CACHE_HIT.set(span, cacheHit.get());
                    if (error != null) {
                        TracingHelper.logError(span, error);
                        result.complete(Boolean.FALSE);
                    } else {
                        traceItems.put(TenantConstants.FIELD_MAX_CONNECTIONS, maxConnections);
                        traceItems.put("current-connections", value.getCurrentValue());
                        final boolean isExceeded = value.getCurrentValue() >= value.getCurrentLimit();
                        LOG.trace("connection limit {}exceeded [tenant: {}, current connections: {}, max-connections: {}]",
                                isExceeded ? "" : "not ", tenant.getTenantId(), value.getCurrentValue(), value.getCurrentLimit());
                        result.complete(isExceeded);
                    }
                }));
            }
        }

        return result.future().map(b -> {
            traceItems.put("limit exceeded", b);
            span.log(traceItems);
            span.finish();
            return b;
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

        final Span span = createSpan("verify message limit", spanContext, tenant);
        final Map<String, Object> items = new HashMap<>();
        items.put("payload-size", payloadSize);

        final Promise<Boolean> result = Promise.promise();

        if (tenant.getResourceLimits() == null) {
            items.put(Fields.EVENT, "no resource limits configured");
            LOG.trace("no resource limits configured for tenant [{}]", tenant.getTenantId());
            result.complete(Boolean.FALSE);
        } else if (tenant.getResourceLimits().getDataVolume() == null) {
            items.put(Fields.EVENT, "no message limits configured");
            LOG.trace("no message limits configured for tenant [{}]", tenant.getTenantId());
            result.complete(Boolean.FALSE);
        } else {
            checkMessageLimit(tenant, payloadSize, items, span, result);
        }
        return result.future()
                .map(b -> {
                    items.put("limit exceeded", b);
                    span.log(items);
                    span.finish();
                    return b;
                });
    }

    private void checkMessageLimit(
            final TenantObject tenant,
            final long payloadSize,
            final Map<String, Object> items,
            final Span span,
            final Promise<Boolean> result) {

        final DataVolume dataVolumeConfig = tenant.getResourceLimits().getDataVolume();
        final long maxBytes = dataVolumeConfig.getMaxBytes();
        final Instant effectiveSince = dataVolumeConfig.getEffectiveSince();
        //If the period is not set explicitly, monthly is assumed as the default value
        final String periodMode = dataVolumeConfig.getPeriod().getMode();
        final long periodInDays = dataVolumeConfig.getPeriod().getNoOfDays();

        LOG.trace("message limit config for tenant [{}] are [{}:{}, {}:{}, {}:{}, {}:{}]", tenant.getTenantId(),
                TenantConstants.FIELD_MAX_BYTES, maxBytes,
                TenantConstants.FIELD_EFFECTIVE_SINCE, effectiveSince,
                TenantConstants.FIELD_PERIOD_MODE, periodMode,
                TenantConstants.FIELD_PERIOD_NO_OF_DAYS, periodInDays);

        if (maxBytes == TenantConstants.UNLIMITED_BYTES || effectiveSince == null || !ResourceLimitsPeriod.isSupportedMode(periodMode) || payloadSize <= 0) {
            result.complete(Boolean.FALSE);
        } else {

            final Context originalContext = Vertx.currentContext();
            final AtomicBoolean cacheHit = new AtomicBoolean(true);
            dataVolumeCache.get(tenant.getTenantId(), (tenantId, executor) -> {
                final CompletableFuture<LimitedResource<Long>> r = new CompletableFuture<>();
                cacheHit.set(false);
                final Instant nowUtc = Instant.now(clock);
                final Long allowedMaxBytes = calculateEffectiveLimit(
                        effectiveSince,
                        nowUtc,
                        periodMode,
                        maxBytes);
                final Duration dataUsagePeriod = calculateResourceUsageDuration(
                        effectiveSince,
                        nowUtc,
                        periodMode,
                        periodInDays);
                if (dataUsagePeriod.toMinutes() <= 0) {
                    r.complete(new LimitedResource<>(allowedMaxBytes, 0L));
                } else {
                    final String queryParams = String.format(
                            QUERY_TEMPLATE_MESSAGE_LIMIT,
                            tenant.getTenantId(),
                            dataUsagePeriod.toMinutes(),
                            config.getCacheTimeout());
                    executeQuery(queryParams, span)
                        .onSuccess(bytesConsumed -> r.complete(new LimitedResource<>(allowedMaxBytes, bytesConsumed)))
                        .onFailure(r::completeExceptionally);
                }
                return r;
            })
            .whenComplete((value, error) -> runOnContext(originalContext, v -> {
                TracingHelper.TAG_CACHE_HIT.set(span, cacheHit.get());
                if (error != null) {
                    TracingHelper.logError(span, error);
                    result.complete(Boolean.FALSE);
                } else {
                    items.put("current period bytes limit", value.getCurrentLimit());
                    items.put("current period bytes consumed", value.getCurrentValue());
                    final boolean isExceeded = (value.getCurrentValue() + payloadSize) > value.getCurrentLimit();
                    LOG.trace(
                            "data limit {}exceeded [tenant: {}, bytes consumed: {}, allowed max-bytes: {}, {}: {}, {}: {}, {}: {}]",
                            isExceeded ? "" : "not ",
                            tenant.getTenantId(), value.getCurrentValue(), value.getCurrentLimit(),
                            TenantConstants.FIELD_EFFECTIVE_SINCE, effectiveSince,
                            TenantConstants.FIELD_PERIOD_MODE, periodMode,
                            TenantConstants.FIELD_PERIOD_NO_OF_DAYS, periodInDays);
                    result.complete(isExceeded);
                }
            }));
        }
    }

    @Override
    public Future<Boolean> isConnectionDurationLimitReached(
            final TenantObject tenant,
            final SpanContext spanContext) {

        Objects.requireNonNull(tenant);

        final Span span = createSpan("verify connection duration limit", spanContext, tenant);
        final Map<String, Object> items = new HashMap<>();

        final Promise<Boolean> result = Promise.promise();

        if (tenant.getResourceLimits() == null) {
            items.put(Fields.EVENT, "no resource limits configured");
            LOG.trace("no resource limits configured for tenant [{}]", tenant.getTenantId());
            result.complete(Boolean.FALSE);
        } else if (tenant.getResourceLimits().getConnectionDuration() == null) {
            items.put(Fields.EVENT, "no connection duration limit configured");
            LOG.trace("no connection duration limit configured for tenant [{}]", tenant.getTenantId());
            result.complete(Boolean.FALSE);
        } else {
            checkConnectionDurationLimit(tenant, items, span, result);
        }

        return result.future()
                .map(b -> {
                    items.put("limit exceeded", b);
                    span.log(items);
                    span.finish();
                    return b;
                });
    }

    private void checkConnectionDurationLimit(
            final TenantObject tenant,
            final Map<String, Object> items,
            final Span span,
            final Promise<Boolean> result) {

        final ConnectionDuration connectionDurationConfig = tenant.getResourceLimits().getConnectionDuration();
        final long maxConnectionDurationInMinutes = connectionDurationConfig.getMaxMinutes();
        final Instant effectiveSince = connectionDurationConfig.getEffectiveSince();
        //If the period is not set explicitly, monthly is assumed as the default value
        final String periodMode = connectionDurationConfig.getPeriod().getMode();
        final long periodInDays = connectionDurationConfig.getPeriod().getNoOfDays();

        LOG.trace("connection duration config for the tenant [{}] is [{}:{}, {}:{}, {}:{}, {}:{}]",
                tenant.getTenantId(),
                TenantConstants.FIELD_MAX_MINUTES, maxConnectionDurationInMinutes,
                TenantConstants.FIELD_EFFECTIVE_SINCE, effectiveSince,
                TenantConstants.FIELD_PERIOD_MODE, periodMode,
                TenantConstants.FIELD_PERIOD_NO_OF_DAYS, periodInDays);

        if (maxConnectionDurationInMinutes == TenantConstants.UNLIMITED_MINUTES || effectiveSince == null || !ResourceLimitsPeriod.isSupportedMode(periodMode)) {
            result.complete(Boolean.FALSE);
        } else {

            final Context originalContext = Vertx.currentContext();
            final AtomicBoolean cacheHit = new AtomicBoolean(true);
            connectionDurationCache.get(tenant.getTenantId(), (tenantId, executor) -> {
                final CompletableFuture<LimitedResource<Duration>> r = new CompletableFuture<>();
                cacheHit.set(false);
                final Instant nowUtc = Instant.now(clock);
                final Duration allowedMaxMinutes = Duration.ofMinutes(calculateEffectiveLimit(
                                effectiveSince,
                                nowUtc,
                                periodMode,
                                maxConnectionDurationInMinutes));
                final Duration connectionDurationUsagePeriod = calculateResourceUsageDuration(
                                effectiveSince,
                                nowUtc,
                                periodMode,
                                periodInDays);

                if (connectionDurationUsagePeriod.toMinutes() <= 0) {
                    r.complete(new LimitedResource<>(allowedMaxMinutes, Duration.ofMinutes(0)));
                } else {
                    final String queryParams = String.format("minute( sum( increase( %s {tenant=\"%s\"} [%dm:%ds])))",
                            METRIC_NAME_CONNECTIONS_DURATION,
                            tenant.getTenantId(),
                            connectionDurationUsagePeriod.toMinutes(),
                            config.getCacheTimeout());
                    executeQuery(queryParams, span)
                        .onSuccess(minutesConnected -> r.complete(new LimitedResource<>(allowedMaxMinutes, Duration.ofMinutes(minutesConnected))))
                        .onFailure(r::completeExceptionally);
                }
                return r;
            })
            .whenComplete((value, error) -> runOnContext(originalContext, v -> {
                TracingHelper.TAG_CACHE_HIT.set(span, cacheHit.get());
                if (error != null) {
                    TracingHelper.logError(span, error);
                    result.complete(Boolean.FALSE);
                } else {
                    items.put("current period's connection duration limit", value.getCurrentLimit());
                    items.put("current period's connection duration consumed", value.getCurrentValue());
                    final boolean isExceeded = value.getCurrentValue().compareTo(value.getCurrentLimit()) >= 0;
                    LOG.trace(
                            "connection duration limit {} exceeded [tenant: {}, connection duration consumed: {}, allowed max-duration: {}, {}: {}, {}: {}, {}: {}]",
                            isExceeded ? "" : "not ",
                            tenant.getTenantId(), value.getCurrentValue(), value.getCurrentLimit(),
                            TenantConstants.FIELD_EFFECTIVE_SINCE, effectiveSince,
                            TenantConstants.FIELD_PERIOD_MODE, periodMode,
                            TenantConstants.FIELD_PERIOD_NO_OF_DAYS, periodInDays);
                    result.complete(isExceeded);
                }
            }));
        }
    }

    private Future<Long> executeQuery(final String query, final Span span) {

        final Promise<Long> result = Promise.promise();
        LOG.trace("running Prometheus query [URL: {}, query: {}]", url, query);
        newQueryRequest(query).send(sendAttempt -> {
            if (sendAttempt.succeeded()) {
                final HttpResponse<JsonObject> response = sendAttempt.result();
                result.complete(extractLongValue(response.body(), span));
            } else {
                final Map<String, Object> traceItems = Map.of(
                        Fields.EVENT, Tags.ERROR.getKey(),
                        Fields.MESSAGE, "failed to run Prometheus query",
                        "URL", url,
                        "query", query,
                        Fields.ERROR_KIND, "Exception",
                        Fields.ERROR_OBJECT, sendAttempt.cause());
                TracingHelper.logError(span, traceItems);
                LOG.warn("failed to run Prometheus query [URL: {}, query: {}]: {}",
                        url, query, sendAttempt.cause().getMessage());
                result.fail(sendAttempt.cause());
            }
        });
        return result.future();
    }

    private HttpRequest<JsonObject> newQueryRequest(final String query) {
        final HttpRequest<JsonObject> request = client.get(QUERY_URI)
                .addQueryParam("query", query)
                .expect(ResponsePredicate.SC_OK)
                .as(BodyCodec.jsonObject());

        if (config.getQueryTimeout() > 0) {
            // enables timeout for Prometheus queries via HTTP API
            request.timeout(config.getQueryTimeout());
        }

        if (!Strings.isNullOrEmpty(config.getUsername()) && !Strings.isNullOrEmpty(config.getPassword())) {
            request.basicAuthentication(config.getUsername(), config.getPassword());
        }
        return request;
    }

    /**
     * Extracts a long value from the JSON result returned by the Prometheus
     * server.
     * <p>
     * The result is expected to have the following structure:
     * <pre>
     * {
     *   "status": "success",
     *   "data": {
     *     "result": [
     *       {
     *         "value": [ $timestamp, "$value" ]
     *       }
     *     ]
     *   }
     * }
     * </pre>
     *
     * @param response The response object.
     * @return The extracted value.
     * @see <a href="https://prometheus.io/docs/prometheus/latest/querying/api/">Prometheus HTTP API</a>
     */
    private Long extractLongValue(final JsonObject response, final Span span) {

        Objects.requireNonNull(response);

        try {
            final String status = response.getString("status");
            if ("error".equals(status)) {
                TracingHelper.logError(span, Map.of(Fields.MESSAGE, "error executing query",
                        "status", status,
                        "error-type", response.getString("errorType"),
                        "error", response.getString("error")));
                LOG.debug("error executing query [status: {}, error type: {}, error: {}]",
                        status, response.getString("errorType"), response.getString("error"));
                return 0L;
            } else {
                // success
                final JsonObject data = response.getJsonObject("data", new JsonObject());
                final JsonArray result = data.getJsonArray("result");
                if (result != null) {
                    if (result.size() == 0) {
                        // no metrics available yet
                        span.log("no metrics available (yet)");
                        return 0L;
                    } else if (result.size() == 1 && result.getJsonObject(0) != null) {
                        final JsonArray valueArray = result.getJsonObject(0).getJsonArray("value");
                        if (valueArray != null && valueArray.size() == 2) {
                            final String value = valueArray.getString(1);
                            if (value != null && !value.isEmpty()) {
                                return Long.parseLong(value);
                            }
                        }
                    }
                }
                final String jsonResponse = response.encodePrettily();
                TracingHelper.logError(span, Map.of(Fields.MESSAGE, "server returned malformed response",
                        "response", jsonResponse));
                LOG.debug("server returned malformed response: {}", jsonResponse);
            }
        } catch (Exception e) {
            final String jsonResponse = response.encodePrettily();
            TracingHelper.logError(span, Map.of(Fields.MESSAGE, "server returned malformed response",
                    "response", jsonResponse));
            LOG.debug("server returned malformed response: {}", jsonResponse);
        }
        return 0L;
    }

    /**
     * Calculates the effective resource limit for a tenant for the current accounting period.
     * <p>
     * For the initial accounting period of a monthly plan the effective resource limit is
     * calculated as follows:
     * <pre>
     *             configured limit 
     *   ----------------------------------- x No. of minutes until start of next accounting period.
     *   No. of minutes in the current month
     * </pre>
     * <p>
     * In all other cases the configured limit is used directly.
     *
     * @param effectiveSince The point of time (UTC) at which the resource limit became or will become effective.
     * @param targetDateTime The point in time (UTC) to calculate the limit for.
     * @param periodType The type of accounting periods that the resource limit is based on.
     * @param configuredLimit The maximum amount of resources to be used per accounting period.
     * @return The resource limit for the current accounting period. The value will be 0 if target
     *         date-time is before effective since date-time.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    long calculateEffectiveLimit(
            final Instant effectiveSince,
            final Instant targetDateTime,
            final String periodType,
            final long configuredLimit) {

        Objects.requireNonNull(effectiveSince, "effective since");
        Objects.requireNonNull(targetDateTime, "target date-time");
        Objects.requireNonNull(periodType, "period mode");

        if (targetDateTime.isBefore(effectiveSince)) {
            return 0;
        }

        // we only need to calculate the effective limit if we are in the initial accounting period
        // of a monthly plan
        if (ResourceLimitsPeriod.PERIOD_MODE_MONTHLY.equals(periodType) && configuredLimit > 0) {

            final ZonedDateTime effectiveSinceZonedDateTime = ZonedDateTime.ofInstant(effectiveSince, ZoneOffset.UTC);
            final ZonedDateTime targetZonedDateTime = ZonedDateTime.ofInstant(targetDateTime, ZoneOffset.UTC);

            if (YearMonth.from(targetZonedDateTime).equals(YearMonth.from(effectiveSinceZonedDateTime))) {

                final ZonedDateTime startOfNextAccountingPeriod = effectiveSinceZonedDateTime
                        .with(TemporalAdjusters.firstDayOfNextMonth())
                        .withHour(0)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0);
                final long minutesTillStartOfNextAccountingPeriod = Math.max(1, Duration
                        .between(effectiveSinceZonedDateTime, startOfNextAccountingPeriod)
                        .toMinutes());
                final long lengthOfCurrentMonthInMinutes = 60 * 24 * effectiveSinceZonedDateTime
                        .range(ChronoField.DAY_OF_MONTH).getMaximum();
                return (long) Math.ceil((double) minutesTillStartOfNextAccountingPeriod * configuredLimit / lengthOfCurrentMonthInMinutes);
            }
        }
        return configuredLimit;
    }

    /**
     * Gets the amount of time that has elapsed in the most recent accounting period up to a given point in time.
     * <p>
     * The amount of time is calculated as the number of full minutes between the beginning of the accounting
     * period that the given target point in time lies in and the target point in time.
     * Note that when the returned duration is used to determine the amount of resources already consumed during
     * the current accounting period, this will result in resources not being accounted for which have been used
     * before the start of the next minute at the beginning of the period.
     * <p>
     * The initial accounting period always starts at the point in time given by the effectiveSince parameter value.
     * For a <em>MONTHLY</em> plan, subsequent accounting periods start on the 1st of each month at 12 AM UTC.
     * For a <em>DAILY</em> plan, subsequent accounting periods start every periodLength days at the same time of day as
     * given in the effectiveSince parameter value.
     *
     * @param effectiveSince The point of time (UTC) at which the resource limit became or will become effective.
     * @param targetDateTime The point in time (UTC) to calculate the duration for.
     * @param periodType The type of accounting periods that the resource limit is based on.
     * @param periodLength The length of a single accounting period. This parameter is only used if the type of
     *                     plan is <em>DAILY</em>.
     * @return The calculated amount of time. The returned duration will have zero length if target date-time
     *         is before effective since date-time.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    Duration calculateResourceUsageDuration(
            final Instant effectiveSince,
            final Instant targetDateTime,
            final String periodType,
            final long periodLength) {

        Objects.requireNonNull(effectiveSince, "effective since");
        Objects.requireNonNull(targetDateTime, "target date-time");
        Objects.requireNonNull(periodType, "period type");

        if (targetDateTime.isBefore(effectiveSince)) {
            return Duration.ZERO;
        }

        final ZonedDateTime targetZonedDateTime = ZonedDateTime.ofInstant(targetDateTime, ZoneOffset.UTC);
        final ZonedDateTime beginningOfMostRecentAccountingPeriod = getBeginningOfMostRecentAccountingPeriod(
                ZonedDateTime.ofInstant(effectiveSince, ZoneOffset.UTC),
                targetZonedDateTime,
                periodType,
                periodLength);
        return Duration.between(beginningOfMostRecentAccountingPeriod, targetZonedDateTime);
    }

    private ZonedDateTime getBeginningOfMostRecentAccountingPeriod(
            final ZonedDateTime effectiveSince,
            final ZonedDateTime targetDateTime,
            final String periodType,
            final long periodLength) {

        switch (periodType) {
        case ResourceLimitsPeriod.PERIOD_MODE_MONTHLY:
            final YearMonth targetYearMonth = YearMonth.from(targetDateTime);
            if (targetYearMonth.equals(YearMonth.from(effectiveSince))) {
                // we are in the initial accounting period
                return effectiveSince;
            } else {
                // subsequent accounting periods start at midnight (start of day) UTC on the 1st of each month
                return ZonedDateTime.of(
                        targetYearMonth.getYear(), targetYearMonth.getMonthValue(), 1,
                        0, 0, 0, 0,
                        ZoneOffset.UTC);
            }
        case ResourceLimitsPeriod.PERIOD_MODE_DAYS:
            final Duration overall = Duration.between(effectiveSince, targetDateTime);
            final Duration accountingPeriodLength = Duration.ofDays(periodLength);
            if (overall.compareTo(accountingPeriodLength) < 1) {
                // we are in the initial accounting period
                return effectiveSince;
            } else {
                // subsequent accounting periods start every accountingPeriodLength days
                // at the same time as effective since 
                final long totalPeriodsElapsed = overall.dividedBy(accountingPeriodLength);
                return effectiveSince.plus(accountingPeriodLength.multipliedBy(totalPeriodsElapsed));
            }
        default:
            return targetDateTime;
        }
    }

    private void runOnContext(final Context context, final Handler<Void> action) {
        if (context != null && context != Vertx.currentContext()) {
            context.runOnContext(go -> action.handle(null));
        } else {
            action.handle(null);
        }
    }
}
