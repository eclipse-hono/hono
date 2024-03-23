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

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedCredentialsDao;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedDeviceDao;
import org.eclipse.hono.service.management.device.AbstractDeviceManagementSearchDevicesTest;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of
 * {@link MongoDbBasedDeviceManagementService#searchDevices(String, int, int, List, List, java.util.Optional, io.opentracing.Span)}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public final class MongoDBBasedDeviceManagementSearchDevicesTest implements AbstractDeviceManagementSearchDevicesTest {

    private static final String DB_NAME = "hono-search-devices-test";
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBBasedDeviceManagementSearchDevicesTest.class);
    private final MongoDbBasedRegistrationConfigProperties config = new MongoDbBasedRegistrationConfigProperties();
    private MongoDbBasedDeviceDao deviceDao;
    private MongoDbBasedCredentialsDao credentialsDao;
    private MongoDbBasedDeviceManagementService service;
    private Vertx vertx;

    /**
     * Starts up the service.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @BeforeAll
    public void setup(final VertxTestContext testContext) {

        vertx = Vertx.vertx();
        deviceDao = MongoDbTestUtils.getDeviceDao(vertx, DB_NAME);
        credentialsDao = MongoDbTestUtils.getCredentialsDao(vertx, DB_NAME);
        service = new MongoDbBasedDeviceManagementService(vertx, deviceDao, credentialsDao, config);
        Future.all(deviceDao.createIndices(), credentialsDao.createIndices()).onComplete(testContext.succeedingThenComplete());
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
        Future.all(
                deviceDao.deleteAllFromCollection(),
                credentialsDao.deleteAllFromCollection())
            .onComplete(testContext.succeedingThenComplete());
    }

    /**
     * Releases the connection to the Mongo DB.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterAll
    public void closeDao(final VertxTestContext testContext) {

        final Promise<Void> devicesCloseHandler = Promise.promise();
        deviceDao.close(devicesCloseHandler);
        final Promise<Void> credentialsCloseHandler = Promise.promise();
        credentialsDao.close(credentialsCloseHandler);

        Future.all(credentialsCloseHandler.future(), devicesCloseHandler.future())
            .compose(ok -> {
                final Promise<Void> vertxCloseHandler = Promise.promise();
                vertx.close(vertxCloseHandler);
                return vertxCloseHandler.future();
            })
            .onComplete(testContext.succeedingThenComplete());
    }

    @Override
    public DeviceManagementService getDeviceManagementService() {
        return this.service;
    }
}
