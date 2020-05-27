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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbConfigProperties;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.tenant.AbstractTenantServiceTest;
import org.eclipse.hono.service.tenant.TenantService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests for {@link MongoDbBasedTenantService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
class MongoDbBasedTenantServiceTest extends AbstractTenantServiceTest {

    private final MongoDbTestUtils mongoDbTestUtils = new MongoDbTestUtils();
    private MongoClient mongoClient;
    private MongoDbBasedTenantService tenantService;
    private Vertx vertx;

    /**
     * Sets up static fixture.
     *
     * @param testContext The test context to use for running asynchronous tests.
     * @throws IOException if the embedded mongo db could not be started on the available port.
     */
    @BeforeAll
    public void setup(final VertxTestContext testContext) throws IOException {
        final String mongoDbHost = "127.0.0.1";
        final int mongoDbPort = Network.getFreeServerPort();
        final MongoDbConfigProperties mongoDbConfig = new MongoDbConfigProperties()
                .setHost(mongoDbHost)
                .setPort(mongoDbPort)
                .setDbName("hono-tenants-test");

        mongoDbTestUtils.startEmbeddedMongoDb(mongoDbHost, mongoDbPort);
        vertx = Vertx.vertx();
        mongoClient = MongoClient.createShared(vertx, mongoDbConfig.getMongoClientConfig());
        tenantService = new MongoDbBasedTenantService(
                vertx,
                mongoClient,
                new MongoDbBasedTenantsConfigProperties());
        tenantService.start().onComplete(testContext.completing());
    }

    /**
     * Cleans up fixture.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterAll
    public void finishTest(final VertxTestContext testContext) {
        final Checkpoint shutdown = testContext.checkpoint(3);
        tenantService.stop().onComplete(s -> shutdown.flag());
        mongoClient.close();
        vertx.close(s -> shutdown.flag());
        mongoDbTestUtils.stopEmbeddedMongoDb();
        shutdown.flag();
    }

    /**
     * Cleans up the collection after tests.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterEach
    public void cleanCollection(final VertxTestContext testContext) {
        mongoClient.removeDocuments(
                new MongoDbBasedTenantsConfigProperties().getCollectionName(), new JsonObject(),
                testContext.completing());
    }



    @Override
    public TenantService getTenantService() {
        return this.tenantService;
    }

    @Override
    public TenantManagementService getTenantManagementService() {
        return this.tenantService;
    }
}
