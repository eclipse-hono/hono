/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.management.tenant;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.util.Adapter;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

/**
 * A suite of tests for verifying implementations of the Tenant management's 
 * search tenants operation.
 * <p>
 * Concrete subclasses need to provide the service implementations under test
 * by means of implementing the {@link #getTenantManagementService()} method.
 * Also the subclasses should clean up any fixture in the database that has
 * been created by individual test cases.
 */
public interface AbstractTenantManagementSearchTenantsTest {

    /**
     * Gets tenant management service being tested.
     *
     * @return The tenant management service
     */
    TenantManagementService getTenantManagementService();

    /**
     * Verifies that a request to search tenants fails with a {@value HttpURLConnection#HTTP_NOT_FOUND}
     * when no matching tenants are found.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchTenantsWhenNoTenantsAreFound(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final Filter filter = new Filter("/enabled", false);

        createTenants(Map.of(tenantId, new Tenant().setEnabled(true)))
                .compose(ok -> getTenantManagementService()
                        .searchTenants(pageSize, pageOffset, List.of(filter), List.of(), NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        assertThat(s.isError()).isTrue();
                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to search tenants with a valid filter succeeds and matching tenants are found.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchTenantsWithAValidFilterSucceeds(final VertxTestContext ctx) {
        final String tenantId1 = DeviceRegistryUtils.getUniqueIdentifier();
        final String tenantId2 = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final Filter filter = new Filter("/enabled", true);

        createTenants(Map.of(
                tenantId1, new Tenant().setEnabled(true),
                tenantId2, new Tenant().setEnabled(false)))
                        .compose(ok -> getTenantManagementService()
                                .searchTenants(pageSize, pageOffset, List.of(filter), List.of(), NoopSpan.INSTANCE))
                        .onComplete(ctx.succeeding(s -> {
                            ctx.verify(() -> {
                                assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                                final SearchResult<TenantWithId> searchResult = s.getPayload();
                                assertThat(searchResult.getTotal()).isEqualTo(1);
                                assertThat(searchResult.getResult().get(0).getId()).isEqualTo(tenantId1);
                            });
                            ctx.completeNow();
                        }));
    }

    /**
     * Verifies that a request to search tenants with multiple filters succeeds and matching tenants are found.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchTenantsWithMultipleFiltersSucceeds(final VertxTestContext ctx) {
        final String tenantId1 = DeviceRegistryUtils.getUniqueIdentifier();
        final String tenantId2 = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final Filter filter1 = new Filter("/enabled", true);
        final Filter filter2 = new Filter("/adapters/0/type", "MQTT");
        final Filter filter3 = new Filter("/ext/group", "A");

        createTenants(Map.of(
                tenantId1, new Tenant().setEnabled(true).setExtensions(Map.of("group", "A")),
                tenantId2, new Tenant().setEnabled(true).addAdapterConfig(new Adapter("MQTT"))
                        .setExtensions(Map.of("group", "A"))))
                                .compose(ok -> getTenantManagementService()
                                        .searchTenants(pageSize, pageOffset, List.of(filter1, filter2, filter3),
                                                List.of(), NoopSpan.INSTANCE)
                                        .onComplete(ctx.succeeding(s -> {
                                            ctx.verify(() -> {
                                                assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                                                final SearchResult<TenantWithId> searchResult = s.getPayload();
                                                assertThat(searchResult.getTotal()).isEqualTo(1);
                                                assertThat(searchResult.getResult()).hasSize(1);
                                                assertThat(searchResult.getResult().get(0).getId())
                                                        .isEqualTo(tenantId2);
                                            });
                                            ctx.completeNow();
                                        })));
    }

    /**
     * Verifies that a request to search tenants with a valid pageSize succeeds and the result is in accordance
     * with the specified page size.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchTenantsWithPageSize(final VertxTestContext ctx) {
        final String tenantId1 = DeviceRegistryUtils.getUniqueIdentifier();
        final String tenantId2 = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 1;
        final int pageOffset = 0;
        final Filter filter = new Filter("/enabled", true);

        createTenants(Map.of(
                tenantId1, new Tenant().setEnabled(true),
                tenantId2, new Tenant().setEnabled(true)))
                        .compose(ok -> getTenantManagementService()
                                .searchTenants(pageSize, pageOffset, List.of(filter), List.of(), NoopSpan.INSTANCE))
                        .onComplete(ctx.succeeding(s -> {
                            ctx.verify(() -> {
                                assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                                final SearchResult<TenantWithId> searchResult = s.getPayload();
                                assertThat(searchResult.getTotal()).isEqualTo(2);
                                assertThat(searchResult.getResult()).hasSize(1);
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
    default void testSearchTenantsWithPageOffset(final VertxTestContext ctx) {
        final String tenantId1 = DeviceRegistryUtils.getUniqueIdentifier();
        final String tenantId2 = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 1;
        final int pageOffset = 1;
        final Filter filter = new Filter("/enabled", true);
        final Sort sortOption = new Sort("/ext/id");
        sortOption.setDirection(Sort.Direction.desc);

        createTenants(Map.of(
                tenantId1, new Tenant().setEnabled(true).setExtensions(Map.of("id", "1")),
                tenantId2, new Tenant().setEnabled(true).setExtensions(Map.of("id", "2"))))
                        .compose(ok -> getTenantManagementService()
                                .searchTenants(pageSize, pageOffset, List.of(filter), List.of(sortOption),
                                        NoopSpan.INSTANCE))
                        .onComplete(ctx.succeeding(s -> {
                            ctx.verify(() -> {
                                assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                                final SearchResult<TenantWithId> searchResult = s.getPayload();
                                assertThat(searchResult.getTotal()).isEqualTo(2);
                                assertThat(searchResult.getResult()).hasSize(1);
                                assertThat(searchResult.getResult().get(0).getId()).isEqualTo(tenantId1);
                            });
                            ctx.completeNow();
                        }));
    }

    /**
     * Verifies that a request to search tenants with a sort option succeeds and the result is in accordance with the
     * specified sort option.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchTenantsWithSortOptions(final VertxTestContext ctx) {
        final String tenantId1 = DeviceRegistryUtils.getUniqueIdentifier();
        final String tenantId2 = DeviceRegistryUtils.getUniqueIdentifier();
        final String tenantId3 = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 1;
        final int pageOffset = 0;
        final Filter filter = new Filter("/enabled", true);
        final Sort sortOption1 = new Sort("/ext/group");
        final Sort sortOption2 = new Sort("/ext/id");
        sortOption1.setDirection(Sort.Direction.desc);
        sortOption2.setDirection(Sort.Direction.asc);

        createTenants(Map.of(
                tenantId1, new Tenant().setEnabled(false).setExtensions(Map.of("id", "1", "group", "B")),
                tenantId2, new Tenant().setEnabled(true).setExtensions(Map.of("id", "2", "group", "B")),
                tenantId3, new Tenant().setEnabled(true).setExtensions(Map.of("id", "3", "group", "B"))))
                        .compose(ok -> getTenantManagementService()
                                .searchTenants(pageSize, pageOffset, List.of(filter), List.of(sortOption1, sortOption2),
                                        NoopSpan.INSTANCE))
                        .onComplete(ctx.succeeding(s -> {
                            ctx.verify(() -> {
                                assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                                final SearchResult<TenantWithId> searchResult = s.getPayload();
                                assertThat(searchResult.getTotal()).isEqualTo(2);
                                assertThat(searchResult.getResult()).hasSize(1);
                                assertThat(searchResult.getResult().get(0).getId()).isEqualTo(tenantId2);
                            });
                            ctx.completeNow();
                        }));
    }

    /**
     * Verifies that a request to search tenants with filters containing the wildcard character '*' 
     * succeeds and matching tenants are found.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchTenantsWithWildCardToMatchMultipleCharacters(final VertxTestContext ctx) {
        final String tenantId1 = DeviceRegistryUtils.getUniqueIdentifier();
        final String tenantId2 = DeviceRegistryUtils.getUniqueIdentifier();
        final String tenantId3 = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 10;
        final int pageOffset = 0;
        final Filter filter1 = new Filter("/ext/id", "tenant*-*");
        final Filter filter2 = new Filter("/ext/value", "test$2*e");
        final Sort sortOption = new Sort("/id");

        createTenants(Map.of(
                tenantId1, new Tenant().setEnabled(false).setExtensions(
                        Map.of("id", "tenant1-id")),
                tenantId2, new Tenant().setEnabled(true).setExtensions(
                        Map.of("id", "tenant2-id", "value", "test$2Value")),
                tenantId3, new Tenant().setEnabled(true).setExtensions(
                        Map.of("id", "tenant3-id", "value", "test$3Value"))))
                                .compose(ok -> getTenantManagementService()
                                        .searchTenants(pageSize, pageOffset, List.of(filter1, filter2),
                                                List.of(sortOption),
                                                NoopSpan.INSTANCE))
                                .onComplete(ctx.succeeding(s -> {
                                    ctx.verify(() -> {
                                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                                        final SearchResult<TenantWithId> searchResult = s.getPayload();
                                        assertThat(searchResult.getTotal()).isEqualTo(1);
                                        assertThat(searchResult.getResult()).hasSize(1);
                                        assertThat(searchResult.getResult().get(0).getId()).isEqualTo(tenantId2);
                                    });
                                    ctx.completeNow();
                                }));
    }

    /**
     * Verifies that a request to search tenants with filters containing the wildcard character '?' 
     * and matching tenants are found.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSearchTenantsWithCardToMatchSingleCharacter(final VertxTestContext ctx) {
        final String tenantId1 = DeviceRegistryUtils.getUniqueIdentifier();
        final String tenantId2 = DeviceRegistryUtils.getUniqueIdentifier();
        final String tenantId3 = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 1;
        final int pageOffset = 0;
        final Filter filter1 = new Filter("/ext/id", "testTenant-?");
        final Filter filter2 = new Filter("/ext/value", "test$?Value");
        final Sort sortOption = new Sort("/ext/id");
        sortOption.setDirection(Sort.Direction.desc);

        createTenants(Map.of(
                tenantId1, new Tenant().setEnabled(false).setExtensions(
                        Map.of("id", "testTenant-x", "value", "test$Value")),
                tenantId2, new Tenant().setEnabled(true).setExtensions(
                        Map.of("id", "testTenant-2", "value", "test$2Value")),
                tenantId3, new Tenant().setEnabled(true).setExtensions(
                        Map.of("id", "testTenant-3", "value", "test$3Value"))))
                                .compose(ok -> getTenantManagementService()
                                        .searchTenants(pageSize, pageOffset, List.of(filter1, filter2),
                                                List.of(sortOption),
                                                NoopSpan.INSTANCE))
                                .onComplete(ctx.succeeding(s -> {
                                    ctx.verify(() -> {
                                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                                        final SearchResult<TenantWithId> searchResult = s.getPayload();
                                        assertThat(searchResult.getTotal()).isEqualTo(2);
                                        assertThat(searchResult.getResult()).hasSize(1);
                                        assertThat(searchResult.getResult().get(0).getId()).isEqualTo(tenantId3);
                                    });
                                    ctx.completeNow();
                                }));
    }

    /**
     * Creates a set of tenants.
     *
     * @param tenantWithIds A list of tenant with ids.
     * @return A succeeded future if all the tenants have been created successfully.
     */
    default Future<?> createTenants(final Map<String, Tenant> tenantWithIds) {
        Future<?> createTenantsFuture = Future.succeededFuture();

        for (final Map.Entry<String, Tenant> entry : tenantWithIds.entrySet()) {
            createTenantsFuture = createTenantsFuture.compose(ok -> getTenantManagementService()
                    .createTenant(Optional.of(entry.getKey()), entry.getValue(), NoopSpan.INSTANCE)
                    .map(r -> {
                        assertThat(r.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED);
                        return null;
                    }));
        }
        return createTenantsFuture;
    }
}
