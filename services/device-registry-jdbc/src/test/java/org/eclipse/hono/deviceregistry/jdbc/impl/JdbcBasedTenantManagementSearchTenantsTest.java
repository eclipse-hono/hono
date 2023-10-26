/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceregistry.jdbc.impl;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantWithId;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

class JdbcBasedTenantManagementSearchTenantsTest extends AbstractJdbcRegistryTest {

    /**
     * Creates a set of tenants.
     *
     * @param tenantsToCreate The tenants to be created. The keys are the tenant identifiers.
     * @return A succeeded future if all the tenants have been created successfully.
     */
    private Future<Void> createTenants(final Map<String, Tenant> tenantsToCreate) {

        final List<Future<Object>> creationResult = tenantsToCreate.entrySet().stream()
                .map(entry -> getTenantManagementService().createTenant(
                        Optional.of(entry.getKey()),
                        entry.getValue(),
                        NoopSpan.INSTANCE)
                    .map(r -> {
                        assertThat(r.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED);
                        return null;
                    }))
                .collect(Collectors.toList());
        return Future.all(creationResult).mapEmpty();
    }

    /**
     * Verifies that a request to search tenants fails with a {@value HttpURLConnection#HTTP_NOT_FOUND}
     * when no matching tenants are found.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testSearchTenantsWhenNoTenantsAreFound(final VertxTestContext ctx) {
        final int pageSize = 10;
        final int pageOffset = 0;

        getTenantManagementService().searchTenants(
                pageSize,
                pageOffset,
                List.of(),
                List.of(),
                NoopSpan.INSTANCE)
        .onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                assertThat(ServiceInvocationException.extractStatusCode(t)).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
            });
            ctx.completeNow();
        }));
    }


    /**
     * Verifies that a request to search tenants with a valid pageSize succeeds and the result is in accordance
     * with the specified page size.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testSearchTenantsWithPageSize(final VertxTestContext ctx) {
        final String tenantId1 = "A" + DeviceRegistryUtils.getUniqueIdentifier();
        final String tenantId2 = "B" + DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 1;
        final int pageOffset = 0;

        createTenants(Map.of(
                tenantId1, new Tenant().setEnabled(true),
                tenantId2, new Tenant().setEnabled(true)))
            .onFailure(ctx::failNow)
            .compose(ok -> getTenantManagementService().searchTenants(
                    pageSize,
                    pageOffset,
                    List.of(),
                    List.of(),
                    NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(s -> {
                ctx.verify(() -> {
                    assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                    final SearchResult<TenantWithId> searchResult = s.getPayload();
                    assertThat(searchResult.getTotal()).isEqualTo(2);
                    assertThat(searchResult.getResult()).hasSize(pageSize);
                    assertThat(searchResult.getResult().get(0).getId()).isEqualTo(tenantId1);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to search tenants with valid page offset succeeds and the result is in accordance with
     * the specified page offset.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testSearchTenantsWithPageOffset(final VertxTestContext ctx) {
        final String tenantId1 = "A" + DeviceRegistryUtils.getUniqueIdentifier();
        final String tenantId2 = "B" + DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 1;
        final int pageOffset = 1;

        createTenants(Map.of(
                tenantId1, new Tenant().setEnabled(true),
                tenantId2, new Tenant().setEnabled(true)))
            .onFailure(ctx::failNow)
            .compose(ok -> getTenantManagementService().searchTenants(
                    pageSize,
                    pageOffset,
                    List.of(),
                    List.of(),
                    NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(s -> {
                ctx.verify(() -> {
                    assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                    final SearchResult<TenantWithId> searchResult = s.getPayload();
                    assertThat(searchResult.getTotal()).isEqualTo(2);
                    assertThat(searchResult.getResult()).hasSize(pageSize);
                    assertThat(searchResult.getResult().get(0).getId()).isEqualTo(tenantId2);
                });
                ctx.completeNow();
            }));
    }
}
