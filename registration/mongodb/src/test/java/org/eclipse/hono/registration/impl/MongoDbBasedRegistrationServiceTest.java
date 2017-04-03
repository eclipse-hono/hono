/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.registration.impl;

import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

import java.io.IOException;

import org.eclipse.hono.util.RegistrationResult;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests {@link MongoDbBasedRegistrationService}.
 */
@RunWith(VertxUnitRunner.class)
public class MongoDbBasedRegistrationServiceTest {

    private static final int TIMEOUT = 5000; // milliseconds

    private static final String TENANT = "tenant";
    private static final String DEVICE = "device";
    
    private static Vertx vertx;
    private static MongoDbBasedRegistrationService registrationAdapter;

    private static final MongodStarter starter = MongodStarter.getDefaultInstance();
    private static MongodExecutable mongodExe;
    private static MongodProcess mongodProcess;
    
    @BeforeClass
    public static void setUp(TestContext context) throws Exception {
        final String mongoDbHost = "localhost";
        final int mongoDbPort = Network.getFreeServerPort();
        startEmbeddedMongoDb(mongoDbHost, mongoDbPort);
        MongoDbConfigProperties mongoDbConfigProperties = new MongoDbConfigProperties();
        mongoDbConfigProperties.setHost(mongoDbHost).setPort(mongoDbPort).setDbName("deviceRegistry");
        registrationAdapter = new MongoDbBasedRegistrationService(mongoDbConfigProperties);
        
        vertx = Vertx.vertx();
        vertx.deployVerticle(registrationAdapter, context.asyncAssertSuccess());
    }

    private static void startEmbeddedMongoDb(String mongoDbHost, int mongoDbPort) throws IOException {
        mongodExe = starter.prepare(new MongodConfigBuilder()
            .version(Version.Main.PRODUCTION)
            .net(new Net(mongoDbHost, mongoDbPort, Network.localhostIsIPv6()))
            .build());
        mongodProcess = mongodExe.start();
    }

    @AfterClass
    public static void tearDown(TestContext context) throws Exception {
        vertx.close(context.asyncAssertSuccess(r -> {
            mongodProcess.stop();
            mongodExe.stop();
        }));
    }

    @After
    public void clearRegistry(TestContext context) throws InterruptedException {
        registrationAdapter.clear(context.asyncAssertSuccess());
    }

    @Test(timeout = TIMEOUT)
    public void testGetUnknownDeviceReturnsNotFound(TestContext context) {
        final Async async = context.async();
        registrationAdapter.getDevice(TENANT, DEVICE, res -> {
            context.assertTrue(res.succeeded());
            RegistrationResult result = res.result();
            context.assertEquals(HTTP_NOT_FOUND, result.getStatus());
            async.complete();
        });
    }

    @Test(timeout = TIMEOUT)
    public void testRemoveDevice(TestContext context) {
        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        // add device
        Future<RegistrationResult> addDeviceTracker = Future.future();
        JsonObject data = new JsonObject();
        registrationAdapter.addDevice(TENANT, DEVICE, data, addDeviceTracker.completer());
        addDeviceTracker.compose(res -> {
            // invocation should return 'HTTP_CREATED'
            context.assertEquals(HTTP_CREATED, res.getStatus());

            // remove device
            Future<RegistrationResult> removeDeviceTracker = Future.future();
            registrationAdapter.removeDevice(TENANT, DEVICE, removeDeviceTracker.completer());
            return removeDeviceTracker;
        }).compose(res -> {
            // 'removeDevice' should return 'HTTP_OK'
            context.assertEquals(HTTP_OK, res.getStatus());
            context.assertEquals(data, res.getPayload());
            done.complete();
        }, done);
    }
    
    @Test(timeout = TIMEOUT)
    public void testRemoveUnknownDeviceReturnsNotFound(TestContext context) {
        final Async async = context.async();
        registrationAdapter.removeDevice(TENANT, DEVICE, res -> {
            context.assertTrue(res.succeeded());
            RegistrationResult result = res.result();
            context.assertEquals(HTTP_NOT_FOUND, result.getStatus());
            async.complete();
        });
    }

