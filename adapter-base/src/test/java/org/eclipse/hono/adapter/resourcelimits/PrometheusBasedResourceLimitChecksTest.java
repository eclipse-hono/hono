/**
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
 */
package org.eclipse.hono.adapter.resourcelimits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.ConnectionDuration;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DataVolume;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.ResourceLimitsPeriod;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies the behavior of {@link PrometheusBasedResourceLimitChecks}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, unit = TimeUnit.SECONDS)
public class PrometheusBasedResourceLimitChecksTest {

    private static final int QUERY_TIMEOUT = 500;
    private static final int REQUEST_TIMEOUT = QUERY_TIMEOUT + 100;

    private PrometheusBasedResourceLimitChecksConfig config;
    private PrometheusBasedResourceLimitChecks limitChecksImpl;
    private WebClient webClient;
    private HttpRequest<JsonObject> jsonRequest;
    private HttpRequest<Buffer> bufferReq;
    private AsyncCache<String, LimitedResource<Long>> connectionCountCache;
    private AsyncCache<String, LimitedResource<Duration>> connectionDurationCache;
    private AsyncCache<String, LimitedResource<Long>> dataVolumeCache;
    private Span span;
    private Tracer tracer;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {

        jsonRequest = mock(HttpRequest.class);

        bufferReq = mock(HttpRequest.class);
        when(bufferReq.addQueryParam(anyString(), anyString())).thenReturn(bufferReq);
        when(bufferReq.expect(any(ResponsePredicate.class))).thenReturn(bufferReq);
        when(bufferReq.basicAuthentication(anyString(), anyString())).thenReturn(bufferReq);
        when(bufferReq.timeout(anyLong())).thenReturn(bufferReq);
        when(bufferReq.as(any(BodyCodec.class))).thenReturn(jsonRequest);

        webClient = mock(WebClient.class);
        when(webClient.post(anyString())).thenReturn(bufferReq);

        connectionCountCache = mock(AsyncCache.class);
        when(connectionCountCache.get(anyString(), any(BiFunction.class))).then(invocation -> {
            final BiFunction<String, Executor, CompletableFuture<LimitedResource<?>>> provider = invocation.getArgument(1);
            return provider.apply(invocation.getArgument(0), mock(Executor.class));
        });
        connectionDurationCache = mock(AsyncCache.class);
        when(connectionDurationCache.get(anyString(), any(BiFunction.class))).then(invocation -> {
            final BiFunction<String, Executor, CompletableFuture<LimitedResource<?>>> provider = invocation.getArgument(1);
            return provider.apply(invocation.getArgument(0), mock(Executor.class));
        });
        dataVolumeCache = mock(AsyncCache.class);
        when(dataVolumeCache.get(anyString(), any(BiFunction.class))).then(invocation -> {
            final BiFunction<String, Executor, CompletableFuture<LimitedResource<?>>> provider = invocation.getArgument(1);
            return provider.apply(invocation.getArgument(0), mock(Executor.class));
        });

        span = TracingMockSupport.mockSpan();
        tracer = TracingMockSupport.mockTracer(span);

        config = new PrometheusBasedResourceLimitChecksConfig();
        config.setQueryTimeout(QUERY_TIMEOUT);

        limitChecksImpl = new PrometheusBasedResourceLimitChecks(
                webClient,
                config,
                connectionCountCache,
                connectionDurationCache,
                dataVolumeCache,
                tracer);
    }

