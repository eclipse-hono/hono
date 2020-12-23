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
package org.eclipse.hono.deviceregistry.mongodb.service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
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

import io.opentracing.Span;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests for {@link MongoDbBasedTenantService#searchTenants(int, int, List, List, Span)}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class MongoDBBasedTenantManagementSearchTenantsTest implements AbstractTenantManagementSearchTenantsTest {
    private static final Logger log = LoggerFactory.getLogger(MongoDBBasedTenantManagementSearchTenantsTest.class);
    private MongoClient mongoClient;
    private final MongoDbBasedTenantsConfigProperties config = new MongoDbBasedTenantsConfigProperties();
    private MongoDbBasedTenantService tenantManagementService;
    private Vertx vertx;

    /**
     * Starts up the service.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @BeforeAll
    public void setup(final VertxTestContext testContext) {
        vertx = Vertx.vertx();
        mongoClient = MongoDbTestUtils.getMongoClient(vertx, "hono-search-tenants-test");
        tenantManagementService = new MongoDbBasedTenantService(vertx, mongoClient, config);
        tenantManagementService.start().onComplete(testContext.completing());
    }

    /**
     * Prints the test name.
     *
     * @param testInfo Test case meta information.
     */
    @BeforeEach
    public void setup(final TestInfo testInfo) {
        log.info("running {}", testInfo.getDisplayName());
    }

    /**
     * Cleans up the collection after tests.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterEach
    public void cleanCollection(final VertxTestContext testContext) {
        mongoClient.removeDocuments(
                config.getCollectionName(),
                new JsonObject(),
                testContext.completing());
    }

    /**
     * Cleans up fixture.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterAll
    public void finishTest(final VertxTestContext testContext) {
        mongoClient.close();
        tenantManagementService.stop()
                .onComplete(s -> vertx.close(testContext.completing()));
    }

    @Override
    public TenantManagementService getTenantManagementService() {
        return this.tenantManagementService;
    }
}
