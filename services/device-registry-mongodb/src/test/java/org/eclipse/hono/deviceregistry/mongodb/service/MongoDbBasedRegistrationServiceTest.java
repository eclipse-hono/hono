/*****************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.service.device.AutoProvisioner;
import org.eclipse.hono.deviceregistry.service.device.AutoProvisionerConfigProperties;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.registration.AbstractRegistrationServiceTest;
import org.eclipse.hono.service.registration.RegistrationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests for {@link MongoDbBasedRegistrationService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class MongoDbBasedRegistrationServiceTest implements AbstractRegistrationServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedRegistrationServiceTest.class);

    private final MongoDbBasedRegistrationConfigProperties config = new MongoDbBasedRegistrationConfigProperties();
    private MongoClient mongoClient;
    private MongoDbBasedRegistrationService registrationService;
    private Vertx vertx;

    /**
     * Starts up the service.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @BeforeAll
    public void setup(final VertxTestContext testContext) {

        vertx = Vertx.vertx();
        mongoClient = MongoDbTestUtils.getMongoClient(vertx, "hono-devices-test");

        registrationService = new MongoDbBasedRegistrationService(
                vertx,
                mongoClient,
                config);

        final AutoProvisioner autoProvisioner = new AutoProvisioner();
        autoProvisioner.setConfig(new AutoProvisionerConfigProperties());
        autoProvisioner.setDeviceManagementService(registrationService);

        registrationService.setAutoProvisioner(autoProvisioner);

        registrationService.createIndices().onComplete(testContext.completing());
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
        registrationService.stop()
            .onComplete(s -> vertx.close(testContext.completing()));
    }

    @Override
    public RegistrationService getRegistrationService() {
        return this.registrationService;
    }

    @Override
    public DeviceManagementService getDeviceManagementService() {
        return this.registrationService;
    }

}