    /**
     * Verifies that the Basic authentication header is set if username and password have been
     * configured.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testExecuteQuerySetsAuthHeader(final VertxTestContext ctx) {

        final PrometheusBasedResourceLimitChecksConfig config = new PrometheusBasedResourceLimitChecksConfig();
        config.setUsername("hono");
        config.setPassword("hono-secret");

        limitChecksImpl = new PrometheusBasedResourceLimitChecks(
                webClient,
                config,
                connectionCountCache,
                connectionDurationCache,
                dataVolumeCache,
                tracer);

        givenCurrentConnections(0);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits().setMaxConnections(10));

        limitChecksImpl.isConnectionLimitReached(tenant, mock(SpanContext.class)).onComplete(
                ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(bufferReq).basicAuthentication(eq("hono"), eq("hono-secret"));
                        verify(jsonRequest).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the connection limit check returns {@code false} if no resource limits
     * have been defined for the tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionLimitCheckSucceedsIfNoResourceLimitsAreSet(final VertxTestContext ctx) {

        givenCurrentConnections(9);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);

        limitChecksImpl.isConnectionLimitReached(tenant, mock(SpanContext.class)).onComplete(
                ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(jsonRequest, never()).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the connection limit check returns {@code false} if the tenant has been
     * configured with unlimited connections explicitly.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionLimitCheckSucceedsIfUnlimited(final VertxTestContext ctx) {

        givenCurrentConnections(9);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits().setMaxConnections(TenantConstants.UNLIMITED_CONNECTIONS));

        limitChecksImpl.isConnectionLimitReached(tenant, mock(SpanContext.class)).onComplete(
                ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(jsonRequest, never()).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the connection limit check returns {@code false} if the tenant's
     * configured connection limit is not yet reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionLimitCheckSucceeds(final VertxTestContext ctx) {

        givenCurrentConnections(9);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits().setMaxConnections(10));

        limitChecksImpl.isConnectionLimitReached(tenant, mock(SpanContext.class)).onComplete(
                ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        assertRequestParamsSet(bufferReq, getExpectedConnectionNumberQuery(tenant), QUERY_TIMEOUT, REQUEST_TIMEOUT);
                        verify(jsonRequest).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the connection limit check returns {@code true} if the tenant's
     * configured connection limit is already reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionLimitCheckFails(final VertxTestContext ctx) {

        givenCurrentConnections(10);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits().setMaxConnections(10));

        limitChecksImpl.isConnectionLimitReached(tenant, mock(SpanContext.class)).onComplete(
                ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertTrue(response);
                        assertRequestParamsSet(bufferReq, getExpectedConnectionNumberQuery(tenant), QUERY_TIMEOUT, REQUEST_TIMEOUT);
                        verify(jsonRequest).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the connection limit check returns {@code false} if the query on
     * the Prometheus server times out.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionLimitCheckSucceedsIfQueryTimesOut(final VertxTestContext ctx) {

        givenFailResponseWithTimeoutException();
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits().setMaxConnections(10));

        limitChecksImpl.isConnectionLimitReached(tenant, mock(SpanContext.class)).onComplete(
                ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        assertRequestParamsSet(bufferReq, getExpectedConnectionNumberQuery(tenant), QUERY_TIMEOUT, REQUEST_TIMEOUT);
                        verify(jsonRequest).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     *
     * Verifies that the message limit check returns {@code false} if the limit is not exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitNotExceeded(final VertxTestContext ctx) {

        givenDataVolumeUsageInBytes(90);
        final long incomingMessageSize = 10;
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits()
                        .setDataVolume(new DataVolume(
                                Instant.parse("2019-01-03T14:30:00Z"),
                                new ResourceLimitsPeriod(ResourceLimitsPeriod.PERIOD_MODE_DAYS).setNoOfDays(30),
                                100L)));
        // current accounting period started at Jan 3rd 2:30 PM UTC
        limitChecksImpl.setClock(Clock.fixed(Instant.parse("2019-01-13T14:30:00Z"), ZoneOffset.UTC));

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        assertRequestParamsSet(bufferReq, getExpectedDataVolumeQuery(tenant, 10 * 24 * 60), QUERY_TIMEOUT, REQUEST_TIMEOUT);
                        verify(jsonRequest).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the message limit check returns {@code true} if the limit is exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitExceeded(final VertxTestContext ctx) {

        // GIVEN a tenant with a monthly plan effective since April 4th 12 AM UTC
        // and a max data volume of 100 bytes per month
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits()
                        .setDataVolume(new DataVolume(
                                Instant.parse("2019-04-04T00:00:00Z"),
                                new ResourceLimitsPeriod(ResourceLimitsPeriod.PERIOD_MODE_MONTHLY),
                                100L)));
        // resulting in an effective data volume limit of 90 bytes for the initial accounting period
        limitChecksImpl.setClock(Clock.fixed(Instant.parse("2019-04-14T00:00:00Z"), ZoneOffset.UTC));
        // and 70 bytes of the data volume already used up
        givenDataVolumeUsageInBytes(70);

        // WHEN checking the message limit for a new message with 21 bytes of payload
        limitChecksImpl.isMessageLimitReached(tenant, 21, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        // THEN the limit is reported as being exceeded
                        assertTrue(response);
                        assertRequestParamsSet(bufferReq, getExpectedDataVolumeQuery(tenant, 10 * 24 * 60), QUERY_TIMEOUT, REQUEST_TIMEOUT);
                        verify(jsonRequest).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the message limit check returns {@code false} if no metrics are
     * available (yet).
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitNotExceededForMissingMetrics(final VertxTestContext ctx) {

        givenDataVolumeUsageInBytes(null);
        final long incomingMessageSize = 20;
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits()
                        .setDataVolume(new DataVolume(
                                Instant.parse("2019-01-03T14:30:00Z"),
                                new ResourceLimitsPeriod(ResourceLimitsPeriod.PERIOD_MODE_DAYS).setNoOfDays(30),
                                100L)));

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize, span.context())
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        // THEN the limit is not exceeded
                        assertFalse(response);
                        verify(jsonRequest).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies the effective resource limit calculation for various scenarios.
     *
     */
    @Test
    public void verifyEffectiveResourceLimitCalculation() {

        final long configuredLimit = 9300;

        // Monthly mode
        // target date lies within the initial accounting period.
        final long numberOfMinutesInSeptember = 24 * 60 * 30;
        // next accounting period starts Oct 1st at midnight (start of day) UTC
        final double remainingMinutesTillStartOfNextAccountingPeriod = 24 * 60 * 28 + 9 * 60 + 30;
        final long expectedEffectiveLimit = (long) Math.ceil(remainingMinutesTillStartOfNextAccountingPeriod * configuredLimit / numberOfMinutesInSeptember );
        assertEquals(expectedEffectiveLimit,
                limitChecksImpl.calculateEffectiveLimit(
                        Instant.parse("2019-09-02T14:30:00Z"),
                        Instant.parse("2019-09-06T09:25:34Z"),
                        ResourceLimitsPeriod.PERIOD_MODE_MONTHLY,
                        configuredLimit));

        // target date lies not within the initial accounting period.
        assertEquals(configuredLimit,
                limitChecksImpl.calculateEffectiveLimit(
                        Instant.parse("2019-08-06T14:30:00Z"),
                        Instant.parse("2019-09-06T14:30:00Z"),
                        ResourceLimitsPeriod.PERIOD_MODE_MONTHLY,
                        configuredLimit));

        // Days mode
        // target date lies within the initial accounting period.
        assertEquals(configuredLimit,
                limitChecksImpl.calculateEffectiveLimit(
                        Instant.parse("2019-08-20T07:18:23Z"),
                        Instant.parse("2019-09-06T14:30:00Z"),
                        ResourceLimitsPeriod.PERIOD_MODE_DAYS,
                        configuredLimit));

        // target date lies not within the initial accounting period.
        assertEquals(configuredLimit,
                limitChecksImpl.calculateEffectiveLimit(
                        Instant.parse("2019-06-15T14:30:00Z"),
                        Instant.parse("2019-09-06T11:14:46Z"),
                        ResourceLimitsPeriod.PERIOD_MODE_DAYS,
                        configuredLimit));
}

