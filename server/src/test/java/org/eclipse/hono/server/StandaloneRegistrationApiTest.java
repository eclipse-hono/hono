/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.server;

import static java.net.HttpURLConnection.*;
import static org.eclipse.hono.util.Constants.DEFAULT_TENANT;

import java.util.stream.IntStream;

import org.eclipse.hono.authentication.impl.AcceptAllAuthenticationService;
import org.eclipse.hono.authorization.impl.InMemoryAuthorizationService;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.connection.ConnectionFactoryImpl.ConnectionFactoryBuilder;
import org.eclipse.hono.service.auth.HonoSaslAuthenticatorFactory;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.service.registration.RegistrationEndpoint;
import org.eclipse.hono.service.registration.impl.FileBasedRegistrationService;
import org.eclipse.hono.util.AggregatingInvocationResultHandler;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;

/**
 * Tests validating Hono's Registration API using a stand alone server.
 */
@RunWith(VertxUnitRunner.class)
public class StandaloneRegistrationApiTest {

    private static final int                   NO_OF_DEVICES = 20;
    private static final int                   TIMEOUT = 5000; // milliseconds
    private static final String                DEVICE_PREFIX = "device";
    private static final String                DEVICE_1 = DEVICE_PREFIX + "1";
    private static final String                USER = "hono-client";
    private static final String                PWD = "secret";
    private static final String                SECRET = "hiusfdazuisdfibgbgabvbzusdatgfASFJSDAFJSDAOF";

    private static Vertx                       vertx = Vertx.vertx();
    private static HonoServer                  server;
    private static FileBasedRegistrationService registrationAdapter;
    private static HonoClient                  client;
    private static RegistrationClient          registrationClient;

    @BeforeClass
    public static void prepareHonoServer(final TestContext ctx) throws Exception {

        server = new HonoServer();
        server.setSaslAuthenticatorFactory(new HonoSaslAuthenticatorFactory(vertx));
        HonoServerConfigProperties configProperties = new HonoServerConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setInsecurePort(0);
        server.setConfig(configProperties);
        server.addEndpoint(new RegistrationEndpoint(vertx));
        registrationAdapter = new FileBasedRegistrationService();
        registrationAdapter.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSharedSecret(SECRET, 10));

        Future<RegistrationClient> setupTracker = Future.future();
        setupTracker.setHandler(ctx.asyncAssertSuccess(r -> {
            registrationClient = r;
        }));

        Future<String> registrationTracker = Future.future();
        Future<String> authenticationTracker = Future.future();
        Future<String> authTracker = Future.future();

        vertx.deployVerticle(registrationAdapter, registrationTracker.completer());
        vertx.deployVerticle(AcceptAllAuthenticationService.class.getName(), authenticationTracker.completer());
        vertx.deployVerticle(InMemoryAuthorizationService.class.getName(), authTracker.completer());

