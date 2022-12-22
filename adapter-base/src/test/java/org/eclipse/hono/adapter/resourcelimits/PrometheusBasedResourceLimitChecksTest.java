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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies the behavior of {@link PrometheusBasedResourceLimitChecks}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, unit = TimeUnit.SECONDS)
public class PrometheusBasedResourceLimitChecksTest {

    private PrometheusBasedResourceLimitChecks limitChecksImpl;
    private AsyncLoadingCache<LimitedResourceKey, LimitedResource<Long>> connectionCountCache;
    private AsyncLoadingCache<LimitedResourceKey, LimitedResource<Duration>> connectionDurationCache;
    private AsyncLoadingCache<LimitedResourceKey, LimitedResource<Long>> dataVolumeCache;
    private Span span;
    private Tracer tracer;
    private TenantClient tenantClient;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {

        connectionCountCache = mock(AsyncLoadingCache.class);
        when(connectionCountCache.get(any(LimitedResourceKey.class))).thenAnswer(i -> {
            final var count = new CompletableFuture<Long>();
            count.complete(10L);
            return count;
        });
        connectionDurationCache = mock(AsyncLoadingCache.class);
        when(connectionDurationCache.get(any(LimitedResourceKey.class))).then(invocation -> {
            final var count = new CompletableFuture<Long>();
            count.complete(10L);
            return count;
        });
        dataVolumeCache = mock(AsyncLoadingCache.class);
        when(dataVolumeCache.get(any(LimitedResourceKey.class))).then(invocation -> {
            final var count = new CompletableFuture<Long>();
            count.complete(10L);
            return count;
        });

        span = TracingMockSupport.mockSpan();
        tracer = TracingMockSupport.mockTracer(span);
        tenantClient = mock(TenantClient.class);
        when(tenantClient.get(anyString(), any())).thenAnswer(i -> {
            final String tenantId = i.getArgument(0);
            final var result = TenantObject.from(tenantId);
            return Future.succeededFuture(result);
        });


        limitChecksImpl = new PrometheusBasedResourceLimitChecks(
                connectionCountCache,
                connectionDurationCache,
                dataVolumeCache,
                tenantClient,
                tracer);
    }

