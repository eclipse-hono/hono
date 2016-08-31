/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
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

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.eclipse.hono.util.Constants.DEFAULT_TENANT;

import java.net.InetAddress;
import java.util.stream.IntStream;

import org.eclipse.hono.authentication.impl.AcceptAllPlainAuthenticationService;
import org.eclipse.hono.authorization.impl.InMemoryAuthorizationService;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.HonoClient.HonoClientBuilder;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.registration.impl.InMemoryRegistrationAdapter;
import org.eclipse.hono.registration.impl.RegistrationEndpoint;
import org.eclipse.hono.util.AggregatingInvocationResultHandler;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;

/**
 * Tests validating Hono's Registration API using a stand alone server.
 */
@RunWith(VertxUnitRunner.class)
public class StandaloneRegistrationApiTest {

    private static final String                KEY_CONTEXT = "Context";
    private static final String                BIND_ADDRESS = InetAddress.getLoopbackAddress().getHostAddress();
    private static final int                   NO_OF_DEVICES = 20;
    private static final int                   TIMEOUT = 5000; // milliseconds
    private static final String                DEVICE_PREFIX = "device";
    private static final String                DEVICE_1 = DEVICE_PREFIX + "1";
    private static final String                USER = "hono-client";
    private static final String                PWD = "secret";

    private static Vertx                       vertx = Vertx.vertx();
    private static HonoServer                  server;
    private static InMemoryRegistrationAdapter registrationAdapter;
    private static HonoClient                  client;
    private static RegistrationClient          registrationClient;

    private static Context getContext(final TestContext ctx) {
        return (Context) ctx.get(KEY_CONTEXT);
    }

    @BeforeClass
    public static void prepareHonoServer(final TestContext ctx) throws Exception {

        server = new HonoServer(BIND_ADDRESS, 0, false);
        server.addEndpoint(new RegistrationEndpoint(vertx, false));
        registrationAdapter = new InMemoryRegistrationAdapter();
        Context context = vertx.getOrCreateContext();
        ctx.put(KEY_CONTEXT, context);

        Future<RegistrationClient> setupTracker = Future.future();
        setupTracker.setHandler(ctx.asyncAssertSuccess(r -> {
            registrationClient = r;
        }));

        Future<String> registrationTracker = Future.future();
        Future<String> authenticationTracker = Future.future();
        Future<String> authTracker = Future.future();

        context.runOnContext(run -> {
            vertx.deployVerticle(registrationAdapter, registrationTracker.completer());
            vertx.deployVerticle(AcceptAllPlainAuthenticationService.class.getName(), authenticationTracker.completer());
            vertx.deployVerticle(InMemoryAuthorizationService.class.getName(), authTracker.completer());

            CompositeFuture.all(registrationTracker, authTracker)
            .compose(r -> {
                Future<String> serverTracker = Future.future();
                vertx.deployVerticle(server, serverTracker.completer());
                return serverTracker;
            }).compose(s -> {
                client = HonoClientBuilder.newClient()
                        .vertx(vertx)
                        .host(server.getBindAddress())
                        .port(server.getPort())
                        .user(USER)
                        .password(PWD)
                        .build();
    
                Future<HonoClient> clientTracker = Future.future();
                context.runOnContext(go -> {
                    client.connect(new ProtonClientOptions(), clientTracker.completer());
                });
                return clientTracker;
            }).compose(c -> {
                c.createRegistrationClient(DEFAULT_TENANT, setupTracker.completer());
            }, setupTracker);
        });
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
            getContext(ctx).runOnContext(go -> {
                Future<Void> closeTracker = Future.future();
                if (registrationClient != null) {
                    registrationClient.close(closeTracker.completer());
                } else {
                    closeTracker.complete();
                }
                closeTracker.compose(c -> {
                    client.shutdown(done.completer());
                }, done);
            });
        }
    }

    @Test(timeout = TIMEOUT)
    public void testGetDeviceReturnsRegisteredDevice(final TestContext ctx) {

        Future<Integer> getTracker = Future.future();
        getTracker.setHandler(ctx.asyncAssertSuccess(getResult -> {
            ctx.assertEquals(HTTP_OK, getResult);
        }));

        getContext(ctx).runOnContext(go -> {
            Future<Integer> regTracker = Future.future();
            registrationClient.register(DEVICE_1, regTracker.completer());
            regTracker.compose(s -> {
                ctx.assertEquals(HTTP_OK, s);
                registrationClient.get(DEVICE_1, getTracker.completer());
            }, getTracker);
        });
    }

    @Test(timeout = TIMEOUT)
    public void testGetDeviceFailsForNonExistingDevice(final TestContext ctx) {

        final Async ok = ctx.async();

        getContext(ctx).runOnContext(go -> {
            registrationClient.get(DEVICE_1, s -> {
                if (s.succeeded() && s.result() == HTTP_NOT_FOUND) {
                    ok.complete();
                }
            });
        });
    }

    @Test(timeout = TIMEOUT)
    public void testDeregisterDeviceFailsForNonExistingDevice(final TestContext ctx) {

        final Async ok = ctx.async();

        getContext(ctx).runOnContext(go -> {
            registrationClient.deregister(DEVICE_1, s -> {
                if (s.succeeded() && s.result() == HTTP_NOT_FOUND) {
                    ok.complete();
                }
            });
        });
    }

    @Test(timeout = 10000l)
    public void testRegisterDevices(final TestContext ctx) {

        Future<Integer> done = Future.future();
        done.setHandler(ctx.asyncAssertSuccess(s -> {
            ctx.assertEquals(HTTP_NOT_FOUND, s);
        }));

        getContext(ctx).runOnContext(go -> {
            // Step 1: register some devices
            Future<Void> registrationTracker = Future.future();
            registerDevices(ctx, registrationTracker.completer());
            registrationTracker.compose(r -> {
                // Step 2: assert that "device1" has been registered
                Future<Integer> getTracker = Future.future();
                registrationClient.get(DEVICE_1, getTracker.completer());
                return getTracker;
            }).compose(getResult -> {
                ctx.assertEquals(HTTP_OK, getResult);
                // Step 3: deregister all devices
                Future<Void> deregTracker = Future.future();
                deregisterDevices(ctx, deregTracker.completer());
                return deregTracker;
            }).compose(r -> {
                // Step 4: assert that "device1" has been deregistered
                registrationClient.get(DEVICE_1, done.completer());
            }, done);
        });
    }

    private void registerDevices(final TestContext ctx, final Handler<AsyncResult<Void>> handler) {

        final Handler<Boolean> resultAggregator = new AggregatingInvocationResultHandler(NO_OF_DEVICES, handler);

        //register devices
        IntStream.range(0, NO_OF_DEVICES).forEach(i -> {
            String deviceId = DEVICE_PREFIX + i;
            getContext(ctx).runOnContext(go -> {
                registrationClient.register(deviceId, s -> {
                    resultAggregator.handle(s.succeeded() && HTTP_OK == s.result());
                });
            });
        });

    }

    private void deregisterDevices(final TestContext ctx, final Handler<AsyncResult<Void>> handler) {

        final Handler<Boolean> resultAggregator = new AggregatingInvocationResultHandler(NO_OF_DEVICES, handler);

        //deregister devices
        IntStream.range(0, NO_OF_DEVICES).forEach(i -> {
            String deviceId = DEVICE_PREFIX + i;
            getContext(ctx).runOnContext(go -> {
                registrationClient.deregister(deviceId, s -> {
                    resultAggregator.handle(s.succeeded() && HTTP_OK == s.result());
                });
            });
        });
    }
}
