/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.mongodb.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedTenantDao;
import org.eclipse.hono.deviceregistry.util.Assertions;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.tenant.AbstractTenantServiceTest;
import org.eclipse.hono.service.tenant.TenantService;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests for {@link MongoDbBasedTenantService} and {@link MongoDbBasedTenantManagementService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class MongoDbBasedTenantServiceTest implements AbstractTenantServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedTenantServiceTest.class);

    private final MongoDbBasedTenantsConfigProperties config = new MongoDbBasedTenantsConfigProperties();
    private MongoDbBasedTenantService tenantService;
    private MongoDbBasedTenantManagementService tenantManagementService;
    private MongoDbBasedTenantDao dao;
    private Vertx vertx;

    /**
     * Starts up the service.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @BeforeAll
    public void startService(final VertxTestContext testContext) {

        vertx = Vertx.vertx();
        dao = MongoDbTestUtils.getTenantDao(vertx, "hono-tenants-test");
        tenantService = new MongoDbBasedTenantService(dao, config);
        tenantManagementService = new MongoDbBasedTenantManagementService(vertx, dao, config);
        dao.createIndices().onComplete(testContext.succeedingThenComplete());
    }

    /**
     * Prints the test name.
     *
     * @param testInfo Test case meta information.
     */
    @BeforeEach
    public void setup(final TestInfo testInfo) {
        LOG.info("running {}", testInfo.getDisplayName());
    }

    /**
     * Cleans up the collection after tests.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterEach
    public void cleanCollection(final VertxTestContext testContext) {
        dao.deleteAllFromCollection().onComplete(testContext.succeedingThenComplete());
    }

    /**
     * Releases the connection to the Mongo DB.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterAll
    public void closeDao(final VertxTestContext testContext) {
        final Promise<Void> daoCloseHandler = Promise.promise();
        dao.close(daoCloseHandler);

        daoCloseHandler.future()
            .compose(ok -> {
                final Promise<Void> vertxCloseHandler = Promise.promise();
                vertx.close(vertxCloseHandler);
                return vertxCloseHandler.future();
            })
            .onComplete(testContext.succeedingThenComplete());
    }

    @Override
    public TenantService getTenantService() {
        return tenantService;
    }

    @Override
    public TenantManagementService getTenantManagementService() {
        return tenantManagementService;
    }

    /**
     * Verifies that a tenant cannot be added if it uses an already registered
     * alias.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddTenantFailsForDuplicateAlias(final VertxTestContext ctx) {

        final var tenantSpec = new Tenant().setAlias("the-alias");
        addTenant("tenant", tenantSpec)
            .compose(ok -> getTenantManagementService().createTenant(
                    Optional.of("other-tenant"),
                    tenantSpec,
                    NoopSpan.INSTANCE))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_CONFLICT));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a tenant cannot be updated with an alias that is already in use by another tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateTenantFailsForDuplicateAlias(final VertxTestContext ctx) {

        final var tenantSpec = new Tenant().setAlias("the-alias");
        addTenant("tenant", tenantSpec)
            .compose(ok -> getTenantManagementService().createTenant(
                    Optional.of("other-tenant"),
                    new Tenant(),
                    NoopSpan.INSTANCE))
            .compose(ok -> getTenantManagementService().updateTenant(
                    "other-tenant",
                    tenantSpec,
                    Optional.empty(),
                    NoopSpan.INSTANCE))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_CONFLICT));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a tenant can be looked up by its alias using the {@link TenantManagementService} API.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantByAliasSucceedsForExistingTenant(final VertxTestContext ctx) {

        final Tenant tenantSpec = new Tenant().setAlias("the-alias");

        // GIVEN a tenant that has been added via the Management API
        addTenant("tenant", tenantSpec)
            .compose(ok -> {
                ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_CREATED, ok.getStatus());
                });
                // WHEN retrieving the tenant by alias using the Tenant API
                return getTenantService().get("the-alias", NoopSpan.INSTANCE);
            })
            .onComplete(ctx.succeeding(tenantResult -> {
                ctx.verify(() -> {
                    // THEN the tenant is found
                    assertThat(tenantResult.isOk()).isTrue();
                    // and the response can be cached
                    assertThat(tenantResult.getCacheDirective()).isNotNull();
                    assertThat(tenantResult.getCacheDirective().isCachingAllowed()).isTrue();
                    assertThat(tenantResult.getPayload().getString(RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID))
                        .isEqualTo("tenant");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the service returns 404 if a client wants to retrieve a tenant using a non-existing
     * identifier/alias.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantFailsForNonMatchingAlias(final VertxTestContext ctx) {

        // GIVEN a tenant that has been added via the Management API
        addTenant("tenant", new Tenant().setAlias("the-alias"))
            .compose(ok -> {
                ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_CREATED, ok.getStatus());
                });
                // WHEN retrieving the tenant by a non-matching identifier
                return getTenantService().get("not-the-alias", NoopSpan.INSTANCE);
            })
            .onComplete(ctx.succeeding(s -> {
                // THEN no tenant is found
                ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_NOT_FOUND, s.getStatus()));
                ctx.completeNow();
            }));
    }
}
