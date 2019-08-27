/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.plan;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MicrometerBasedMetrics;
import org.eclipse.hono.util.DataVolume;
import org.eclipse.hono.util.DataVolumePeriod;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;

/**
 * Resource limit checks which compare configured limits with live metrics retrieved
 * from a <em>Prometheus</em> server.
 */
public final class PrometheusBasedResourceLimitChecks implements ResourceLimitChecks {

    private static final String CONNECTIONS_METRIC_NAME = MicrometerBasedMetrics.METER_CONNECTIONS_AUTHENTICATED
            .replace(".", "_");
    private static final String MESSAGES_PAYLOAD_SIZE_METRIC_NAME = String.format("%s_bytes_sum",
            MicrometerBasedMetrics.METER_MESSAGES_PAYLOAD.replace(".", "_"));
    private static final String COMMANDS_PAYLOAD_SIZE_METRIC_NAME = String.format("%s_bytes_sum",
            MicrometerBasedMetrics.METER_COMMANDS_PAYLOAD.replace(".", "_"));
    private static final Logger log = LoggerFactory.getLogger(PrometheusBasedResourceLimitChecks.class);
    private static final String QUERY_URI = "/api/v1/query";
    private static final String LIMITS_CACHE_NAME = "resource-limits";

    private final WebClient client;
    private final PrometheusBasedResourceLimitChecksConfig config;
    private final ExpiringValueCache<Object, Object> limitsCache;

    /**
     * Creates new checks.
     *
     * @param webClient The client to use for querying the Prometheus server.
     * @param config The PrometheusBasedResourceLimitChecks configuration object.
     * @param cacheProvider The cache provider to use for creating caches for metrics data retrieved
     *                      from the prometheus backend or {@code null} if they should not be cached.
     * @throws NullPointerException if any of the parameters except cacheProvider are {@code null}.
     */
    public PrometheusBasedResourceLimitChecks(final WebClient webClient,
            final PrometheusBasedResourceLimitChecksConfig config, final CacheProvider cacheProvider) {
        this.client = Objects.requireNonNull(webClient);
        this.config = Objects.requireNonNull(config);
        this.limitsCache = Optional.ofNullable(cacheProvider)
                .map(provider -> provider.getCache(LIMITS_CACHE_NAME))
                .orElse(null);
    }

    @Override
    public Future<Boolean> isConnectionLimitReached(final TenantObject tenant) {

        Objects.requireNonNull(tenant);
        if (tenant.getResourceLimits() == null) {
            log.trace("No connection limits configured for the tenant [{}]", tenant.getTenantId());
            return Future.succeededFuture(Boolean.FALSE);
        }

        final long maxConnections = tenant.getResourceLimits().getMaxConnections();

        log.trace("connection limit for tenant [{}] is [{}]", tenant.getTenantId(), maxConnections);

        if (maxConnections == -1) {
            return Future.succeededFuture(Boolean.FALSE);
        } else {
            final String queryParams = String.format("sum(%s{tenant=\"%s\"})", CONNECTIONS_METRIC_NAME,
                    tenant.getTenantId());
            return executeQuery(queryParams)
                    .map(currentConnections -> {
                        if (currentConnections < maxConnections) {
                            return Boolean.FALSE;
                        } else {
                            log.trace(
                                    "connection limit exceeded [tenant: {}, current connections: {}, max-connections: {}]",
                                    tenant.getTenantId(), currentConnections, maxConnections);
                            return Boolean.TRUE;
                        }
                    }).otherwise(Boolean.FALSE);
        }
    }

