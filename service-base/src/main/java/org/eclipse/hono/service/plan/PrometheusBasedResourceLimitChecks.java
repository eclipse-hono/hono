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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MicrometerBasedMetrics;
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

    /**
     * The default value for the maximum number of connections to be allowed is -1, which implies no limit.
     */
    static final long DEFAULT_MAX_CONNECTIONS = -1;

    /**
     * The name of the property that contains the maximum number of connections to be allowed for a tenant.
     */
    static final String FIELD_MAX_CONNECTIONS = "max-connections";

    /**
     * The name of the property that contains the configuration options for the data volume.
     */
    static final String FIELD_DATA_VOLUME = "data-volume";

    /**
     * The default value for the maximum number of bytes to be allowed for a tenant is set to -1, which implies no
     * limit.
     */
    static final long DEFAULT_MAX_BYTES = -1;

    /**
     * The name of the property that contains the maximum number of bytes to be allowed for a tenant.
     */
    static final String FIELD_MAX_BYTES = "max-bytes";

    /**
     * The default number of days for which the data usage being calculated, is set to 30 days.
     */
    static final long DEFAULT_PERIOD_IN_DAYS = 30;

    /**
     * The name of the property that contains the number of days for which the data usage is calculated.
     */
    static final String FIELD_PERIOD_IN_DAYS = "period-in-days";

    /**
     * The name of the property that contains the date on which the data volume limit came into effect.
     */
    static final String FIELD_EFFECTIVE_SINCE = "effective-since";

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
        final long maxConnections = getConnectionsLimit(tenant);

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
        final long maxBytes = getMaximumNumberOfBytes(tenant);
        final Instant effectiveSince = getEffectiveSince(tenant);
        final long periodInDays = getPeriodInDays(tenant);

        log.trace("message limit config for tenant [{}] are [{}:{}, {}:{}, {}:{}]", tenant.getTenantId(),
                FIELD_MAX_BYTES, maxBytes, FIELD_EFFECTIVE_SINCE, effectiveSince, FIELD_PERIOD_IN_DAYS, periodInDays);

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
                                    tenant.getTenantId(), bytesConsumed, FIELD_MAX_BYTES, maxBytes,
                                    FIELD_EFFECTIVE_SINCE, effectiveSince, FIELD_PERIOD_IN_DAYS, periodInDays);
                            return Boolean.TRUE;
                        }
                    }).otherwise(Boolean.FALSE);
        }
    }

    /**
     * Gets the connections limit for the tenant if configured, else returns {@link #DEFAULT_MAX_CONNECTIONS}.
     *
     * @param tenant The tenant configuration object.
     * @return The connections limit.
     */
    long getConnectionsLimit(final TenantObject tenant) {
        return Optional.ofNullable(tenant.getResourceLimits())
                .map(resourceLimits -> resourceLimits.getLong(FIELD_MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS))
                .orElse(DEFAULT_MAX_CONNECTIONS);
    }

    /**
     * Gets the maximum number of bytes to be allowed for the time period defined by the
     * {@link #getPeriodInDays(TenantObject)}.
     *
     * @param tenant The tenant configuration object.
     * @return The maximum number of bytes or {@link #DEFAULT_MAX_BYTES} if not set.
     */
    long getMaximumNumberOfBytes(final TenantObject tenant) {
        return Optional.ofNullable(tenant.getResourceLimits())
                .map(limits -> limits.getJsonObject(FIELD_DATA_VOLUME))
                .map(dataVolumeLimits -> dataVolumeLimits.getLong(FIELD_MAX_BYTES, DEFAULT_MAX_BYTES))
                .orElse(DEFAULT_MAX_BYTES);
    }

    /**
     * Gets the number of days for which the data usage is calculated and compared against the
     * {@link #getMaximumNumberOfBytes(TenantObject)}.
     *
     * @param tenant The tenant configuration object.
     * @return The number of days for which the data usage is calculated or {@link #DEFAULT_PERIOD_IN_DAYS} if not set.
     */
    long getPeriodInDays(final TenantObject tenant) {
        return Optional.ofNullable(tenant.getResourceLimits())
                .map(limits -> limits.getJsonObject(FIELD_DATA_VOLUME))
                .map(dataVolumeLimits -> dataVolumeLimits.getLong(FIELD_PERIOD_IN_DAYS, DEFAULT_PERIOD_IN_DAYS))
                .orElse(DEFAULT_PERIOD_IN_DAYS);
    }

    /**
     * Gets the instant on which the data volume limit came into effect.
     * <p>
     * The date string should comply to the {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME}
     *
     * @param tenant The tenant configuration object.
     * @return The instant on which the data volume limit came into effective or {@code null} if not set.
     * @throws DateTimeParseException if the date string fails to comply with the
     *             {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} format.
     */
    Instant getEffectiveSince(final TenantObject tenant) {
        return Optional.ofNullable(tenant.getResourceLimits())
                .map(limits -> limits.getJsonObject(FIELD_DATA_VOLUME))
                .map(dataVolumeLimits -> dataVolumeLimits.getString(FIELD_EFFECTIVE_SINCE))
                .map(this::getInstant)
                .orElse(null);
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

    private Instant getInstant(final String timestamp) {

        if (timestamp == null) {
            return null;
        } else {
            try {
                return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(timestamp, OffsetDateTime::from).toInstant();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }
}
