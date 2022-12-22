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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.ResourceLimitsPeriod.PeriodMode;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;


/**
 * Tests verifying behavior of {@code PrometheusBasedAsyncCacheLoader}.
 *
 */
class PrometheusBasedAsyncCacheLoaderTest extends AsyncCacheLoaderTestBase {

    private PrometheusBasedAsyncCacheLoader<LimitedResourceKey, Long> loader;
    private CompletableFuture<Long> result;

    /**
     */
    @BeforeEach
    void setUpLoader() {
        result = CompletableFuture.completedFuture(10L);

        loader = new PrometheusBasedAsyncCacheLoader<>(webClient, config, tracer) {

            @Override
            public CompletableFuture<Long> asyncLoad(
                    final LimitedResourceKey key,
                    final Executor executor) {
                executeQuery("query", null);
                return result;
            }
        };
    }

    /**
     * Verifies that the Basic authentication header is set if username and password have been
     * configured.
     */
    @Test
    void testExecuteQuerySetsAuthHeader() {

        config.setUsername("hono");
        config.setPassword("hono-secret");

        givenCurrentConnections(0);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits().setMaxConnections(10));

        final var key = new LimitedResourceKey(tenant.getTenantId(), (tenantId, ctx) -> Future.succeededFuture(tenant));
        try {
            final CompletableFuture<? extends Long> result = loader.asyncLoad(key, executor);
            assertThat(result.isDone()).isTrue();
            verify(bufferReq).basicAuthentication(eq("hono"), eq("hono-secret"));
        } catch (final Exception e) {
            fail(e);
        }
    }

    /**
     * Verifies the effective resource limit calculation for various scenarios.
     *
     */
    @Test
    void verifyEffectiveResourceLimitCalculation() {

        final long configuredLimit = 9300;

        // Monthly mode
        // target date lies within the initial accounting period.
        final long numberOfMinutesInSeptember = 24 * 60 * 30;
        // next accounting period starts Oct 1st at midnight (start of day) UTC
        final double remainingMinutesTillStartOfNextAccountingPeriod = 24 * 60 * 28 + 9 * 60 + 30;
        final long expectedEffectiveLimit = (long) Math.ceil(remainingMinutesTillStartOfNextAccountingPeriod * configuredLimit / numberOfMinutesInSeptember );
        assertEquals(expectedEffectiveLimit,
                loader.calculateEffectiveLimit(
                        Instant.parse("2019-09-02T14:30:00Z"),
                        Instant.parse("2019-09-06T09:25:34Z"),
                        PeriodMode.monthly,
                        configuredLimit));

        // target date lies not within the initial accounting period.
        assertEquals(configuredLimit,
                loader.calculateEffectiveLimit(
                        Instant.parse("2019-08-06T14:30:00Z"),
                        Instant.parse("2019-09-06T14:30:00Z"),
                        PeriodMode.monthly,
                        configuredLimit));

        // Days mode
        // target date lies within the initial accounting period.
        assertEquals(configuredLimit,
                loader.calculateEffectiveLimit(
                        Instant.parse("2019-08-20T07:18:23Z"),
                        Instant.parse("2019-09-06T14:30:00Z"),
                        PeriodMode.days,
                        configuredLimit));

        // target date lies not within the initial accounting period.
        assertEquals(configuredLimit,
                loader.calculateEffectiveLimit(
                        Instant.parse("2019-06-15T14:30:00Z"),
                        Instant.parse("2019-09-06T11:14:46Z"),
                        PeriodMode.days,
                        configuredLimit));
    }
}
