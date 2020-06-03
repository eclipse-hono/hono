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

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.service.registration.RegistrationServiceTests;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests for {@link MongoDbBasedRegistrationService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class MongoDbBasedRegistrationServiceTest extends RegistrationServiceTests {

    private MongoClient mongoClient;
    private MongoDbBasedRegistrationService registrationService;
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
        mongoClient = MongoDbTestUtils.getMongoClient(vertx, "hono-devices-test");
        registrationService = new MongoDbBasedRegistrationService(
                vertx,
                mongoClient,
                new MongoDbBasedRegistrationConfigProperties());
        registrationService.start().onComplete(testContext.completing());
    }

    /**
     * Cleans up fixture.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterAll
    public void finishTest(final VertxTestContext testContext) {

        final Checkpoint shutdown = testContext.checkpoint(2);
        registrationService.stop().onComplete(s -> shutdown.flag());
        mongoClient.close();
        vertx.close(s -> shutdown.flag());
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