    /**
     * Verifies the resource usage period calculation for various scenarios.
     *
     */
    @Test
    public void verifyResourceUsagePeriodCalculation() {

        // Monthly mode

        // within initial accounting period
        Instant since = Instant.parse("2019-09-06T07:15:00Z");
        Instant now = Instant.parse("2019-09-10T14:30:00Z");
        // most recent accounting period starts at effective since
        Instant currentAccountingPeriodStart = since;

        assertEquals(Duration.between(currentAccountingPeriodStart, now),
                limitChecksImpl.calculateResourceUsageDuration(
                        since,
                        now,
                        ResourceLimitsPeriod.PERIOD_MODE_MONTHLY,
                        30));

        // after initial accounting period
        since = Instant.parse("2019-08-06T05:11:00Z");
        now = Instant.parse("2019-09-06T14:30:00Z");
        // current accounting period starts on first day of current month at midnight (start of day) UTC
        currentAccountingPeriodStart = Instant.parse("2019-09-01T00:00:00Z");

        assertEquals(Duration.between(currentAccountingPeriodStart, now),
                limitChecksImpl.calculateResourceUsageDuration(
                        since,
                        now,
                        ResourceLimitsPeriod.PERIOD_MODE_MONTHLY,
                        30));

        // Days mode

        // within initial accounting period
        since = Instant.parse("2019-08-20T19:03:59Z");
        now = Instant.parse("2019-09-10T09:32:17Z");
        // current accounting period starts at effective since date-time
        currentAccountingPeriodStart = since;

        assertEquals(Duration.between(currentAccountingPeriodStart, now),
                limitChecksImpl.calculateResourceUsageDuration(
                        since,
                        now,
                        ResourceLimitsPeriod.PERIOD_MODE_DAYS,
                        30));

        // after initial accounting period
        since = Instant.parse("2019-07-03T11:49:22Z");
        now = Instant.parse("2019-09-10T14:30:11Z");
        // current accounting period starts 2 x 30 days after start
        currentAccountingPeriodStart = Instant.parse("2019-09-01T11:49:22Z");

        assertEquals(Duration.between(currentAccountingPeriodStart, now),
                limitChecksImpl.calculateResourceUsageDuration(
                        since,
                        now,
                        ResourceLimitsPeriod.PERIOD_MODE_DAYS,
                        30));
    }

