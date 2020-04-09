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

import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbCallExecutor;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.service.registration.RegistrationServiceTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Context;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
@Timeout(timeUnit = TimeUnit.SECONDS, value = 3)
class MongoDbBasedRegistrationServiceTest extends RegistrationServiceTests {

    private MongoDbCallExecutor mongoDbCallExecutor;
    private MongoClient mongoClient;
    private MongoDbBasedRegistrationService svc;

    @BeforeEach
    void setUp() {
        final Context ctx = mock(Context.class);
        final Vertx vertx = mock(Vertx.class);
        final MongoDbBasedRegistrationConfigProperties config = new MongoDbBasedRegistrationConfigProperties();
        svc = spy(new MongoDbBasedRegistrationService());
        svc.setConfig(config);

        mongoDbCallExecutor = mock(MongoDbCallExecutor.class);
        mongoClient = new MongoDbClientMock();
        prepareMongoDbCallExecutorMock();
        svc.setExecutor(mongoDbCallExecutor);
        svc.start();
    }

    private void prepareMongoDbCallExecutorMock() {
        when(mongoDbCallExecutor.getMongoClient()).thenReturn(mongoClient);
        when(mongoDbCallExecutor.createCollectionIndex(anyString(), any(JsonObject.class), any(IndexOptions.class),
                anyInt()))
                        .then(invocation -> {
                            final String collection = invocation.getArgument(0);
                            final JsonObject key = invocation.getArgument(1);
                            final IndexOptions options = invocation.getArgument(2);
                            final Promise<Void> indexCreated = Promise.promise();
                            mongoClient.createIndexWithOptions(collection, key, options, indexCreated);
                            return indexCreated;
                        });
    }

    @Override
    public RegistrationService getRegistrationService() {
        return svc;
    }

    @Override
    public DeviceManagementService getDeviceManagementService() {
        return svc;
    }

}