    /**
     * Checks if the maximum data volume for the messages configured for a tenant
     * have been reached.
     * @param tenant The tenant configuration to check the limit against.
     * @param payloadSize The message payload size in bytes.
     * @return A future indicating the outcome of the check.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException}
     *         if the check could not be performed.
     */
    @Override
    public Future<Boolean> isMessageLimitReached(final TenantObject tenant,
            final long payloadSize) {

        Objects.requireNonNull(tenant);
        if (tenant.getResourceLimits() == null || tenant.getResourceLimits().getDataVolume() == null) {
            log.trace("No message limits configured for the tenant [{}]", tenant.getTenantId());
            return Future.succeededFuture(Boolean.FALSE);
        }

        final DataVolume dataVolumeConfig = tenant.getResourceLimits().getDataVolume();
        final long maxBytes = dataVolumeConfig.getMaxBytes();
        final Instant effectiveSince = dataVolumeConfig.getEffectiveSince();
        final long periodInDays = Optional.ofNullable(dataVolumeConfig.getPeriod())
                .map(DataVolumePeriod::getNoOfDays)
                .orElse(0);

        log.trace("message limit config for tenant [{}] are [{}:{}, {}:{}, {}:{}]", tenant.getTenantId(),
                TenantConstants.FIELD_MAX_BYTES, maxBytes,
                TenantConstants.FIELD_EFFECTIVE_SINCE, effectiveSince,
                TenantConstants.FIELD_PERIOD_NO_OF_DAYS, periodInDays);

        if (maxBytes == -1 || effectiveSince == null || periodInDays <= 0 || payloadSize <= 0) {
            return Future.succeededFuture(Boolean.FALSE);
        } else {
            final long dataUsagePeriod = calculateDataUsagePeriod(effectiveSince, periodInDays);

            if (dataUsagePeriod <= 0) {
                return Future.succeededFuture(Boolean.FALSE);
            }

            final String queryParams = String.format(
                    "floor(sum(increase(%s{status=~\"%s|%s\", tenant=\"%s\"} [%sd]) or %s*0) + sum(increase(%s{status=~\"%s|%s\", tenant=\"%s\"} [%sd]) or %s*0))",
                    MESSAGES_PAYLOAD_SIZE_METRIC_NAME,
                    MetricsTags.ProcessingOutcome.FORWARDED.asTag().getValue(),
                    MetricsTags.ProcessingOutcome.UNPROCESSABLE.asTag().getValue(),
                    tenant.getTenantId(),
                    dataUsagePeriod,
                    COMMANDS_PAYLOAD_SIZE_METRIC_NAME,
                    COMMANDS_PAYLOAD_SIZE_METRIC_NAME,
                    MetricsTags.ProcessingOutcome.FORWARDED.asTag().getValue(),
                    MetricsTags.ProcessingOutcome.UNPROCESSABLE.asTag().getValue(),
                    tenant.getTenantId(),
                    dataUsagePeriod,
                    MESSAGES_PAYLOAD_SIZE_METRIC_NAME);
            final String key = String.format("%s_bytes_consumed", tenant.getTenantId());

            return Optional.ofNullable(limitsCache)
                    .map(success -> limitsCache.get(key))
                    .map(cachedValue -> Future.succeededFuture((long) cachedValue))
                    .orElseGet(() -> executeQuery(queryParams)
                            .map(bytesConsumed -> addToCache(limitsCache, key, bytesConsumed)))
                    .map(bytesConsumed -> {
                        if ((bytesConsumed + payloadSize) <= maxBytes) {
                            return Boolean.FALSE;
                        } else {
                            log.trace("data limit exceeded for tenant. [{}] are [bytesUsed:{}, {}:{}, {}:{}, {}:{}]",
                                    tenant.getTenantId(), bytesConsumed, TenantConstants.FIELD_MAX_BYTES,
                                    maxBytes,
                                    TenantConstants.FIELD_EFFECTIVE_SINCE, effectiveSince,
                                    TenantConstants.FIELD_PERIOD_NO_OF_DAYS, periodInDays);
                            return Boolean.TRUE;
                        }
                    }).otherwise(Boolean.FALSE);
        }
    }

    private Future<Long> executeQuery(final String query) {

        final Future<Long> result = Future.future();
        log.trace("running query [{}] against Prometheus backend [http://{}:{}{}]",
                query, config.getHost(), config.getPort(), QUERY_URI);
        client.get(config.getPort(), config.getHost(), QUERY_URI)
        .addQueryParam("query", query)
        .expect(ResponsePredicate.SC_OK)
        .as(BodyCodec.jsonObject())
        .send(sendAttempt -> {
            if (sendAttempt.succeeded()) {
                final HttpResponse<JsonObject> response = sendAttempt.result();
                result.complete(extractLongValue(response.body()));
            } else {
                log.debug("error fetching result from Prometheus: {}", sendAttempt.cause().getMessage());
                result.fail(sendAttempt.cause());
            }
        });
        return result;
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
    private Long extractLongValue(final JsonObject response) {

        Objects.requireNonNull(response);

        try {
            final String status = response.getString("status");
            if ("error".equals(status)) {
                log.debug("error while executing query [status: {}, error type: {}, error: {}]",
                        status, response.getString("errorType"), response.getString("error"));
                return 0L;
            } else {
                // success
                final JsonObject data = response.getJsonObject("data", new JsonObject());
                final JsonArray result = data.getJsonArray("result");
                if (result != null && result.size() == 1 && result.getJsonObject(0) != null) {
                    final JsonArray valueArray = result.getJsonObject(0).getJsonArray("value");
                    if (valueArray != null && valueArray.size() == 2) {
                        final String value = valueArray.getString(1);
                        if (value != null && !value.isEmpty()) {
                            return Long.parseLong(value);
                        }
                    }
                }
                log.debug("received malformed response from Prometheus server: {}", response.encodePrettily());
            }
        } catch (Exception e) {
            log.debug("received malformed response from Prometheus server: {}", response.encodePrettily(), e);
        }
        return 0L;
    }

    /**
     * Calculate the period for which the data usage is to be retrieved from the prometheus server.
     * 
     * @param effectiveSince The instant on which the data volume limit came into effect.
     * @param periodInDays The number of days for which the data usage is to be calculated.
     * @return The period for which the data usage is to be calculated.
     */
    private long calculateDataUsagePeriod(final Instant effectiveSince, final long periodInDays) {
        final long inclusiveDaysBetween = DAYS.between(effectiveSince, Instant.now()) + 1;
        if (inclusiveDaysBetween > 0 && periodInDays > 0) {
            final long dataUsagePeriodInDays = inclusiveDaysBetween % periodInDays;
            return dataUsagePeriodInDays == 0 ? periodInDays : dataUsagePeriodInDays;
        }
        return -1L;
    }

    private long addToCache(final ExpiringValueCache<Object, Object> cache, final String key, final long result) {
        Optional.ofNullable(cache)
                .ifPresent(success -> cache.put(key, result,
                        Duration.ofSeconds(config.getCacheTimeout())));
        return result;
    }
}