    @Test(timeout = TIMEOUT)
    public void testDuplicateRegistrationFails(TestContext context) {
        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        // add device
        Future<RegistrationResult> addDeviceTracker = Future.future();
        JsonObject data = new JsonObject();
        registrationAdapter.addDevice(TENANT, DEVICE, data, addDeviceTracker.completer());
        addDeviceTracker.compose(res -> {
            // first invocation should return 'HTTP_CREATED'
            context.assertEquals(HTTP_CREATED, res.getStatus());

            // add device AGAIN
            Future<RegistrationResult> addDeviceAgainTracker = Future.future();
            registrationAdapter.addDevice(TENANT, DEVICE, data, addDeviceAgainTracker.completer());
            return addDeviceAgainTracker;
        }).compose(res -> {
            // 2nd invocation should return 'HTTP_CONFLICT'
            context.assertEquals(HTTP_CONFLICT, res.getStatus());
            done.complete();
        }, done);
    }

    @Test(timeout = TIMEOUT)
    public void testGetSucceedsForAddedDevice(TestContext context) {
        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        // add device
        Future<RegistrationResult> addDeviceTracker = Future.future();
        JsonObject data = new JsonObject();
        registrationAdapter.addDevice(TENANT, DEVICE, data, addDeviceTracker.completer());
        addDeviceTracker.compose(res -> {
            // invocation should return 'HTTP_CREATED'
            context.assertEquals(HTTP_CREATED, res.getStatus());

            // get device
            Future<RegistrationResult> getDeviceTracker = Future.future();
            registrationAdapter.getDevice(TENANT, DEVICE, getDeviceTracker.completer());
            return getDeviceTracker;
        }).compose(res -> {
            // 'getDevice' should return 'HTTP_OK'
            context.assertEquals(HTTP_OK, res.getStatus());
            context.assertEquals(data, res.getPayload());
            done.complete();
        }, done);
    }

    @Test(timeout = TIMEOUT)
    public void testGetFailsForRemovedDevice(TestContext context) {
        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        // add device
        Future<RegistrationResult> addDeviceTracker = Future.future();
        JsonObject data = new JsonObject();
        registrationAdapter.addDevice(TENANT, DEVICE, data, addDeviceTracker.completer());
        addDeviceTracker.compose(res -> {
            // invocation should return 'HTTP_CREATED'
            context.assertEquals(HTTP_CREATED, res.getStatus());

            // remove device
            Future<RegistrationResult> removeDeviceTracker = Future.future();
            registrationAdapter.removeDevice(TENANT, DEVICE, removeDeviceTracker.completer());
            return removeDeviceTracker;
        }).compose(res -> {
            // 'removeDevice' invocation should return 'HTTP_OK'
            context.assertEquals(HTTP_OK, res.getStatus());
            context.assertEquals(data, res.getPayload());

            // remove device
            Future<RegistrationResult> getDeviceTracker = Future.future();
            registrationAdapter.getDevice(TENANT, DEVICE, getDeviceTracker.completer());
            return getDeviceTracker;
        }).compose(res -> {
            // 'getDevice' should return 'HTTP_NOT_FOUND'
            context.assertEquals(HTTP_NOT_FOUND, res.getStatus());
            done.complete();
        }, done);
    }

    @Test(timeout = TIMEOUT)
    public void testUpdateDeviceSucceeds(TestContext context) {
        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        // add device
        Future<RegistrationResult> addDeviceTracker = Future.future();
        JsonObject data = new JsonObject()
            .put("someKey", "someValue");
        JsonObject updatedData = new JsonObject()
            .put("someOtherKey", "someOtherValue");
        registrationAdapter.addDevice(TENANT, DEVICE, data, addDeviceTracker.completer());
        addDeviceTracker.compose(res -> {
            // invocation should return 'HTTP_CREATED'
            context.assertEquals(HTTP_CREATED, res.getStatus());

            // update device
            Future<RegistrationResult> updateDeviceTracker = Future.future();
            registrationAdapter.updateDevice(TENANT, DEVICE, updatedData, updateDeviceTracker.completer());
            return updateDeviceTracker;
        }).compose(res -> {
            // 'updateDevice' should return 'HTTP_OK'
            context.assertEquals(HTTP_OK, res.getStatus());
            // initial data should be returned
            context.assertEquals(data, res.getPayload());
            done.complete();
        }, done);
    }

