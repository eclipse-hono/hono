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

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.verify;

import static com.google.common.truth.Truth.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutionException;

import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.DataVolume;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.ResourceLimitsPeriod;
import org.eclipse.hono.util.ResourceLimitsPeriod.PeriodMode;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;


/**
 * Tests verifying behavior of {@link DataVolumeAsyncCacheLoader}.
 *
 */
public class DataVolumeAsyncCacheLoaderTest extends AsyncCacheLoaderTestBase {

    private DataVolumeAsyncCacheLoader loader;

    /**
     * Sets up the loader to test.
     */
    @BeforeEach
    void setUpLoader() {
        loader = new DataVolumeAsyncCacheLoader(webClient, config, tracer);
    }

    private static String getExpectedDataVolumeQuery(
            final String tenantId,
            final long minutes) {

        return String.format(
                "floor(sum(increase(hono_telemetry_payload_bytes_sum{status=~\"forwarded|unprocessable\", tenant=\"%1$s\"} [%2$dm]) or vector(0))"
                + " + sum(increase(hono_command_payload_bytes_sum{status=~\"forwarded|unprocessable\", tenant=\"%1$s\"} [%2$dm]) or vector(0)))",
                tenantId,
                minutes);
    }

    private static TenantObject getTenantObject(
            final String tenantId,
            final Instant effectiveSince) {

        return TenantObject.from(tenantId)
                .setResourceLimits(new ResourceLimits()
                        .setDataVolume(new DataVolume(
                                effectiveSince,
                                new ResourceLimitsPeriod(PeriodMode.days).setNoOfDays(30),
                                90_000L)));
    }

    private void assertLimitedResourceResult(
            final TenantObject tenant,
            final long accountPeriodDurationMinutes,
            final Long expectedLimit,
            final Long expectedValue) {

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
                        getExpectedDataVolumeQuery(tenant.getTenantId(), accountPeriodDurationMinutes),
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
     * Verifies that the used data volume for the current accounting period can successfully be retrieved
     * from the Prometheus server.
    */
   @Test
   public void testAsyncLoadSucceeds() {

       givenDataVolumeUsageInBytes(40_000);
       final var tenant = getTenantObject(TENANT_ID, Instant.parse("2019-04-01T00:00:00Z"));
       // current accounting period started at May 1st 12:00 AM UTC
       loader.setClock(Clock.fixed(Instant.parse("2019-05-11T00:00:00Z"), ZoneOffset.UTC));

       assertLimitedResourceResult(tenant, 10 * 24 * 60, 90_000L, 40_000L);
   }

   /**
    * Verifies that the used data volume for the current accounting period can successfully be retrieved
    * from the Prometheus server.
   */
  @Test
  public void testAsyncLoadReturnsZeroBytesForMissingMetrics() {

      givenDataVolumeUsageInBytes(null);
      final var tenant = getTenantObject(TENANT_ID, Instant.parse("2019-01-03T14:30:00Z"));
      // current accounting period started at Jan 3rd 2:30 PM UTC
      loader.setClock(Clock.fixed(Instant.parse("2019-01-13T14:30:00Z"), ZoneOffset.UTC));

      assertLimitedResourceResult(tenant, 10 * 24 * 60, 90_000L, 0L);
  }

}
