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
import org.eclipse.hono.authentication.impl.AcceptAllPlainAuthenticationService;
import org.eclipse.hono.authorization.impl.InMemoryAuthorizationService;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.HonoClient.HonoClientBuilder;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.registration.impl.FileBasedRegistrationService;
import org.eclipse.hono.telemetry.impl.MessageDiscardingTelemetryDownstreamAdapter;
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

    private static final Logger                LOG = LoggerFactory.getLogger(StandaloneTelemetryApiTest.class);
    private static final String                BIND_ADDRESS = InetAddress.getLoopbackAddress().getHostAddress();
    private static final String                DEVICE_PREFIX = "device";
    private static final String                DEVICE_1 = DEVICE_PREFIX + "1";
    private static final String                USER = "hono-client";
    private static final String                PWD = "secret";

    private static Vertx                       vertx = Vertx.vertx();
    private static HonoServer                  server;
    private static FileBasedRegistrationService registrationAdapter;
    private static MessageDiscardingTelemetryDownstreamAdapter telemetryAdapter;
    private static HonoClient                  client;
    private static MessageSender               telemetrySender;

    @BeforeClass
    public static void prepareHonoServer(final TestContext ctx) throws Exception {

        telemetryAdapter = new MessageDiscardingTelemetryDownstreamAdapter(vertx);
        server = new HonoServer().setBindAddress(BIND_ADDRESS).setPort(0).setSaslAuthenticatorFactory(new HonoSaslAuthenticatorFactory(vertx));
        TelemetryEndpoint telemetryEndpoint = new TelemetryEndpoint(vertx);
        telemetryEndpoint.setTelemetryAdapter(telemetryAdapter);
        server.addEndpoint(telemetryEndpoint);
        registrationAdapter = new FileBasedRegistrationService();

        final Future<HonoClient> setupTracker = Future.future();
        setupTracker.setHandler(ctx.asyncAssertSuccess());

        Future<String> registrationTracker = Future.future();
        Future<String> authenticationTracker = Future.future();
        Future<String> authTracker = Future.future();

        vertx.deployVerticle(registrationAdapter, registrationTracker.completer());
        vertx.deployVerticle(InMemoryAuthorizationService.class.getName(), authTracker.completer());
        vertx.deployVerticle(AcceptAllPlainAuthenticationService.class.getName(), authenticationTracker.completer());

        CompositeFuture.all(registrationTracker, authTracker)
        .compose(r -> {
            Future<String> serverTracker = Future.future();
            vertx.deployVerticle(server, serverTracker.completer());
            return serverTracker;
        }).compose(s -> {
            client = HonoClientBuilder.newClient()
                    .vertx(vertx)
                    .name("test")
                    .host(server.getBindAddress())
                    .port(server.getPort())
                    .user(USER)
                    .password(PWD)
                    .build();
            client.connect(new ProtonClientOptions(), setupTracker.completer());
        }, setupTracker);
    }

    @Before
    public void createSender(final TestContext ctx) {

        registrationAdapter.addDevice(DEFAULT_TENANT, DEVICE_1, null);

        client.getOrCreateTelemetrySender(DEFAULT_TENANT, ctx.asyncAssertSuccess(r -> {
            telemetrySender = r;
        }));
    }

    @After
    public void clearRegistry(final TestContext ctx) throws InterruptedException {

        registrationAdapter.clear();
        telemetrySender.close(ctx.asyncAssertSuccess());
    }

    @AfterClass
    public static void shutdown(final TestContext ctx) {

        if (client != null) {
            client.shutdown(ctx.asyncAssertSuccess());
        }
    }

    @Test(timeout = 10000l)
    public void testTelemetryUploadSucceedsForRegisteredDevice(final TestContext ctx) throws Exception {

        LOG.debug("starting telemetry upload test");
        int count = 30;
        final Async messagesReceived = ctx.async(count);
        telemetryAdapter.setMessageConsumer(msg -> {
            messagesReceived.countDown();
            LOG.debug("received message [id: {}]", msg.getMessageId());
        });

        IntStream.range(0, count).forEach(i -> {
            Async waitForCredit = ctx.async();
            LOG.trace("sending message {}", i);
            telemetrySender.send(DEVICE_1, "payload" + i, "text/plain; charset=utf-8", done -> waitForCredit.complete());
            LOG.trace("sender's send queue full: {}", telemetrySender.sendQueueFull());
            waitForCredit.await();
        });
    }

    @Test(timeout = 1000l)
    public void testLinkGetsClosedWhenUploadingDataForUnknownDevice(final TestContext ctx) throws Exception {

        telemetrySender.setErrorHandler(ctx.asyncAssertFailure(s -> {
            LOG.debug(s.getMessage());
        }));
        telemetrySender.send("UNKNOWN", "payload", "text/plain", capacityAvailable -> {});
    }

    @Test()
    public void testLinkGetsClosedWhenUploadingMalformedTelemetryDataMessage(final TestContext ctx) throws Exception {

        final Message msg = ProtonHelper.message("malformed");
        msg.setMessageId("malformed-message");

        telemetrySender.setErrorHandler(ctx.asyncAssertFailure(s -> {
            LOG.debug(s.getMessage());
        }));
        Async sent = ctx.async();
        telemetrySender.send(msg, capacityAvailable -> sent.complete());
        sent.awaitSuccess(200);
    }

    @Test(timeout = 2000l)
    public void testLinkGetsClosedWhenDeviceUploadsDataOriginatingFromOtherDevice(final TestContext ctx) throws Exception {

        Async closed = ctx.async();
        telemetrySender.close(ctx.asyncAssertSuccess(done -> closed.complete()));
        closed.await(1000);
        client.getOrCreateTelemetrySender(DEFAULT_TENANT, "device-0", ctx.asyncAssertSuccess(sender -> {
            telemetrySender = sender;
            telemetrySender.setErrorHandler(ctx.asyncAssertFailure(s -> {
                LOG.debug(s.getMessage());
            }));
            telemetrySender.send("device-1", "from other device", "text/plain");
        }));
    }
}
