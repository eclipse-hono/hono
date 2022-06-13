/*******************************************************************************
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
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.ResourceLimitsPeriod.PeriodMode;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;

/**
 * A Caffeine cache loader that invokes the <em>Prometheus</em> query API to retrieve values.
 *
 * @param <K> The type of key being used in the cache.
 * @param <V> The type of value being used in the cache.
 */
abstract class PrometheusBasedAsyncCacheLoader<K, V> implements AsyncCacheLoader<K, V> {

    private static final Logger LOG = LoggerFactory.getLogger(PrometheusBasedAsyncCacheLoader.class);
    private static final String QUERY_URI = "/api/v1/query";

    protected Clock clock = Clock.systemUTC();

    protected final Tracer tracer;
    private final WebClient client;
    private final PrometheusBasedResourceLimitChecksConfig config;
    private final String url;

    /**
     * Creates a new loader.
     *
     * @param webClient The client to use for querying the Prometheus server.
     * @param config The PrometheusBasedResourceLimitChecks configuration object.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected PrometheusBasedAsyncCacheLoader(
            final WebClient webClient,
            final PrometheusBasedResourceLimitChecksConfig config,
            final Tracer tracer) {

        this.client = Objects.requireNonNull(webClient);
        this.config = Objects.requireNonNull(config);
        this.tracer = Objects.requireNonNull(tracer);
        this.url = String.format("%s://%s:%d%s",
                config.isTlsEnabled() ? "https" : "http",
                config.getHost(),
                config.getPort(),
                QUERY_URI);
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
     * @throws NullPointerException if clock is {@code null}.
     */
    void setClock(final Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Invokes the Prometheus server's query API.
     *
     * @param query The query to execute.
     * @param tracingContext The Open Tracing context to use for tracking the execution of the query.
     * @return A future indicating the outcome.
     */
    protected Future<Long> executeQuery(final String query, final SpanContext tracingContext) {

        final var span = tracer.buildSpan("execute Prometheus query")
                .addReference(References.FOLLOWS_FROM, tracingContext)
                .withTag(Tags.COMPONENT, getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(Tags.HTTP_URL.getKey(), url)
                .start();
        // set as active span so that it is used as parent for the vert.x web client request span
        final Scope spanScope = tracer.activateSpan(span);

        LOG.trace("running Prometheus query [URL: {}, query: {}]", url, query);
        span.log(Map.of(
                Fields.MESSAGE, "running Prometheus query",
                "query", query));

        final Promise<HttpResponse<JsonObject>> result = Promise.promise();
        newQueryRequest(query).send(result);
        return result.future()
                .onFailure(t -> {
                    TracingHelper.logError(span, Map.of(
                            Fields.MESSAGE, "failed to run Prometheus query",
                            Fields.ERROR_KIND, "Exception",
                            Fields.ERROR_OBJECT, t));
                    LOG.warn("failed to run Prometheus query [URL: {}, query: {}]: {}",
                            url, query, t.getMessage());
                })
                .map(response -> {
                    Tags.HTTP_STATUS.set(span, response.statusCode());
                    return extractLongValue(response.body(), span);
                })
                .onComplete(r -> {
                    span.finish();
                    spanScope.close();
                });
    }

    private HttpRequest<JsonObject> newQueryRequest(final String query) {

        final HttpRequest<?> request = client.post(QUERY_URI)
                .addQueryParam("query", query)
                .expect(ResponsePredicate.SC_OK);

        if (config.getQueryTimeout() > 0) {
            // limit query execution time on Prometheus
            request.addQueryParam("timeout", String.format("%dms", config.getQueryTimeout()));
            // make sure that the HTTP client waits at least as long as Prometheus will spend on
            // executing the query before giving up
            request.timeout(config.getQueryTimeout() + 100);
        }

        if (!Strings.isNullOrEmpty(config.getUsername()) && !Strings.isNullOrEmpty(config.getPassword())) {
            request.basicAuthentication(config.getUsername(), config.getPassword());
        }
        return request.as(BodyCodec.jsonObject());
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
     * @param span The Open Tracing span to use for tracking the processing of the response.
     * @return The extracted value.
     * @throws NullPointerException if response is {@code null}.
     * @see <a href="https://prometheus.io/docs/prometheus/latest/querying/api/">Prometheus HTTP API</a>
     */
    protected final Long extractLongValue(final JsonObject response, final Span span) {

        Objects.requireNonNull(response);

        try {
            final String status = response.getString("status");
            if ("error".equals(status)) {
                final String errorMessage = response.getString("error");
                TracingHelper.logError(span, Map.of(
                        Fields.MESSAGE, String.format("error executing query: %s", errorMessage),
                        "status", status,
                        Fields.ERROR_KIND, response.getString("errorType")));
                LOG.debug("error executing query [status: {}, error type: {}, error: {}]",
                        status, response.getString("errorType"), errorMessage);
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
     * @param periodMode The type of accounting periods that the resource limit is based on.
     * @param configuredLimit The maximum amount of resources to be used per accounting period.
     * @return The resource limit for the current accounting period. The value will be 0 if target
     *         date-time is before effective since date-time.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected long calculateEffectiveLimit(
            final Instant effectiveSince,
            final Instant targetDateTime,
            final PeriodMode periodMode,
            final long configuredLimit) {

        Objects.requireNonNull(effectiveSince, "effective since");
        Objects.requireNonNull(targetDateTime, "target date-time");
        Objects.requireNonNull(periodMode, "period mode");

        if (targetDateTime.isBefore(effectiveSince)) {
            return 0;
        }

        // we only need to calculate the effective limit if we are in the initial accounting period
        // of a monthly plan
        if (PeriodMode.monthly == periodMode && configuredLimit > 0) {

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
}
