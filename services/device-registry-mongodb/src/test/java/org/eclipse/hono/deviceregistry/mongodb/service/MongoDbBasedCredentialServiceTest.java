/*****************************************************************************
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

import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.service.credentials.AbstractCredentialsServiceTest;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests for {@link MongoDbBasedCredentialsService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class MongoDbBasedCredentialServiceTest extends AbstractCredentialsServiceTest {

    private MongoClient mongoClient;
    private final MongoDbBasedCredentialsConfigProperties credentialsServiceConfig = new MongoDbBasedCredentialsConfigProperties();
    private MongoDbBasedCredentialsService credentialsService;
    private MongoDbBasedRegistrationService registrationService;
    private MongoDbBasedDeviceBackend deviceBackendService;
    private Vertx vertx;

    /**
     * Sets up static fixture.
     *
     * @param testContext The test context to use for running asynchronous tests.
     * @throws IOException if the embedded mongo db could not be started on the available port.
     */
    @BeforeAll
    public void setup(final VertxTestContext testContext) throws IOException {

        vertx = Vertx.vertx();
        mongoClient = MongoDbTestUtils.getMongoClient(vertx, "hono-credentials-test");
        credentialsService = new MongoDbBasedCredentialsService(
                vertx,
                mongoClient,
                credentialsServiceConfig,
                new SpringBasedHonoPasswordEncoder());
        registrationService = new MongoDbBasedRegistrationService(
                vertx,
                mongoClient,
                new MongoDbBasedRegistrationConfigProperties());
        credentialsService.start().onComplete(testContext.completing());
        registrationService.start().onComplete(testContext.completing());
        deviceBackendService = new MongoDbBasedDeviceBackend(this.registrationService, this.credentialsService);
    }

    /**
     * Cleans up fixture.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterAll
    public void finishTest(final VertxTestContext testContext) {

        final Checkpoint shutdown = testContext.checkpoint(3);

        credentialsService.stop().onComplete(s -> shutdown.flag());
        registrationService.stop().onComplete(s -> shutdown.flag());
        mongoClient.close();
        vertx.close(s -> shutdown.flag());
    }

    /**
     * Cleans up the collection after tests.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterEach
    public void cleanCollection(final VertxTestContext testContext) {
        mongoClient.removeDocuments(credentialsServiceConfig.getCollectionName(), new JsonObject(),
                testContext.completing());
    }

    @Override
    public CredentialsService getCredentialsService() {
        return this.credentialsService;
    }

    @Override
    public CredentialsManagementService getCredentialsManagementService() {
        return this.credentialsService;
    }

    @Override
    public DeviceManagementService getDeviceManagementService() {
        return this.deviceBackendService;
    }
}