        CompositeFuture.all(registrationTracker, authTracker)
        .compose(r -> {
            Future<String> serverTracker = Future.future();
            vertx.deployVerticle(server, serverTracker.completer());
            return serverTracker;
        }).compose(s -> {
            client = new HonoClientImpl(vertx, ConnectionFactoryBuilder.newBuilder()
                    .vertx(vertx)
                    .name("test")
                    .host(server.getInsecurePortBindAddress())
                    .port(server.getInsecurePort())
                    .user(USER)
                    .password(PWD)
                    .build());

            Future<HonoClient> clientTracker = Future.future();
            client.connect(new ProtonClientOptions(), clientTracker.completer());
            return clientTracker;
        }).compose(c -> {
            c.createRegistrationClient(DEFAULT_TENANT, setupTracker.completer());
        }, setupTracker);
    }

    @After
    public void clearRegistry() throws InterruptedException {

        registrationAdapter.clear();
    }

    @AfterClass
    public static void shutdown(final TestContext ctx) {

        Future<Void> done = Future.future();
        done.setHandler(ctx.asyncAssertSuccess());

        if (client != null) {
            Future<Void> closeTracker = Future.future();
            if (registrationClient != null) {
                registrationClient.close(closeTracker.completer());
            } else {
                closeTracker.complete();
            }
            closeTracker.compose(c -> {
                client.shutdown(done.completer());
            }, done);
        }
    }

    @Test(timeout = TIMEOUT)
    public void testGetDeviceReturnsRegisteredDevice(final TestContext ctx) {

        Future<RegistrationResult> getTracker = Future.future();
        getTracker.setHandler(ctx.asyncAssertSuccess(getResult -> {
            ctx.assertEquals(HTTP_OK, getResult.getStatus());
        }));

        Future<RegistrationResult> regTracker = Future.future();
        registrationClient.register(DEVICE_1, null, regTracker.completer());
        regTracker.compose(s -> {
            ctx.assertEquals(HTTP_CREATED, s.getStatus());
            registrationClient.get(DEVICE_1, getTracker.completer());
        }, getTracker);
    }

    @Test(timeout = TIMEOUT)
    public void testGetDeviceFailsForNonExistingDevice(final TestContext ctx) {

        final Async ok = ctx.async();

        registrationClient.get(DEVICE_1, s -> {
            if (s.succeeded() && s.result().getStatus() == HTTP_NOT_FOUND) {
                ok.complete();
            }
        });
    }

    @Test(timeout = TIMEOUT)
    public void testDeregisterDeviceFailsForNonExistingDevice(final TestContext ctx) {

        final Async ok = ctx.async();

        registrationClient.deregister(DEVICE_1, s -> {
            if (s.succeeded() && s.result().getStatus() == HTTP_NOT_FOUND) {
                ok.complete();
            }
        });
    }

    @Test(timeout = TIMEOUT)
    public void testFindDeviceSucceedsForExistingKey(final TestContext ctx) {
        registrationAdapter.addDevice(DEFAULT_TENANT, DEVICE_1, new JsonObject().put("ep", "lwm2m"));
        final Async ok = ctx.async();
        registrationClient.find("ep", "lwm2m", s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_OK);
            JsonObject payload = s.result().getPayload();
            ctx.assertNotNull(payload);
            ctx.assertEquals(DEVICE_1, payload.getString(RegistrationConstants.FIELD_HONO_ID));
            JsonObject data = payload.getJsonObject(RegistrationConstants.FIELD_DATA);
            ctx.assertEquals("lwm2m", data.getString("ep"));
            ok.complete();
        });
    }

    @Test(timeout = TIMEOUT)
    public void testFindDeviceFailsForNonMatchingValue(final TestContext ctx) {
        registrationAdapter.addDevice(DEFAULT_TENANT, DEVICE_1, new JsonObject().put("ep", "lwm2m"));
        final Async ok = ctx.async();
        registrationClient.find("ep", "non-existing", s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_NOT_FOUND);
            ok.complete();
        });
    }

    @Test(timeout = TIMEOUT)
    public void testRegisterDevices(final TestContext ctx) {

        Future<RegistrationResult> done = Future.future();
        done.setHandler(ctx.asyncAssertSuccess(s -> {
            ctx.assertEquals(HTTP_NOT_FOUND, s.getStatus());
        }));

        // Step 1: register some devices
        Future<Void> registrationTracker = Future.future();
        registerDevices(ctx, registrationTracker.completer());
        registrationTracker.compose(r -> {
            // Step 2: assert that "device1" has been registered
            Future<RegistrationResult> getTracker = Future.future();
            registrationClient.get(DEVICE_1, getTracker.completer());
            return getTracker;
        }).compose(getResult -> {
            ctx.assertEquals(HTTP_OK, getResult.getStatus());
            // Step 3: deregister all devices
            Future<Void> deregTracker = Future.future();
            deregisterDevices(ctx, deregTracker.completer());
            return deregTracker;
        }).compose(r -> {
            // Step 4: assert that "device1" has been deregistered
            registrationClient.get(DEVICE_1, done.completer());
        }, done);
    }

    private void registerDevices(final TestContext ctx, final Handler<AsyncResult<Void>> handler) {

        final Handler<Boolean> resultAggregator = new AggregatingInvocationResultHandler(NO_OF_DEVICES, handler);

        //register devices
        IntStream.range(0, NO_OF_DEVICES).forEach(i -> {
            String deviceId = DEVICE_PREFIX + i;
            registrationClient.register(deviceId, null, s -> {
                resultAggregator.handle(s.succeeded() && HTTP_CREATED == s.result().getStatus());
            });
        });

    }

    private void deregisterDevices(final TestContext ctx, final Handler<AsyncResult<Void>> handler) {

        final Handler<Boolean> resultAggregator = new AggregatingInvocationResultHandler(NO_OF_DEVICES, handler);

        //deregister devices
        IntStream.range(0, NO_OF_DEVICES).forEach(i -> {
            String deviceId = DEVICE_PREFIX + i;
            registrationClient.deregister(deviceId, s ->
                    resultAggregator.handle(s.succeeded() && HTTP_OK == s.result().getStatus()));
        });
    }
}
