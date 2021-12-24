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

import java.util.concurrent.ExecutionException;

import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;


/**
 * Tests verifying behavior of {@link ConnectedDevicesAsyncCacheLoader}.
 *
 */
public class ConnectedDevicesAsyncCacheLoaderTest extends AsyncCacheLoaderTestBase {


    private ConnectedDevicesAsyncCacheLoader loader;

    /**
     * Sets up the loader to test.
     */
    @BeforeEach
    void setUpLoader() {
        loader = new ConnectedDevicesAsyncCacheLoader(webClient, config, tracer);
    }

    private static String getExpectedConnectionNumberQuery(final String tenant) {

        return String.format(
                "sum(hono_connections_authenticated{tenant=\"%1$s\"})",
                tenant);
    }

    private static TenantObject getTenantObject(
            final String tenantId,
            final int maxConnections) {

        return TenantObject.from(tenantId)
                .setResourceLimits(new ResourceLimits().setMaxConnections(maxConnections));
    }

    private void assertLimitedResourceResult(
            final TenantObject tenant,
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
                assertRequestParamsSet(bufferReq, getExpectedConnectionNumberQuery(tenant.getTenantId()), QUERY_TIMEOUT, REQUEST_TIMEOUT);
                verify(jsonRequest).send(VertxMockSupport.anyHandler());
                assertThat(value.getCurrentLimit()).isEqualTo(expectedLimit);
            }
        } catch (InterruptedException | ExecutionException e) {
            fail(e);
        }
    }

    /**
     * Verifies that the current number of connected devices
     * can successfully be retrieved from the Prometheus server.
     */
    @Test
    public void testAsyncLoadSucceeds() {

        givenCurrentConnections(9);
        final var tenant = getTenantObject(TENANT_ID, 100);

        assertLimitedResourceResult(tenant, 100L, 9L);
    }

    /**
     * Verifies that the loader returns zero current connections if no metrics are available (yet).
     */
    @Test
    public void testAsyncLoadReturnsZeroConnectionsForMissingMetrics() {

        givenCurrentConnections(null);
        final var tenant = getTenantObject(TENANT_ID, 100);

        assertLimitedResourceResult(tenant, 100L, 0L);
    }

    /**
     * Verifies that a limited resource with no limit is returned if the tenant has been
     * configured without any resource limits.
     */
    @Test
    public void testAsyncLoadSucceedsIfNoResourceLimitsSet() {

        givenCurrentConnections(90);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);

        assertLimitedResourceResult(tenant, null, 0L);
    }

    /**
     * Verifies that a limited resource with no limit is returned if the tenant has been
     * configured with unlimited connections explicitly.
     */
    @Test
    public void testAsyncLoadSucceedsIfUnlimited() {

        givenCurrentConnections(90);
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true)
                .setResourceLimits(new ResourceLimits().setMaxConnections(TenantConstants.UNLIMITED_CONNECTIONS));

        assertLimitedResourceResult(tenant, null, 0L);
    }


    /**
     * Verifies that the future returned for query is failed if the connection to
     * the Prometheus server times out.
     */
    @Test
    public void testAsyncLoadFailsIfConnectionTimesOut() {

        givenFailResponseWithTimeoutException();
        final var tenant = getTenantObject(TENANT_ID, 100);

        final var key = new LimitedResourceKey(TENANT_ID, (tenantId, ctx) -> Future.succeededFuture(tenant));
        final var result = loader.asyncLoad(key, executor);

        assertThat(result.isDone()).isTrue();
        assertRequestParamsSet(bufferReq, getExpectedConnectionNumberQuery(TENANT_ID), QUERY_TIMEOUT, REQUEST_TIMEOUT);
        verify(jsonRequest).send(VertxMockSupport.anyHandler());

        assertThrows(ExecutionException.class, () -> result.get());
    }

}
