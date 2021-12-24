/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.verify;

import static com.google.common.truth.Truth.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutionException;

import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.ConnectionDuration;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.ResourceLimitsPeriod;
import org.eclipse.hono.util.ResourceLimitsPeriod.PeriodMode;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

/**
 * Tests verifying behavior of  {@link ConnectionDurationAsyncCacheLoader}.
 *
 */
public class ConnectionDurationAsyncCacheLoaderTest extends AsyncCacheLoaderTestBase {

    private ConnectionDurationAsyncCacheLoader loader;

    /**
     * Sets up the loader to test.
     */
    @BeforeEach
    void setUpLoader() {
        loader = new ConnectionDurationAsyncCacheLoader(webClient, config, tracer);
    }

    private static String getExpectedConnectionDurationQuery(
            final String tenantId,
            final long minutes) {

        return String.format(
                "minute( sum( increase( hono_connections_authenticated_duration_seconds_sum {tenant=\"%1$s\"} [%2$dm])))",
                tenantId,
                minutes);
    }

    private static TenantObject getTenantObject(
            final String tenantId,
            final Instant effectiveSince) {

        return TenantObject.from(tenantId)
                .setResourceLimits(new ResourceLimits()
                        .setConnectionDuration(new ConnectionDuration(
                                effectiveSince,
                                new ResourceLimitsPeriod(PeriodMode.monthly),
                                90L)));
    }

    private void assertLimitedResourceResult(
            final TenantObject tenant,
            final long accountPeriodDurationMinutes,
            final Duration expectedLimit,
            final Duration expectedValue) {

        final var key = new LimitedResourceKey(tenant.getTenantId(), (tenantId, ctx) -> Future.succeededFuture(tenant));
        final var result = loader.asyncLoad(key, executor);

        assertThat(result.isDone()).isTrue();

        try {
            final var value = result.get();
            assertThat(value.getCurrentValue()).isEqualTo(expectedValue);
            if (expectedLimit == null) {
                assertThat(value.getCurrentLimit()).isNull();
            } else {
                assertRequestParamsSet(
                        bufferReq,
                        getExpectedConnectionDurationQuery(tenant.getTenantId(), accountPeriodDurationMinutes),
                        QUERY_TIMEOUT,
                        REQUEST_TIMEOUT);
                verify(jsonRequest).send(VertxMockSupport.anyHandler());
                assertThat(value.getCurrentLimit()).isEqualTo(expectedLimit);
            }
        } catch (InterruptedException | ExecutionException e) {
            fail(e);
        }
    }

    /**
     * Verifies that the loader returns the current limit and consumed connection duration.
     */
    @Test
    public void testAsyncLoadSucceeds() {

        givenDeviceConnectionDurationInMinutes(20);
        final var tenant = getTenantObject(
                TENANT_ID,
                Instant.parse("2019-04-11T00:00:00Z"));
        // start of current accounting period is Apr 11th 00:00 AM UTC
        loader.setClock(Clock.fixed(Instant.parse("2019-04-21T00:00:00Z"), ZoneOffset.UTC));

        assertLimitedResourceResult(tenant, 10 * 24 * 60, Duration.ofMinutes(60L), Duration.ofMinutes(20L));
    }

    /**
     * Verifies that the loader returns a zero length duration if no metrics are available (yet).
     */
    @Test
    public void testAsyncLoadReturnsZeroDurationForMissingMetrics() {

        givenDeviceConnectionDurationInMinutes(null);
        final var tenant = getTenantObject(
                TENANT_ID,
                Instant.parse("2019-01-12T00:00:00Z"));
        // start of current accounting period is Apr 1st 00:00 AM UTC
        loader.setClock(Clock.fixed(Instant.parse("2019-04-21T00:00:00Z"), ZoneOffset.UTC));

        assertLimitedResourceResult(tenant, 20 * 24 * 60, Duration.ofMinutes(90L), Duration.ZERO);
    }

    /**
     * Verifies that the loader returns a zerlo length duration if connection to the Prometheus server times out.
     */
    @Test
    public void testAsyncLoadReturnsZeroDurationIfConnectionTimesOut() {

        givenFailResponseWithTimeoutException();
        final var tenant = getTenantObject(
                TENANT_ID,
                Instant.parse("2019-01-12T14:30:00Z"));
        // start of current accounting period is Jan 12th 2:30 PM UTC
        loader.setClock(Clock.fixed(Instant.parse("2019-01-22T14:30:00Z"), ZoneOffset.UTC));

        final var key = new LimitedResourceKey(TENANT_ID, (tenantId, ctx) -> Future.succeededFuture(tenant));
        final var result = loader.asyncLoad(key, executor);

        assertThat(result.isDone()).isTrue();
        assertRequestParamsSet(
                bufferReq,
                getExpectedConnectionDurationQuery(TENANT_ID, 10 * 24 * 60), QUERY_TIMEOUT, REQUEST_TIMEOUT);
        verify(jsonRequest).send(VertxMockSupport.anyHandler());

        assertThrows(ExecutionException.class, () -> result.get());
    }
}