    /**
     * Verifies that the message limit check returns {@code false} if the limit is not set
     * and that no call is made to retrieve metrics data from the Prometheus server.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitNotExceededWhenNotConfigured(final VertxTestContext ctx) {

        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);

        limitChecksImpl.isMessageLimitReached(tenant, 10, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(jsonRequest, never()).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the consumed bytes value is taken from the data volume cache.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMessageLimitUsesValueFromCache(final VertxTestContext ctx) {

        when(dataVolumeCache.get(anyString(), any(BiFunction.class)))
            .then(invocation -> {
                final CompletableFuture<LimitedResource<Long>> result = new CompletableFuture<>();
                result.complete(new LimitedResource<>(60L, 100L));
                return result;
            });
        final long incomingMessageSize = 20;
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits()
                        .setDataVolume(new DataVolume(
                                Instant.parse("2019-01-03T14:30:00Z"),
                                new ResourceLimitsPeriod(ResourceLimitsPeriod.PERIOD_MODE_DAYS).setNoOfDays(30),
                                100L)));

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize, span.context())
                .onComplete(ctx.succeeding(exceeded -> {
                    ctx.verify(() -> {
                        assertTrue(exceeded);
                        verify(jsonRequest, never()).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the message limit is not exceeded if no value is in the cache yet and the
     * Prometheus query is still running.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMessageLimitFallsBackToDefaultValueIfQueryStillRunning(final VertxTestContext ctx) {

        when(dataVolumeCache.get(anyString(), any(BiFunction.class)))
            .then(invocation -> {
                final CompletableFuture<LimitedResource<Long>> result = new CompletableFuture<>();
                return result;
            });
        final long incomingMessageSize = 20;
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits()
                        .setDataVolume(new DataVolume(
                                Instant.parse("2019-01-03T14:30:00Z"),
                                new ResourceLimitsPeriod(ResourceLimitsPeriod.PERIOD_MODE_DAYS).setNoOfDays(30),
                                100L)));

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize, span.context())
                .onComplete(ctx.succeeding(exceeded -> {
                    ctx.verify(() -> {
                        assertFalse(exceeded);
                        verify(jsonRequest, never()).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that on a data volume cache miss the current data volume consumption is retrieved from
     * the Prometheus server.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMessageLimitStoresValueToCache(final VertxTestContext ctx) {

        givenDataVolumeUsageInBytes(100);
        final long incomingMessageSize = 20;
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits()
                        .setDataVolume(new DataVolume(
                                Instant.parse("2019-01-03T14:30:00Z"),
                                new ResourceLimitsPeriod(ResourceLimitsPeriod.PERIOD_MODE_DAYS).setNoOfDays(30),
                                100L)));
        when(dataVolumeCache.get(anyString(), any(BiFunction.class))).then(invocation -> {
            final BiFunction<String, Executor, CompletableFuture<LimitedResource<Long>>> provider = invocation.getArgument(1);
            return provider.apply(invocation.getArgument(0), mock(Executor.class));
        });

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        verify(jsonRequest).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the function to compute a value asynchronously is invoked once only.
     */
    @Test
    public void testCaffeineAsyncCacheComputesValueOnceOnly() {

        final var computationsTriggered = new AtomicInteger(0);
        final var result = new CompletableFuture<LimitedResource<Long>>();
        final Executor executor = mock(Executor.class);
        doAnswer(invocation -> {
            final Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        // GIVEN an asynchronous Caffeine cache
        dataVolumeCache = Caffeine.newBuilder()
                .executor(executor)
                .buildAsync();

        final BiFunction<String, Executor, CompletableFuture<LimitedResource<Long>>> computation = (key, exec) -> {
            computationsTriggered.incrementAndGet();
            return result;
        };
        // WHEN getting the value for a key providing a function for computing the value asynchronously
        final var resultOne = dataVolumeCache.get("key", computation);
        // and retrieving the value for the same key again
        final var resultTwo = dataVolumeCache.get("key", computation);

        // THEN both invocations return the same incomplete future
        assertThat(resultOne.isDone()).isFalse();
        assertThat(resultTwo.isDone()).isFalse();
        assertThat(resultTwo == resultOne);
        // but the computation of the value has been triggered once only
        assertThat(computationsTriggered.get()).isEqualTo(1);

        // and WHEN the value has been computed
        result.complete(new LimitedResource<Long>(10L, 5L));

        // THEN a subsequent look up of the same key
        final var resultThree = dataVolumeCache.get("key", computation);

        // does not trigger computation of the value again
        assertThat(computationsTriggered.get()).isEqualTo(1);

        // but instead returns the existing completed future
        assertThat(resultThree == resultOne);
        assertThat(resultThree.isDone()).isTrue();
        try {
            assertThat(resultThree.get().getCurrentLimit()).isEqualTo(10L);
            assertThat(resultThree.get().getCurrentValue()).isEqualTo(5L);
        } catch (InterruptedException | ExecutionException e) {
            fail(e);
        }
    }


    /**
     *
     * Verifies that the connection duration limit check returns {@code false} if the limit is not exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionDurationLimitNotExceeded(final VertxTestContext ctx) {

        givenDeviceConnectionDurationInMinutes(90);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits()
                        .setConnectionDuration(new ConnectionDuration(
                                Instant.parse("2019-03-10T14:30:00Z"),
                                new ResourceLimitsPeriod(ResourceLimitsPeriod.PERIOD_MODE_MONTHLY),
                                100L)));
        // start of current accounting period is April 1st 12 AM UTC
        limitChecksImpl.setClock(Clock.fixed(Instant.parse("2019-04-11T00:00:00Z"), ZoneOffset.UTC));

        limitChecksImpl.isConnectionDurationLimitReached(tenant, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        assertRequestParamsSet(bufferReq, getExpectedConnectionDurationQuery(tenant, 10 * 24 * 60), QUERY_TIMEOUT, REQUEST_TIMEOUT);
                        verify(jsonRequest).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     *
     * Verifies that the connection duration limit check returns {@code true} if the limit is exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionDurationLimitExceeded(final VertxTestContext ctx) {

        givenDeviceConnectionDurationInMinutes(100);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits()
                        .setConnectionDuration(new ConnectionDuration(
                                Instant.parse("2019-01-03T14:30:00Z"),
                                new ResourceLimitsPeriod(ResourceLimitsPeriod.PERIOD_MODE_DAYS).setNoOfDays(30),
                                100L)));
        // start of current accounting period is Feb 2nd 2:30 PM UTC
        limitChecksImpl.setClock(Clock.fixed(Instant.parse("2019-02-12T14:30:00Z"), ZoneOffset.UTC));

        limitChecksImpl.isConnectionDurationLimitReached(tenant, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertTrue(response);
                        assertRequestParamsSet(bufferReq, getExpectedConnectionDurationQuery(tenant, 10 * 24 * 60), QUERY_TIMEOUT, REQUEST_TIMEOUT);
                        verify(jsonRequest).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the message limit check returns {@code false} if a timeout has occurred.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitNotExceededWhenTimeoutOccurred(final VertxTestContext ctx) {

        givenFailResponseWithTimeoutException();
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits()
                        .setDataVolume(new DataVolume(
                                Instant.parse("2019-01-03T14:30:00Z"),
                                new ResourceLimitsPeriod(ResourceLimitsPeriod.PERIOD_MODE_MONTHLY),
                                50L)));

        limitChecksImpl.isMessageLimitReached(tenant, 100L, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(jsonRequest).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the connection duration limit check returns {@code false} if no metrics are
     * available (yet).
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionDurationLimitNotExceededForMissingMetrics(final VertxTestContext ctx) {

        givenDeviceConnectionDurationInMinutes(null);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits()
                        .setConnectionDuration(new ConnectionDuration(
                                Instant.parse("2019-01-12T14:30:00Z"),
                                new ResourceLimitsPeriod(ResourceLimitsPeriod.PERIOD_MODE_MONTHLY),
                                100L)));
        // start of current accounting period is Jan 12th 2:30 PM UTC
        limitChecksImpl.setClock(Clock.fixed(Instant.parse("2019-01-22T14:30:00Z"), ZoneOffset.UTC));

        limitChecksImpl.isConnectionDurationLimitReached(tenant, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        // THEN the limit is not exceeded
                        assertFalse(response);
                        assertRequestParamsSet(bufferReq, getExpectedConnectionDurationQuery(tenant, 10 * 24 * 60), QUERY_TIMEOUT, REQUEST_TIMEOUT);
                        verify(jsonRequest).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     *
     * Verifies that the connection duration limit check returns {@code false} if a timeout occurred.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionDurationLimitNotExceededWhenTimeoutOccurred(final VertxTestContext ctx) {

        givenFailResponseWithTimeoutException();
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits()
                        .setConnectionDuration(new ConnectionDuration(
                                Instant.parse("2019-01-03T14:30:00Z"),
                                new ResourceLimitsPeriod(ResourceLimitsPeriod.PERIOD_MODE_DAYS).setNoOfDays(30),
                                10L)));
        limitChecksImpl.isConnectionDurationLimitReached(tenant, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(jsonRequest).send(VertxMockSupport.anyHandler());
                    });
                    ctx.completeNow();
                }));
    }

    private void givenCurrentConnections(final Integer currentConnections) {
        givenResponseWithValue(currentConnections);
    }

    private void givenDataVolumeUsageInBytes(final Integer consumedBytes) {
        givenResponseWithValue(consumedBytes);
    }

    private void givenDeviceConnectionDurationInMinutes(final Integer consumedMinutes) {
        givenResponseWithValue(consumedMinutes);
    }

    @SuppressWarnings("unchecked")
    private void givenResponseWithValue(final Integer value) {
        doAnswer(invocation -> {
            final Handler<AsyncResult<HttpResponse<JsonObject>>> responseHandler = invocation.getArgument(0);
            final HttpResponse<JsonObject> response = mock(HttpResponse.class);
            when(response.body()).thenReturn(createPrometheusResponse(value));
            responseHandler.handle(Future.succeededFuture(response));
            return null;
        }).when(jsonRequest).send(VertxMockSupport.anyHandler());
    }

    private void givenFailResponseWithTimeoutException() {
        doAnswer(invocation -> {
            final Handler<AsyncResult<HttpResponse<JsonObject>>> responseHandler = invocation.getArgument(0);
            responseHandler.handle(Future.failedFuture(new TimeoutException()));
            return null;
        }).when(jsonRequest).send(VertxMockSupport.anyHandler());
    }

    private static JsonObject createPrometheusResponse(final Integer value) {
        final JsonArray valueArray = new JsonArray();
        if (value != null) {
            valueArray.add("timestamp").add(String.valueOf(value));
        }
        return new JsonObject()
                .put("status", "success")
                .put("data", new JsonObject()
                        .put("result", new JsonArray().add(new JsonObject()
                                .put("value", valueArray))));
    }

    private static String getExpectedDataVolumeQuery(
            final TenantObject tenant,
            final long minutes) {

        return String.format(
                "floor(sum(increase(hono_messages_payload_bytes_sum{status=~\"forwarded|unprocessable\", tenant=\"%1$s\"} [%2$dm]) or vector(0))"
                + " + sum(increase(hono_commands_payload_bytes_sum{status=~\"forwarded|unprocessable\", tenant=\"%1$s\"} [%2$dm]) or vector(0)))",
                tenant.getTenantId(),
                minutes);
    }

    private static String getExpectedConnectionDurationQuery(
            final TenantObject tenant,
            final long minutes) {

        return String.format(
                "minute( sum( increase( hono_connections_authenticated_duration_seconds_sum {tenant=\"%1$s\"} [%2$dm])))",
                tenant.getTenantId(),
                minutes);
    }

    private static String getExpectedConnectionNumberQuery(final TenantObject tenant) {

        return String.format(
                "sum(hono_connections_authenticated{tenant=\"%1$s\"})",
                tenant.getTenantId());
    }

    private static void assertRequestParamsSet(
            final HttpRequest<?> request,
            final String expectedQuery,
            final int expectedQueryTimeoutMillis,
            final long expectedRequestTimeoutMillis) {
        verify(request).addQueryParam(eq("query"), eq(expectedQuery));
        verify(request).addQueryParam(eq("timeout"), eq(String.valueOf(expectedQueryTimeoutMillis) + "ms"));
        verify(request).timeout(expectedRequestTimeoutMillis);
    }
}
