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

import static org.eclipse.hono.util.Constants.DEFAULT_TENANT;

import java.net.InetAddress;
import java.util.stream.IntStream;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.authorization.impl.InMemoryAuthorizationService;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.TelemetrySender;
import org.eclipse.hono.registration.impl.InMemoryRegistrationAdapter;
import org.eclipse.hono.telemetry.impl.MessageDiscardingTelemetryAdapter;
import org.eclipse.hono.telemetry.impl.TelemetryEndpoint;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonHelper;

/**
 * Stand alone integration tests for Hono's Telemetry API.
 *
 */
@RunWith(VertxUnitRunner.class)
public class StandaloneTelemetryApiTest {

    private static final String KEY_CONTEXT = "Context";
    private static final Logger LOG          = LoggerFactory.getLogger(StandaloneTelemetryApiTest.class);
    private static final String BIND_ADDRESS = InetAddress.getLoopbackAddress().getHostAddress();
    private static final String DEVICE = "device";
    private static final String DEVICE_1 = DEVICE + "1";

    private static Vertx                       vertx = Vertx.vertx();
    private static HonoServer                  server;
    private static InMemoryRegistrationAdapter registrationAdapter;
    private static MessageDiscardingTelemetryAdapter telemetryAdapter;
    private static HonoClient                  client;
    private static TelemetrySender             telemetrySender;

    private static Context getContext(final TestContext ctx) {
        return (Context) ctx.get(KEY_CONTEXT);
    }

    @BeforeClass
    public static void prepareHonoServer(final TestContext ctx) throws Exception {

        server = new HonoServer(BIND_ADDRESS, 0, false);
        server.addEndpoint(new TelemetryEndpoint(vertx, false));
        registrationAdapter = new InMemoryRegistrationAdapter();
        telemetryAdapter = new MessageDiscardingTelemetryAdapter();

        Future<HonoClient> setupTracker = Future.future();
        setupTracker.setHandler(ctx.asyncAssertSuccess(r -> {
            ctx.put(KEY_CONTEXT, vertx.getOrCreateContext());
        }));

        Future<String> registrationTracker = Future.future();
        Future<String> authTracker = Future.future();
        Future<String> telemetryTracker = Future.future();

        vertx.deployVerticle(registrationAdapter, registrationTracker.completer());
        vertx.deployVerticle(InMemoryAuthorizationService.class.getName(), authTracker.completer());
        vertx.deployVerticle(telemetryAdapter, telemetryTracker.completer());

        CompositeFuture.all(registrationTracker, authTracker, telemetryTracker)
        .compose(r -> {
            Future<String> serverTracker = Future.future();
            vertx.deployVerticle(server, serverTracker.completer());
            return serverTracker;
        }).compose(s -> {
            client = HonoClient.newInstance(vertx, server.getBindAddress(), server.getPort());
            client.connect(new ProtonClientOptions(), setupTracker.completer());
        }, setupTracker);
    }

    @Before
    public void createSender(final TestContext ctx) {

        final Async senderCreation = ctx.async();
        getContext(ctx).runOnContext(go -> {
            client.createTelemetrySender(DEFAULT_TENANT, r -> {
                if (r.succeeded()) {
                    telemetrySender = r.result();
                    senderCreation.complete();
                }
            });
        });
    }

    @After
    public void clearRegistry(final TestContext ctx) throws InterruptedException {

        final Async senderShutdown = ctx.async();
        registrationAdapter.clear();
        getContext(ctx).runOnContext(go -> {
            telemetrySender.close(r -> {
                if (r.succeeded()) {
                    senderShutdown.complete();
                }
            });
        });
    }

    @AfterClass
    public static void shutdown(final TestContext ctx) {

        final Async clientShutdown = ctx.async();
        getContext(ctx).runOnContext(go -> {
            client.shutdown(r -> {
                if (r.succeeded()) {
                    clientShutdown.complete();
                }
            });
        });
    }

    @Test(timeout = 10000l)
    public void testTelemetryUploadSucceedsForRegisteredDevice(final TestContext ctx) throws Exception {

        LOG.debug("starting telemetry upload test");
        int count = 30;
        final Async messagesReceived = ctx.async(count);
        registrationAdapter.addDevice(DEFAULT_TENANT, DEVICE_1);
        telemetryAdapter.setMessageConsumer(msg -> {
            messagesReceived.countDown();
            LOG.debug("received message [id: {}]", msg.getMessageId());
        });

        IntStream.range(0, count).forEach(i -> {
            getContext(ctx).runOnContext(go -> {
                telemetrySender.send(DEVICE_1, "payload" + i, "text/plain");
            });
        });
    }

    @Test(timeout = 1000l)
    public void testLinkGetsClosedWhenUploadingDataForUnknownDevice(final TestContext ctx) throws Exception {

        registrationAdapter.addDevice(DEFAULT_TENANT, DEVICE_1);
        telemetrySender.setErrorHandler(ctx.asyncAssertFailure(s -> {
            LOG.debug(s.getMessage());
        }));
        getContext(ctx).runOnContext(go -> {
            telemetrySender.send("UNKNOWN", "payload", "text/plain");
        });
    }

    @Test(timeout = 1000l)
    public void testLinkGetsClosedWhenUploadingMalformedTelemetryDataMessage(final TestContext ctx) throws Exception {

        registrationAdapter.addDevice(DEFAULT_TENANT, DEVICE_1);
        final Message msg = ProtonHelper.message("malformed");
        msg.setMessageId("malformed-message");

        telemetrySender.setErrorHandler(ctx.asyncAssertFailure(s -> {
            LOG.debug(s.getMessage());
        }));

        getContext(ctx).runOnContext(go -> {
            telemetrySender.send(msg);
        });
    }

}
