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

import static org.eclipse.hono.util.Constants.DEFAULT_TENANT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.TestSupport;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.connection.ConnectionFactoryImpl.ConnectionFactoryBuilder;
import org.eclipse.hono.service.auth.HonoSaslAuthenticatorFactory;
import org.eclipse.hono.service.registration.RegistrationAssertionHelper;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.telemetry.impl.MessageDiscardingTelemetryDownstreamAdapter;
import org.eclipse.hono.telemetry.impl.TelemetryEndpoint;
import org.eclipse.hono.util.MessageHelper;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final String                DEVICE_PREFIX = "device";
    private static final String                DEVICE_1 = DEVICE_PREFIX + "1";
    private static final String                USER = "hono-client";
    private static final String                PWD = "secret";
    private static final String                SECRET = "dajAIOFDHUIFHFSDAJKGFKSDF,SBDFAZUSDJBFFNCLDNC";
    private static final int                   TIMEOUT = 2000; // milliseconds

    private static Vertx                       vertx = Vertx.vertx();
    private static HonoServer                  server;
    private static MessageDiscardingTelemetryDownstreamAdapter telemetryAdapter;
    private static HonoClient                  client;
    private static MessageSender               telemetrySender;
    private static RegistrationAssertionHelper assertionHelper;

    @BeforeClass
    public static void prepareHonoServer(final TestContext ctx) throws Exception {

        assertionHelper = RegistrationAssertionHelperImpl.forSharedSecret(SECRET, 10);
        telemetryAdapter = new MessageDiscardingTelemetryDownstreamAdapter(vertx);
        server = new HonoServer();
        server.setSaslAuthenticatorFactory(new HonoSaslAuthenticatorFactory(vertx, TestSupport.createAuthenticationService(createUser())));
        HonoServerConfigProperties configProperties = new HonoServerConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setInsecurePort(0);
        server.setConfig(configProperties);
        TelemetryEndpoint telemetryEndpoint = new TelemetryEndpoint(vertx);
        telemetryEndpoint.setTelemetryAdapter(telemetryAdapter);
        telemetryEndpoint.setRegistrationAssertionValidator(assertionHelper);
        server.addEndpoint(telemetryEndpoint);

        final Future<HonoClient> setupTracker = Future.future();
        setupTracker.setHandler(ctx.asyncAssertSuccess());

        Future<String> serverTracker = Future.future();

        vertx.deployVerticle(server, serverTracker.completer());

        serverTracker.compose(s -> {
            client = new HonoClientImpl(vertx, ConnectionFactoryBuilder.newBuilder()
                    .vertx(vertx)
                    .name("test")
                    .host(server.getInsecurePortBindAddress())
                    .port(server.getInsecurePort())
                    .user(USER)
                    .password(PWD)
                    .build());
            client.connect(new ProtonClientOptions(), setupTracker.completer());
        }, setupTracker);

    }

    /**
     * Creates a Hono user containing all authorities required for running this test class.
     * 
     * @return The user.
     */
    private static HonoUser createUser() {

        AuthoritiesImpl authorities = new AuthoritiesImpl()
                .addResource(TelemetryConstants.TELEMETRY_ENDPOINT, "*", new Activity[]{ Activity.READ, Activity.WRITE });
        HonoUser user = mock(HonoUser.class);
        when(user.getName()).thenReturn("test-client");
        when(user.getAuthorities()).thenReturn(authorities);
        return user;
    }

    @Before
    public void createSender(final TestContext ctx) {

        telemetryAdapter.setMessageConsumer(msg -> {});
    }

    @After
    public void clearRegistry(final TestContext ctx) throws InterruptedException {

        if (telemetrySender != null && telemetrySender.isOpen()) {
            Async done = ctx.async();
            telemetrySender.close(closeAttempt -> {
                ctx.assertTrue(closeAttempt.succeeded());
                done.complete();
            });
            done.await(1000L);
        }
    }

    @AfterClass
    public static void shutdown(final TestContext ctx) {

        if (client != null) {
            client.shutdown(ctx.asyncAssertSuccess());
        }
    }

    @Test(timeout = TIMEOUT)
    public void testTelemetryUploadSucceedsForRegisteredDevice(final TestContext ctx) throws Exception {

        LOG.debug("starting telemetry upload test");
        int count = 30;
        final Async messagesReceived = ctx.async(count);
        telemetryAdapter.setMessageConsumer(msg -> {
            messagesReceived.countDown();
            LOG.debug("received message [id: {}]", msg.getMessageId());
        });

        Async sender = ctx.async();
        client.getOrCreateTelemetrySender(DEFAULT_TENANT, creationAttempt -> {
            ctx.assertTrue(creationAttempt.succeeded());
            telemetrySender = creationAttempt.result();
            sender.complete();
        });
        sender.await(1000L);

        String registrationAssertion = assertionHelper.getAssertion(DEFAULT_TENANT, DEVICE_1);
        LOG.debug("got registration assertion for device [{}]: {}", DEVICE_1, registrationAssertion);

        IntStream.range(0, count).forEach(i -> {
            Async waitForCredit = ctx.async();
            LOG.trace("sending message {}", i);
            telemetrySender.send(DEVICE_1, "payload" + i, "text/plain; charset=utf-8", registrationAssertion, done -> waitForCredit.complete());
            LOG.trace("sender's send queue full: {}", telemetrySender.sendQueueFull());
            waitForCredit.await();
        });

    }

    @Test(timeout = TIMEOUT)
    public void testLinkGetsClosedWhenUploadingDataWithNonMatchingRegistrationAssertion(final TestContext ctx) throws Exception {

        String assertion = assertionHelper.getAssertion(DEFAULT_TENANT, "other-device");

        client.getOrCreateTelemetrySender(DEFAULT_TENANT, ctx.asyncAssertSuccess(sender -> {
            sender.setErrorHandler(ctx.asyncAssertFailure(s -> {
                LOG.debug(s.getMessage());
            }));
            sender.send(DEVICE_1, "payload", "text/plain", assertion, capacityAvailable -> {});
        }));

    }

    @Test(timeout = TIMEOUT)
    public void testLinkGetsClosedWhenUploadingDataWithoutRegistrationAssertion(final TestContext ctx) throws Exception {

        Message msg = ProtonHelper.message();
        msg.setBody(new Data(new Binary("payload".getBytes(StandardCharsets.UTF_8))));
        msg.setContentType("text/plain");
        MessageHelper.addDeviceId(msg, DEVICE_1);
        MessageHelper.addTenantId(msg, DEFAULT_TENANT);
        // NO registration assertion included

        client.getOrCreateTelemetrySender(DEFAULT_TENANT, ctx.asyncAssertSuccess(sender -> {
            sender.setErrorHandler(ctx.asyncAssertFailure(s -> {
                LOG.debug(s.getMessage());
            }));
            sender.send(msg, capacityAvailable -> {});
        }));

    }

    @Test(timeout = TIMEOUT)
    public void testLinkGetsClosedWhenUploadingMalformedTelemetryDataMessage(final TestContext ctx) throws Exception {

        final Message msg = ProtonHelper.message("malformed");
        msg.setMessageId("malformed-message");

        client.getOrCreateTelemetrySender(DEFAULT_TENANT, ctx.asyncAssertSuccess(sender -> {
            sender.setErrorHandler(ctx.asyncAssertFailure(error -> {
                LOG.debug(error.getMessage());
            }));
            sender.send(msg, capacityAvailable -> {});
        }));

    }

    @Test(timeout = TIMEOUT)
    public void testLinkGetsClosedWhenDeviceUploadsDataOriginatingFromOtherDevice(final TestContext ctx) throws Exception {

        String registrationAssertion = assertionHelper.getAssertion(DEFAULT_TENANT, "device-1");

        client.getOrCreateTelemetrySender(DEFAULT_TENANT, "device-0", ctx.asyncAssertSuccess(sender -> {
            sender.setErrorHandler(ctx.asyncAssertFailure(s -> {
                LOG.debug(s.getMessage());
            }));
            sender.send("device-1", "from other device", "text/plain", registrationAssertion);
        }));
    }
}
