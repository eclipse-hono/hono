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

import static org.mockito.Mockito.*;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.amqp.transport.Target;
import org.apache.qpid.proton.message.Message;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
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
    Vertx                       vertx;
    ProtonConnection            connection;
    ProtonSender                protonSender;

    @Before
    public void init() {
        vertx = Vertx.vertx();
    }

    @After
    public void disconnect(final TestContext ctx) {
        if (connection != null) {
            connection.close();
        }
        vertx.close(ctx.asyncAssertSuccess(done -> LOG.info("Vertx has been shut down")));
    }

    private static HonoServer createServer(final Endpoint telemetryEndpoint) {
        HonoServer result = new HonoServer();
        result.setHost("0.0.0.0");
        result.setPort(0);
        if (telemetryEndpoint != null) {
            result.addEndpoint(telemetryEndpoint);
        }
        return result;
    }

    private void connectToServer(final TestContext ctx, final HonoServer server) {

        String host = InetAddress.getLoopbackAddress().getHostAddress();
        int port = server.getPort();
        connection = TestSupport.openConnection(ctx, vertx, host, port);
    }

    @Test
    public void testStartFailsIfTelemetryEndpointIsNotConfigured(final TestContext ctx) {

        // GIVEN a Hono server without a telemetry endpoint configured
        HonoServer server = createServer(null);

        // WHEN starting the server
        vertx.deployVerticle(server,
                // THEN startup fails
                ctx.asyncAssertFailure());
    }

    @Test
    public void testHandleReceiverOpenForwardsToTelemetryEndpoint(final TestContext ctx) {

        // GIVEN a server with a telemetry endpoint
        final Async linkEstablished = ctx.async();
        final Endpoint telemetryEndpoint = new Endpoint() {

            @Override
            public String getName() {
                return TelemetryConstants.TELEMETRY_ENDPOINT;
            }

            @Override
            public void establishLink(final ProtonReceiver receiver, final ResourceIdentifier targetResource) {
                linkEstablished.complete();
            }
        };
        HonoServer server = createServer(telemetryEndpoint);

        final Async deployment = ctx.async();
        vertx.deployVerticle(InMemoryAuthorizationService.class.getName());
        vertx.deployVerticle(server, res -> {
            ctx.assertTrue(res.succeeded());
            deployment.complete();
        });
        deployment.await(1000);

        // WHEN a client connects to the server using a telemetry address
        final Target target = getTarget(TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX + Constants.DEFAULT_TENANT);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteTarget()).thenReturn(target);
        server.handleReceiverOpen(mock(ProtonConnection.class), receiver);

        // THEN the server delegates link establishment to the telemetry endpoint 
        linkEstablished.await(1000);
    }

    @Test
    public void testHandleReceiverOpenRejectsUnauthorizedClient(final TestContext ctx) {

        // GIVEN a server with a telemetry endpoint
        final Async linkClosed = ctx.async();
        final Endpoint telemetryEndpoint = mock(Endpoint.class);
        when(telemetryEndpoint.getName()).thenReturn(TelemetryConstants.TELEMETRY_ENDPOINT);
        HonoServer server = createServer(telemetryEndpoint);

        final Async deployment = ctx.async();
        vertx.deployVerticle(InMemoryAuthorizationService.class.getName());
        vertx.deployVerticle(server, res -> {
            ctx.assertTrue(res.succeeded());
            deployment.complete();
        });
        deployment.await(1000);

        // WHEN a client connects to the server using a telemetry address for a tenant it is not authorized to write to
        final Target target = getTarget(TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX + "RESTRICTED_TENANT");
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteTarget()).thenReturn(target);
        when(receiver.close()).thenAnswer(new Answer<ProtonReceiver>() {
            @Override
            public ProtonReceiver answer(final InvocationOnMock invocation) throws Throwable {
                linkClosed.complete();
                return receiver;
            }
        });
        server.handleReceiverOpen(mock(ProtonConnection.class), receiver);

        // THEN the server closes the link with the client
        linkClosed.await(1000);
    }

    @Test
    public void testTelemetryUpload(final TestContext ctx) {
        LOG.debug("starting telemetry upload test");
        final int messagesToBeSent = 30;
        final Async deployed = ctx.async();
        final Async sent = ctx.async(messagesToBeSent);
        int timeout = 2000; // milliseconds
        final AtomicLong start = new AtomicLong();

        TelemetryEndpoint telemetryEndpoint = new TelemetryEndpoint(vertx);
        HonoServer server = createServer(telemetryEndpoint);
        vertx.deployVerticle(MessageDiscardingTelemetryAdapter.class.getName());
        vertx.deployVerticle(InMemoryAuthorizationService.class.getName());
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
