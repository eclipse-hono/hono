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

import java.util.concurrent.TimeUnit;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedTenantDao;
import org.eclipse.hono.service.management.tenant.AbstractTenantManagementSearchTenantsTest;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying tenant search functionality of {@link MongoDbBasedTenantManagementService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class MongoDBBasedTenantManagementSearchTenantsTest implements AbstractTenantManagementSearchTenantsTest {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBBasedTenantManagementSearchTenantsTest.class);

    private final MongoDbBasedTenantsConfigProperties config = new MongoDbBasedTenantsConfigProperties();
    private MongoDbBasedTenantManagementService tenantManagementService;
    private MongoDbBasedTenantDao dao;
    private Vertx vertx;

    /**
     * Starts up the service.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @BeforeAll
    public void setup(final VertxTestContext testContext) {
        vertx = Vertx.vertx();
        dao = MongoDbTestUtils.getTenantDao(vertx, "hono-search-tenants-test");
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
     * @param testInfo Test case meta information.
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterEach
    public void cleanCollection(final TestInfo testInfo, final VertxTestContext testContext) {
        LOG.info("finished {}", testInfo.getDisplayName());
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
    public TenantManagementService getTenantManagementService() {
        return this.tenantManagementService;
    }
}