    /**
     * Verifies that the function to compute a value asynchronously is invoked once only.
     *
     * @throws Exception if the async cache throws fails to load values.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCaffeineAsyncCacheComputesValueOnceOnly() throws Exception {

        final var computationsTriggered = new AtomicInteger(0);
        final var result = new CompletableFuture<Long>();
        final AsyncCacheLoader<LimitedResourceKey, Long> loader = mock(AsyncCacheLoader.class);
        when(loader.asyncLoad(any(LimitedResourceKey.class), any(Executor.class))).thenAnswer(i -> {
            computationsTriggered.incrementAndGet();
            return result;
        });

        // GIVEN an asynchronous Caffeine cache
        final var cache = Caffeine.newBuilder().buildAsync(loader);

        final var key1 = new LimitedResourceKey("tenant", (tenantId, ctx) -> Future.succeededFuture());
        final var key2 = new LimitedResourceKey("tenant", (tenantId, ctx) -> Future.succeededFuture());
        final var key3 = new LimitedResourceKey("tenant", (tenantId, ctx) -> Future.succeededFuture());
        // WHEN getting the value for a tenant
        final var resultOne = cache.get(key1);
        // and retrieving the value for the same tenant again
        final var resultTwo = cache.get(key2);

        // THEN both invocations return the same incomplete future
        assertThat(resultOne.isDone()).isFalse();
        assertThat(resultTwo.isDone()).isFalse();
        assertThat(resultTwo == resultOne).isTrue();
        // but the computation of the value has been triggered once only
        assertThat(computationsTriggered.get()).isEqualTo(1);

        // and WHEN the value has been computed
        result.complete(5L);

        // THEN a subsequent look up of the same tenant
        final var resultThree = cache.get(key3);

        // does not trigger computation of the value again
        assertThat(computationsTriggered.get()).isEqualTo(1);

        // but instead returns the existing completed future
        assertThat(resultThree == resultOne).isTrue();
        assertThat(resultThree.isDone()).isTrue();
        try {
            assertThat(resultThree.get()).isEqualTo(5L);
        } catch (InterruptedException | ExecutionException e) {
            fail(e);
        }
    }

    /**
     * Verifies that the connection limit check returns {@code false} if no resource limits
     * have been defined for the tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionLimitCheckSucceedsIfNoResourceLimitsFound(final VertxTestContext ctx) {

        givenCurrentConnections(null, 9L);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT);

        limitChecksImpl.isConnectionLimitReached(tenant, mock(SpanContext.class)).onComplete(
                ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(connectionCountCache).get(argThat(spec -> spec.getTenantId().equals(tenant.getTenantId())));
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

        givenCurrentConnections(90L, 80L);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT);

        limitChecksImpl.isConnectionLimitReached(tenant, mock(SpanContext.class)).onComplete(
                ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(connectionCountCache).get(argThat(spec -> spec.getTenantId().equals(tenant.getTenantId())));
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

        givenCurrentConnections(80L, 100L);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT);

        limitChecksImpl.isConnectionLimitReached(tenant, mock(SpanContext.class)).onComplete(
                ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertTrue(response);
                        verify(connectionCountCache).get(argThat(spec -> spec.getTenantId().equals(tenant.getTenantId())));
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

        givenDataVolumeUsageInBytes(90_000L, 50_000L);
        final long incomingMessageSize = 10;
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT);

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(dataVolumeCache).get(argThat(spec -> spec.getTenantId().equals(tenant.getTenantId())));
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

        givenDataVolumeUsageInBytes(70_000L, 69_980L);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT);

        // WHEN checking the message limit for a new message with 21 bytes of payload
        limitChecksImpl.isMessageLimitReached(tenant, 21, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        // THEN the limit is reported as being exceeded
                        assertTrue(response);
                        verify(dataVolumeCache).get(argThat(spec -> spec.getTenantId().equals(tenant.getTenantId())));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the message limit check returns {@code false} if no resource limit is set.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitNotExceededIfNoResourceLimitsFound(final VertxTestContext ctx) {

        givenDataVolumeUsageInBytes(null, 69_980L);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT);

        limitChecksImpl.isMessageLimitReached(tenant, 10, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(dataVolumeCache).get(argThat(spec -> spec.getTenantId().equals(tenant.getTenantId())));
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
    public void testMessageLimitFallsBackToDefaultValueIfQueryStillRunning(final VertxTestContext ctx) {

        when(dataVolumeCache.get(any(LimitedResourceKey.class))).thenReturn(new CompletableFuture<LimitedResource<Long>>());
        final long incomingMessageSize = 20;
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT);

        limitChecksImpl.isMessageLimitReached(tenant, incomingMessageSize, span.context())
                .onComplete(ctx.succeeding(exceeded -> {
                    ctx.verify(() -> {
                        assertFalse(exceeded);
                        verify(dataVolumeCache).get(argThat(spec -> spec.getTenantId().equals(tenant.getTenantId())));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     *
     * Verifies that the connection duration limit check returns {@code false} if the limit is not exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionDurationLimitNotExceeded(final VertxTestContext ctx) {

        givenDeviceConnectionDuration(Duration.ofMinutes(90L), Duration.ofMinutes(20L));
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT);

        limitChecksImpl.isConnectionDurationLimitReached(tenant, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(connectionDurationCache).get(argThat(spec -> spec.getTenantId().equals(tenant.getTenantId())));
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

        givenDeviceConnectionDuration(Duration.ofMinutes(90L), Duration.ofMinutes(100L));
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT);

        limitChecksImpl.isConnectionDurationLimitReached(tenant, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertTrue(response);
                        verify(connectionDurationCache).get(argThat(spec -> spec.getTenantId().equals(tenant.getTenantId())));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the connection duration limit check returns {@code false} if a timeout occurred.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionDurationLimitCheckSucceedsIfNoResourceLimitsFound(final VertxTestContext ctx) {

        givenDeviceConnectionDuration(null, Duration.ofMinutes(20L));
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT);

        limitChecksImpl.isConnectionDurationLimitReached(tenant, mock(SpanContext.class))
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertFalse(response);
                        verify(connectionDurationCache).get(argThat(spec -> spec.getTenantId().equals(tenant.getTenantId())));
                    });
                    ctx.completeNow();
                }));
    }

    private void givenCurrentConnections(final Long maxConnections, final Long currentConnections) {
        when(connectionCountCache.get(any(LimitedResourceKey.class))).thenAnswer(i -> {
            final var count = new CompletableFuture<LimitedResource<Long>>();
            count.complete(new LimitedResource<>(maxConnections, currentConnections));
            return count;
        });
    }

    private void givenDataVolumeUsageInBytes(final Long maxBytes, final Long consumedBytes) {
        when(dataVolumeCache.get(any(LimitedResourceKey.class))).thenAnswer(i -> {
            final var count = new CompletableFuture<LimitedResource<Long>>();
            count.complete(new LimitedResource<>(maxBytes, consumedBytes.longValue()));
            return count;
        });
    }

    private void givenDeviceConnectionDuration(final Duration maxDuration, final Duration consumedConnectionDuration) {
        when(connectionDurationCache.get(any(LimitedResourceKey.class))).thenAnswer(i -> {
            final var count = new CompletableFuture<LimitedResource<Duration>>();
            count.complete(new LimitedResource<>(maxDuration, consumedConnectionDuration));
            return count;
        });
    }
}
