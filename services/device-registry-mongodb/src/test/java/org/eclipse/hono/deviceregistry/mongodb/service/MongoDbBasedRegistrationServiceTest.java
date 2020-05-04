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
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbCallExecutor;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.service.registration.RegistrationServiceTests;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.Vertx;
import io.vertx.ext.mongo.MongoClient;
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
    //The documentation suggests to make the MongodStarter instance static. 
    // For more info https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo
    private static final MongodStarter MONGOD_STARTER = MongodStarter.getDefaultInstance();
    private MongoDbBasedRegistrationService registrationService;
    private Vertx vertx;
    private MongodExecutable mongodExecutable;
    private MongodProcess mongodProcess;

    /**
     * Sets up static fixture.
     *
     * @param testContext The test context to use for running asynchronous tests.
     * @throws IOException if the embedded mongo db could not be started on the available port.
     */
    @BeforeAll
    public void setup(final VertxTestContext testContext) throws IOException {
        final String mongoDbHost = "localhost";
        final int mongoDbPort = Network.getFreeServerPort();

        startEmbeddedMongoDb(mongoDbHost, mongoDbPort);

        vertx = Vertx.vertx();

        registrationService = new MongoDbBasedRegistrationService();
        registrationService.setConfig(new MongoDbBasedRegistrationConfigProperties());

        final MongoClient mongoClient = MongoClient.createShared(vertx, new MongoDbConfigProperties()
                .setHost(mongoDbHost)
                .setPort(mongoDbPort)
                .setDbName("mongoDBTestDeviceRegistry")
                .getMongoClientConfig());
        registrationService.setMongoClient(mongoClient);
        registrationService.setExecutor(new MongoDbCallExecutor(vertx, mongoClient));

        registrationService.start().onComplete(testContext.completing());
    }

    /**
     * Cleans up fixture.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterAll
    public void finishTest(final VertxTestContext testContext) {
        registrationService.stop().onComplete(testContext.completing());
        vertx.close(testContext.completing());
        stopEmbeddedMongoDb();
    }

    @Override
    public RegistrationService getRegistrationService() {
        return this.registrationService;
    }

    @Override
    public DeviceManagementService getDeviceManagementService() {
        return this.registrationService;
    }

    private void startEmbeddedMongoDb(final String host, final int port) throws IOException {
        mongodExecutable = MONGOD_STARTER.prepare(new MongodConfigBuilder()
                .version(Version.Main.PRODUCTION)
                .net(new Net(host, port, Network.localhostIsIPv6()))
                .build());
        mongodProcess = mongodExecutable.start();
    }

    private void stopEmbeddedMongoDb() {
        mongodProcess.stop();
        mongodExecutable.stop();
    }
}
