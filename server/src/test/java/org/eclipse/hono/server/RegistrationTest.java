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
import static java.util.Optional.ofNullable;
import static org.eclipse.hono.registration.RegistrationConstants.ACTION_DEREGISTER;
import static org.eclipse.hono.registration.RegistrationConstants.ACTION_GET;
import static org.eclipse.hono.registration.RegistrationConstants.ACTION_REGISTER;
import static org.eclipse.hono.registration.RegistrationConstants.APP_PROPERTY_STATUS;
import static org.eclipse.hono.util.Constants.DEFAULT_TENANT;
import static org.eclipse.hono.util.MessageHelper.getLinkName;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.authorization.impl.InMemoryAuthorizationService;
import org.eclipse.hono.registration.RegistrationConstants;
import org.eclipse.hono.registration.impl.InMemoryRegistrationAdapter;
import org.eclipse.hono.registration.impl.RegistrationEndpoint;
import org.eclipse.hono.telemetry.impl.MessageDiscardingTelemetryAdapter;
import org.eclipse.hono.telemetry.impl.TelemetryEndpoint;
import org.eclipse.hono.util.TestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.impl.ProtonReceiverImpl;

/**
 * Tests sending registration messages to Hono Server.
 */
@RunWith(VertxUnitRunner.class)
public class RegistrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationTest.class);
    private static final String BIND_ADDRESS = InetAddress.getLoopbackAddress().getHostAddress();
    private static final int COUNT = 20;
    private static final int TIMEOUT = 5000; // milliseconds
    private static final String DEVICE = "device";
    private static final String DEVICE_1 = DEVICE + "1";

    private Vertx vertx;
    private ProtonConnection connection;
    private ProtonSender protonSender;
    private ProtonReceiver protonReceiver;

    private final Map<String, Handler<Message>> handlers = new HashMap<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    @Before
    public void init() {
        vertx = Vertx.vertx();
    }

    @After
    public void disconnect(final TestContext ctx) {

        if (protonSender != null) {
            protonSender.close();
        }
        if (protonReceiver != null) {
            protonReceiver.close();
        }
        if (connection != null) {
            connection.close();
        }
        if (vertx != null) {
            vertx.close(ctx.asyncAssertSuccess(done -> LOG.info("Vertx has been shut down")));
        }
    }

    @Test(timeout = TIMEOUT)
    public void testRegisterDevices(final TestContext ctx) throws InterruptedException {

        LOG.debug("starting device registration test {}//{}", vertx, vertx.deploymentIDs());
        final Async deployed = ctx.async();
        final Async receiverOpen = ctx.async();
        final Async senderOpen = ctx.async();
        final AtomicLong start = new AtomicLong();

        final Endpoint registrationEndpoint = new RegistrationEndpoint(vertx, false);
        final Endpoint telemetryEndpoint = new TelemetryEndpoint(vertx, false);
        final HonoServer server = createServer(telemetryEndpoint, registrationEndpoint);

        vertx.deployVerticle(MessageDiscardingTelemetryAdapter.class.getName());
        vertx.deployVerticle(InMemoryRegistrationAdapter.class.getName());
        vertx.deployVerticle(InMemoryAuthorizationService.class.getName());
        vertx.deployVerticle(server, res -> {
            ctx.assertTrue(res.succeeded());
            deployed.complete();
        });
        deployed.awaitSuccess(TIMEOUT);
        LOG.debug("deployed verticles {}//{}", vertx, vertx.deploymentIDs());

        connectToServer(ctx, server);

        final String receiverAddress =  RegistrationConstants.NODE_ADDRESS_REGISTRATION_PREFIX + DEFAULT_TENANT + "/reply-1234";
        protonReceiver = connection.createReceiver(receiverAddress);
        protonReceiver.openHandler(opened -> {
                LOG.debug("inbound link created [{}] at {}", getLinkName(opened.result()), opened.result().getSource().getAddress());
                receiverOpen.complete();
            })
           .closeHandler(closed -> LOG.debug("receiver closed {}...", ((ProtonReceiverImpl) closed.result()).getName()))
           .handler((delivery, message) -> {
               LOG.debug("Received reply {}", message);
               final String correlationId = (String) message.getCorrelationId();
               ofNullable(handlers.remove(correlationId)).ifPresent(h -> h.handle(message));
           }).setPrefetch(10).open();

        LOG.info("Waiting for receiver...");
        receiverOpen.awaitSuccess(TIMEOUT);
        LOG.info("Receiver open...");

        protonSender = connection.createSender(RegistrationConstants.NODE_ADDRESS_REGISTRATION_PREFIX + DEFAULT_TENANT);
        protonSender
           .setQoS(ProtonQoS.AT_LEAST_ONCE)
           .closeHandler(closed -> LOG.debug("Closed link {}...", getLinkName(closed.result())))
           .openHandler(senderOpened -> {
               ctx.assertTrue(senderOpened.succeeded());
               LOG.debug("outbound link created, now starting to send messages");
               start.set(System.currentTimeMillis());

               getDevice(ctx, DEVICE_1, HTTP_NOT_FOUND);
               deregisterDevice(ctx, DEVICE_1, HTTP_NOT_FOUND);

               //register devices
               IntStream.range(0, COUNT).forEach(i -> registerDevice(ctx, DEVICE + i, HTTP_OK));

               getDevice(ctx, DEVICE_1, HTTP_OK);

               // remove devices
               IntStream.range(0, COUNT).forEach(i -> deregisterDevice(ctx, DEVICE + i, HTTP_OK));

               getDevice(ctx, DEVICE_1, HTTP_NOT_FOUND);
               deregisterDevice(ctx, DEVICE_1, HTTP_NOT_FOUND);

               senderOpen.complete();

           }).open();

        senderOpen.await(TIMEOUT);
    }

    private void registerDevice(final TestContext ctx, final String deviceId, final int expectedStatusCode) {
        final String messageId = nextMessageId();
        final String replyTo = protonReceiver.getSource().getAddress();
        registerReplyHandler(ctx, messageId, expectedStatusCode);
        protonSender.send(TestSupport.newRegistrationMessage(messageId, ACTION_REGISTER, DEFAULT_TENANT, deviceId, replyTo));
    }

    private void deregisterDevice(final TestContext ctx, final String deviceId, final int expectedStatusCode) {
        final String messageId = nextMessageId();
        final String replyTo = protonReceiver.getSource().getAddress();
        registerReplyHandler(ctx, messageId, expectedStatusCode);
        protonSender.send(TestSupport.newRegistrationMessage(messageId, ACTION_DEREGISTER, DEFAULT_TENANT, deviceId, replyTo));
    }

    private void getDevice(final TestContext ctx, final String deviceId, final int expectedStatusCode) {
        final String messageId = nextMessageId();
        final String replyTo = protonReceiver.getSource().getAddress();
        registerReplyHandler(ctx, messageId, expectedStatusCode);
        protonSender.send(TestSupport.newRegistrationMessage(messageId, ACTION_GET, DEFAULT_TENANT, deviceId, replyTo));
    }

    private void registerReplyHandler(final TestContext ctx, final String correlationId, final int expectedStatusCode) {
        final Async async = ctx.async();
        handlers.put(correlationId, m -> {
            ctx.assertNotNull(m.getApplicationProperties());
            ctx.assertEquals(m.getApplicationProperties().getValue().get(APP_PROPERTY_STATUS), Integer.toString(expectedStatusCode));
            async.complete();
        });
    }

    private String nextMessageId() {
        return "test-" + counter.getAndIncrement();
    }

    private static HonoServer createServer(final Endpoint... endpoint) {
        final HonoServer result = new HonoServer(BIND_ADDRESS, 0, false);
        Stream.of(endpoint).filter(Objects::nonNull).forEach(result::addEndpoint);
        return result;
    }

    private void connectToServer(final TestContext ctx, final HonoServer server) {
        connection = TestSupport.openConnection(ctx, vertx, server.getBindAddress(), server.getPort());
    }
}
