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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.amqp.transport.Target;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.authorization.AuthorizationConstants;
import org.eclipse.hono.authorization.Permission;
import org.eclipse.hono.authorization.impl.InMemoryAuthorizationService;
import org.eclipse.hono.impl.ProtonSenderWriteStream;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.telemetry.impl.MessageDiscardingTelemetryAdapter;
import org.eclipse.hono.telemetry.impl.TelemetryEndpoint;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryDataReadStream;
import org.eclipse.hono.util.TestSupport;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Integration tests for Hono Server.
 *
 */
@RunWith(VertxUnitRunner.class)
public class HonoServerTest {

    private static final Logger LOG          = LoggerFactory.getLogger(HonoServerTest.class);
    private static final String BIND_ADDRESS = InetAddress.getLoopbackAddress().getHostAddress();
    Vertx                       vertx;
    ProtonConnection            connection;
    ProtonSender                protonSender;

    @After
    public void disconnect(final TestContext ctx) {
        if (connection != null) {
            connection.close();
        }
        if (vertx != null) {
            vertx.close(ctx.asyncAssertSuccess(done -> LOG.info("Vertx has been shut down")));
        }
    }

    private static HonoServer createServer(final Endpoint telemetryEndpoint) {
        HonoServer result = new HonoServer(BIND_ADDRESS, 0, false);
        if (telemetryEndpoint != null) {
            result.addEndpoint(telemetryEndpoint);
        }
        return result;
    }

    private void connectToServer(final TestContext ctx, final HonoServer server) {

        connection = TestSupport.openConnection(ctx, vertx, BIND_ADDRESS, server.getPort());
    }

    @Test
    public void testStartFailsIfTelemetryEndpointIsNotConfigured() {

        // GIVEN a Hono server without a telemetry endpoint configured
        HonoServer server = createServer(null);
        server.init(mock(Vertx.class), mock(Context.class));

        // WHEN starting the server
        Future<Void> startupFuture = Future.future();
        server.start(startupFuture);
        assertTrue(startupFuture.failed());
    }

    @Test
    public void testHandleReceiverOpenForwardsToTelemetryEndpoint() throws InterruptedException {

        // GIVEN a server with a telemetry endpoint
        final String targetAddress = TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX + Constants.DEFAULT_TENANT;
        final EventBus eventBus = mock(EventBus.class);
        final JsonObject authMsg = AuthorizationConstants.getAuthorizationMsg(Constants.DEFAULT_SUBJECT, targetAddress, Permission.WRITE.toString());
        TestSupport.expectReplyForMessage(eventBus, AuthorizationConstants.EVENT_BUS_ADDRESS_AUTHORIZATION_IN, authMsg, AuthorizationConstants.ALLOWED);
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        final CountDownLatch linkEstablished = new CountDownLatch(1);
        final Endpoint telemetryEndpoint = new BaseEndpoint(vertx) {

            @Override
            public String getName() {
                return TelemetryConstants.TELEMETRY_ENDPOINT;
            }

            @Override
            public void onLinkAttach(final ProtonReceiver receiver, final ResourceIdentifier targetResource) {
                linkEstablished.countDown();
            }
        };
        HonoServer server = createServer(telemetryEndpoint);
        server.init(vertx, mock(Context.class));

        // WHEN a client connects to the server using a telemetry address
        final Target target = getTarget(targetAddress);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteTarget()).thenReturn(target);
        server.handleReceiverOpen(mock(ProtonConnection.class), receiver);

        // THEN the server delegates link establishment to the telemetry endpoint 
        assertTrue(linkEstablished.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testHandleReceiverOpenRejectsUnauthorizedClient() throws InterruptedException {

        // GIVEN a server with a telemetry endpoint
        final String restrictedTargetAddress = TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX + "RESTRICTED_TENANT";
        final EventBus eventBus = mock(EventBus.class);
        final JsonObject authMsg = AuthorizationConstants.getAuthorizationMsg(Constants.DEFAULT_SUBJECT, restrictedTargetAddress, Permission.WRITE.toString());
        TestSupport.expectReplyForMessage(eventBus, AuthorizationConstants.EVENT_BUS_ADDRESS_AUTHORIZATION_IN, authMsg, AuthorizationConstants.DENIED);
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);

        final Endpoint telemetryEndpoint = mock(Endpoint.class);
        when(telemetryEndpoint.getName()).thenReturn(TelemetryConstants.TELEMETRY_ENDPOINT);
        HonoServer server = createServer(telemetryEndpoint);
        server.init(vertx, mock(Context.class));

        // WHEN a client connects to the server using a telemetry address for a tenant it is not authorized to write to
        final CountDownLatch linkClosed = new CountDownLatch(1);
        final Target target = getTarget(restrictedTargetAddress);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteTarget()).thenReturn(target);
        when(receiver.close()).thenAnswer(new Answer<ProtonReceiver>() {
            @Override
            public ProtonReceiver answer(final InvocationOnMock invocation) throws Throwable {
                linkClosed.countDown();
                return receiver;
            }
        });
        server.handleReceiverOpen(mock(ProtonConnection.class), receiver);

        // THEN the server closes the link with the client
        assertTrue(linkClosed.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testTelemetryUpload(final TestContext ctx) {
        vertx = Vertx.vertx();
        LOG.debug("starting telemetry upload test");
        final int messagesToBeSent = 30;
        final Async deployed = ctx.async();
        final Async sent = ctx.async(messagesToBeSent);
        int timeout = 2000; // milliseconds
        final AtomicLong start = new AtomicLong();

        TelemetryEndpoint telemetryEndpoint = new TelemetryEndpoint(vertx, false);
        HonoServer server = createServer(telemetryEndpoint);
        vertx.deployVerticle(MessageDiscardingTelemetryAdapter.class.getName());
        vertx.deployVerticle(new InMemoryAuthorizationService());
        vertx.deployVerticle(server, res -> {
            ctx.assertTrue(res.succeeded());
            deployed.complete();
        });
        deployed.await(1000);
        connectToServer(ctx, server);

        protonSender = connection.createSender(TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX + Constants.DEFAULT_TENANT);
        protonSender
                .setQoS(ProtonQoS.AT_MOST_ONCE)
                .openHandler(senderOpen -> {
                    ctx.assertTrue(senderOpen.succeeded());
                    LOG.debug("outbound link created, now starting to send messages");
                    start.set(System.currentTimeMillis());
                    uploadTelemetryData(messagesToBeSent, protonSender, allProduced -> {
                        LOG.debug("all {} telemetry messages have been produced", messagesToBeSent);
                    }, sent);
                }).open();

        sent.await(timeout);
        LOG.info("messages sent after {} milliseconds: {}", System.currentTimeMillis() - start.get(),
                messagesToBeSent - sent.count());
        protonSender.close();
    }

    private Target getTarget(final String targetAddress) {
        Target result = mock(Target.class);
        when(result.getAddress()).thenReturn(targetAddress);
        return result;
    }

    private void uploadTelemetryData(final int count, final ProtonSender sender, final Handler<Void> allProducedHandler,
            final Async sentMessages) {
        vertx.executeBlocking(future -> {
            ReadStream<Message> rs = new TelemetryDataReadStream(vertx, count, Constants.DEFAULT_TENANT);
            WriteStream<Message> ws = new ProtonSenderWriteStream(sender, delivered -> sentMessages.countDown());
            rs.endHandler(done -> future.complete());
            LOG.debug("pumping test telemetry data to Hono server");
            Pump.pump(rs, ws).start();
        }, done -> allProducedHandler.handle(null));
    }
}
