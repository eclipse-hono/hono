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
package org.eclipse.hono.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.IntStream;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.TestSupport;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.connection.ConnectionFactoryImpl.ConnectionFactoryBuilder;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.event.impl.EventEndpoint;
import org.eclipse.hono.service.auth.HonoSaslAuthenticatorFactory;
import org.eclipse.hono.service.registration.RegistrationAssertionHelper;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.util.Constants;
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
 * Stand alone integration tests for Hono's Event API.
 *
 */
@RunWith(VertxUnitRunner.class)
public class StandaloneEventApiTest {

    private static final Logger                LOG = LoggerFactory.getLogger(StandaloneEventApiTest.class);
    private static final String                DEVICE_PREFIX = "device";
    private static final String                DEVICE_1 = DEVICE_PREFIX + "1";
    private static final String                USER = "hono-client";
    private static final String                PWD = "secret";
    private static final String                SECRET = "dajAIOFDHUIFHFSDAJKGFKSDF,SBDFAZUSDJBFFNCLDNC";
    private static final int                   TIMEOUT = 2000; // milliseconds

    private static Vertx                       vertx = Vertx.vertx();
    private static HonoMessaging                  server;
    private static MessageDiscardingDownstreamAdapter downstreamAdapter;
    private static HonoClient                  client;
    private static MessageSender               eventSender;
    private static RegistrationAssertionHelper assertionHelper;

    /**
     * Sets up Hono Messaging service.
     * 
     * @param ctx The test context.
     */
    @BeforeClass
    public static void prepareHonoServer(final TestContext ctx) {

        assertionHelper = RegistrationAssertionHelperImpl.forSharedSecret(SECRET, 10);
        downstreamAdapter = new MessageDiscardingDownstreamAdapter(vertx);
        server = new HonoMessaging();
        server.setSaslAuthenticatorFactory(new HonoSaslAuthenticatorFactory(vertx, TestSupport.createAuthenticationService(createUser())));
        HonoMessagingConfigProperties configProperties = new HonoMessagingConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setInsecurePort(0);
        server.setConfig(configProperties);
        EventEndpoint eventEndpoint = new EventEndpoint(vertx);
        eventEndpoint.setMetrics(mock(MessagingMetrics.class));
        eventEndpoint.setEventAdapter(downstreamAdapter);
        eventEndpoint.setRegistrationAssertionValidator(assertionHelper);
        server.addEndpoint(eventEndpoint);

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
                .addResource(EventConstants.EVENT_ENDPOINT, "*", new Activity[]{ Activity.READ, Activity.WRITE });
        HonoUser user = mock(HonoUser.class);
        when(user.getName()).thenReturn("test-client");
        when(user.getAuthorities()).thenReturn(authorities);
        return user;
    }

    @Before
    public void createSender(final TestContext ctx) {

        downstreamAdapter.setMessageConsumer(msg -> {});
    }

    @After
    public void clearRegistry(final TestContext ctx) throws InterruptedException {

        if (eventSender != null && eventSender.isOpen()) {
            Async done = ctx.async();
            eventSender.close(closeAttempt -> {
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

    @Test(timeout = 5000)
    public void testSendEventSucceedsForRegisteredDevice(final TestContext ctx) throws Exception {

        LOG.debug("starting send event test");
        int count = 30;
        final Async messagesReceived = ctx.async(count);
        downstreamAdapter.setMessageConsumer(msg -> {
            messagesReceived.countDown();
            LOG.debug("received message [id: {}]", msg.getMessageId());
        });

        Async sender = ctx.async();
        client.getOrCreateEventSender(Constants.DEFAULT_TENANT, creationAttempt -> {
            ctx.assertTrue(creationAttempt.succeeded());
            eventSender = creationAttempt.result();
            sender.complete();
        });
        sender.await(1000L);

        String registrationAssertion = assertionHelper.getAssertion(Constants.DEFAULT_TENANT, DEVICE_1);
        LOG.debug("got registration assertion for device [{}]: {}", DEVICE_1, registrationAssertion);

        IntStream.range(0, count).forEach(i -> {
            Async waitForCredit = ctx.async();
            LOG.trace("sending message {}", i);
            eventSender.send(DEVICE_1, null, "payload" + i, "text/plain; charset=utf-8", registrationAssertion, replenished -> {
                waitForCredit.complete();
            }, (id, delivery) -> {
                ctx.assertTrue(Accepted.class.isInstance(delivery.getRemoteState()), "message has not been accepted");
            });
            LOG.trace("sender's send queue full: {}", eventSender.sendQueueFull());
            waitForCredit.await(100);
        });

    }

    @Test
    public void testEventWithNonMatchingRegistrationAssertionGetRejected(final TestContext ctx) throws Exception {

        final String assertion = assertionHelper.getAssertion(Constants.DEFAULT_TENANT, "other-device");
        final Async dispositionUpdate = ctx.async();

        client.getOrCreateEventSender(Constants.DEFAULT_TENANT, ctx.asyncAssertSuccess(sender -> {
            sender.send(DEVICE_1, "payload", "text/plain", assertion, (id, delivery) -> {
                ctx.assertTrue(Rejected.class.isInstance(delivery.getRemoteState()));
                ctx.assertTrue(sender.isOpen());
                dispositionUpdate.complete();
            });
        }));

        dispositionUpdate.await(TIMEOUT);
    }

    @Test(timeout = TIMEOUT)
    public void testMalformedEventGetsRejected(final TestContext ctx) throws Exception {

        final Message msg = ProtonHelper.message("malformed");
        msg.setMessageId("malformed-message");

        client.getOrCreateEventSender(Constants.DEFAULT_TENANT, ctx.asyncAssertSuccess(sender -> {
            sender.send(msg, (id, delivery) -> {
                ctx.assertTrue(Rejected.class.isInstance(delivery.getRemoteState()));
                ctx.assertTrue(sender.isOpen());
            });
        }));
    }

    @Test(timeout = TIMEOUT)
    public void testEventOriginatingFromOtherDeviceGetsRejected(final TestContext ctx) throws Exception {

        String registrationAssertion = assertionHelper.getAssertion(Constants.DEFAULT_TENANT, "device-1");

        client.getOrCreateEventSender(Constants.DEFAULT_TENANT, "device-0", ctx.asyncAssertSuccess(sender -> {
            sender.send("device-1", "from other device", "text/plain", registrationAssertion, (id, delivery) -> {
                ctx.assertTrue(Rejected.class.isInstance(delivery.getRemoteState()));
                ctx.assertTrue(sender.isOpen());
            });
        }));
    }
}