    @Test(timeout = TIMEOUT)
    public void testUpdateUnknownDeviceReturnsNotFound(TestContext context) {
        final Async async = context.async();
        JsonObject data = new JsonObject();
        registrationAdapter.updateDevice(TENANT, DEVICE, data, res -> {
            context.assertTrue(res.succeeded());
            RegistrationResult result = res.result();
            context.assertEquals(HTTP_NOT_FOUND, result.getStatus());
            async.complete();
        });
    }

    @Test(timeout = TIMEOUT)
    public void testFindDeviceSucceeds(TestContext context) {
        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        final String myKey = "myKey";
        final String myValue = "myValue";
        JsonObject data = new JsonObject()
            .put(myKey, myValue)
            .put("other", "x");
        JsonObject data2 = new JsonObject()
            .put(myKey, "myValue2")
            .put("other", "x");
        
        
        // add device
        Future<RegistrationResult> addDeviceTracker = Future.future();
        registrationAdapter.addDevice(TENANT, DEVICE, data, addDeviceTracker.completer());
        addDeviceTracker.compose(res -> {
            // addDevice should return 'HTTP_CREATED'
            context.assertEquals(HTTP_CREATED, res.getStatus());

            // add another device
            Future<RegistrationResult> add2ndDeviceTracker = Future.future();
            registrationAdapter.addDevice(TENANT, DEVICE + "2", data2, add2ndDeviceTracker.completer());
            return add2ndDeviceTracker;
        }).compose(res -> {
            // addDevice should return 'HTTP_CREATED'
            context.assertEquals(HTTP_CREATED, res.getStatus());

            // find device
            Future<RegistrationResult> getDeviceTracker = Future.future();
            registrationAdapter.findDevice(TENANT, myKey, myValue, getDeviceTracker.completer());
            return getDeviceTracker;
        }).compose(res -> {
            // 'findDevice' should return 'HTTP_OK'
            context.assertEquals(HTTP_OK, res.getStatus());
            context.assertEquals(data, res.getPayload());
            done.complete();
        }, done);
    }

    @Test(timeout = TIMEOUT)
    public void testFindDeviceSucceedsAlsoWithTwoMatches(TestContext context) {
        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        final String myKey = "myKey";
        final String myValue = "myValue";
        JsonObject data = new JsonObject()
            .put(myKey, myValue)
            .put("other", "x");

        // add device
        Future<RegistrationResult> addDeviceTracker = Future.future();
        registrationAdapter.addDevice(TENANT, DEVICE, data, addDeviceTracker.completer());
        addDeviceTracker.compose(res -> {
            // addDevice should return 'HTTP_CREATED'
            context.assertEquals(HTTP_CREATED, res.getStatus());

            // add another device
            Future<RegistrationResult> add2ndDeviceTracker = Future.future();
            registrationAdapter.addDevice(TENANT, DEVICE + "2", data, add2ndDeviceTracker.completer());
            return add2ndDeviceTracker;
        }).compose(res -> {
            // addDevice should return 'HTTP_CREATED'
            context.assertEquals(HTTP_CREATED, res.getStatus());

            // find device
            Future<RegistrationResult> getDeviceTracker = Future.future();
            registrationAdapter.findDevice(TENANT, myKey, myValue, getDeviceTracker.completer());
            return getDeviceTracker;
        }).compose(res -> {
            // 'findDevice' should return 'HTTP_OK'
            context.assertEquals(HTTP_OK, res.getStatus());
            context.assertEquals(data, res.getPayload());
            done.complete();
        }, done);
    }

    @Test(timeout = TIMEOUT)
    public void testFindDeviceWithNoMatchReturnsNotFound(TestContext context) {
        final Async async = context.async();
        registrationAdapter.findDevice(TENANT, "key", "value", res -> {
            context.assertTrue(res.succeeded());
            RegistrationResult result = res.result();
            context.assertEquals(HTTP_NOT_FOUND, result.getStatus());
            async.complete();
        });
    }
}