/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
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

import static org.eclipse.hono.TestSupport.*;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Record;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonSession;

/**
 * Verifies standard behavior of {@code ForwardingDownstreamAdapter}.
 */
@RunWith(VertxUnitRunner.class)
public class ForwardingDownstreamAdapterTest {

    private static final Vertx vertx = Vertx.vertx();

    private ResourceIdentifier          targetAddress = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, "myTenant", null);
    private ForwardingDownstreamAdapter adapter;
    private ConnectionFactory           connectionFactory;
    private Record                      attachments;
    private ProtonConnection            con;

    /**
     * Initializes mocks etc.
     */
    @Before
    public void setup() {
        attachments = mock(Record.class);
        ProtonSession session = mock(ProtonSession.class);
        con = mock(ProtonConnection.class);
        when(con.getRemoteContainer()).thenReturn("downstream");
        when(con.createSession()).thenReturn(session);
        when(con.attachments()).thenReturn(attachments);
        connectionFactory = newMockConnectionFactory(con, false);
    }

    /**
     * Verifies that an upstream client is replenished with credits from the downstream container
     * when a link is successfully established.
     */
    @Test
    public void testClientAttachedReplenishesClientOnSuccess() {

        final UpstreamReceiver client = newClient();

        when(client.getTargetAddress()).thenReturn(targetAddress.toString());

        // GIVEN an adapter with a connection to the downstream container
        givenADownstreamAdapter();
        adapter.setDownstreamConnectionFactory(connectionFactory);
        adapter.start(Future.future());

        // WHEN a client wants to attach to Hono for uploading telemetry data
        adapter.onClientAttach(client, s -> {});

        // THEN assert that the client is given some credit
        verify(client).replenish(DEFAULT_CREDITS);
    }

    /**
     * Verifies that <em>drain</em> requests received from the downstream container are forwarded
     * to upstream clients.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testHandleFlowForwardsDrainRequestUpstream() {

        final UpstreamReceiver client = newClient();
        when(client.getTargetAddress()).thenReturn(targetAddress.toString());
        final ProtonSender drainingSender = newMockSender(true);

        // GIVEN an adapter with a connection to the downstream container and a client attached
        givenADownstreamAdapter();
        adapter.setDownstreamConnectionFactory(connectionFactory);
        adapter.start(Future.future());
        adapter.addSender(client, drainingSender);

        // WHEN the downstream sender drains the adapter
        adapter.handleFlow(drainingSender, client);

        // THEN assert that the upstream client has been drained
        verify(client).drain(anyLong(), any(Handler.class));
    }

    /**
     * Verifies that the adapter refuses to accept a link from an upstream client
     * when there is no connection to the downstream container.
     * 
     * @param ctx The Vert.x test context. 
     */
    @Test
    public void testGetDownstreamSenderClosesLinkIfDownstreamConnectionIsBroken(final TestContext ctx) {

        final UpstreamReceiver client = newClient();

        when(client.getTargetAddress()).thenReturn(targetAddress.toString());
        when(client.getConnectionId()).thenReturn("CON_ID");

        // GIVEN an adapter without connection to the downstream container
        givenADownstreamAdapter();
        adapter.setDownstreamConnectionFactory(newMockConnectionFactory(true));
        adapter.disableRetryOnFailedConnectAttempt();
        adapter.start(Future.future());

        // WHEN a client wants to attach to Hono for uploading telemetry data
        // THEN assert that no sender can be created
        Async clientAttachFailure = ctx.async();
        adapter.onClientAttach(client, s -> {
            if (s.failed()) {
                clientAttachFailure.complete();
            }
        });
        clientAttachFailure.await(600);
        assertTrue(adapter.isActiveSendersEmpty());
        assertTrue(adapter.isSendersPerConnectionEmpty());
    }

    /**
     * Verifies that corresponding sender links to the downstream container are closed when
     * a connection to an upstream client is lost/closed.
     */
    @Test
    public void testOnClientDisconnectClosesDownstreamSenders() {

        final String upstreamConnection = "upstream-connection-id";
        final String linkId = "link-id";
        final UpstreamReceiver client = newClient(linkId, upstreamConnection);
        final ProtonSender downstreamSender = newMockSender(false);

        givenADownstreamAdapter(downstreamSender);
        adapter.setDownstreamConnectionFactory(connectionFactory);
        adapter.start(Future.future());
        adapter.addSender(client, downstreamSender);

        // WHEN the upstream client disconnects
        adapter.onClientDisconnect(upstreamConnection);

        // THEN the downstream sender is closed and removed from the sender list
        verify(downstreamSender).close();
        assertTrue(adapter.isActiveSendersEmpty());
        assertTrue(adapter.isSendersPerConnectionEmpty());
    }

    /**
     * Verifies that the adapter tries to re-establish a lost connection to a downstream container.
     */
    @Test
    public void testDownstreamDisconnectTriggersReconnect() {

        // expect the connection factory to be invoked twice
        // first on initial connection
        // second on re-connect attempt
        HandlerCapturingConnectionFactory factory = new HandlerCapturingConnectionFactory(con, 2);

        // GIVEN an adapter connected to a downstream container
        givenADownstreamAdapter();
        adapter.setDownstreamConnectionFactory(factory);
        adapter.start(Future.future());

        // WHEN the downstream connection fails
        factory.getDisconnectHandler().handle(con);

        // THEN the adapter tries to reconnect to the downstream container
        if (!factory.await(1, TimeUnit.SECONDS)) {
            fail("adapter did not re-connect to downstream container");
        } else {
            assertTrue(adapter.isActiveSendersEmpty());
        }
    }

    /**
     * Verifies that the adapter tries to re-connect to a downstream container once the existing
     * connection has been closed remotely.
     */
    @Test
    public void testOnRemoteCloseTriggersReconnect() {

        // expect the connection factory to be invoked twice
        // first on initial connection
        // second on re-connect attempt
        HandlerCapturingConnectionFactory factory = new HandlerCapturingConnectionFactory(con, 2);

        // GIVEN an adapter connected to a downstream container
        givenADownstreamAdapter();
        adapter.setDownstreamConnectionFactory(factory);
        adapter.start(Future.future());

        // WHEN the downstream container closes the connection
        factory.getCloseHandler().handle(Future.succeededFuture(con));

        // THEN the adapter tries to reconnect to the downstream container
        if (!factory.await(1, TimeUnit.SECONDS)) {
            fail("adapter did not re-connect to downstream container");
        } else {
            assertTrue(adapter.isActiveSendersEmpty());
        }
    }

    /**
     * Verifies that all links to upstream clients are closed when the connection to the
     * downstream container is lost.
     */
    @Test
    public void testDownstreamDisconnectClosesUpstreamReceivers() {

        final UpstreamReceiver client = newClient();
        final ProtonSender downstreamSender = newMockSender(false);
        // expect the connection factory to be invoked twice
        // first on initial connection
        // second on re-connect attempt
        HandlerCapturingConnectionFactory factory = new HandlerCapturingConnectionFactory(con, 2);

        // GIVEN an adapter connected to a downstream container
        givenADownstreamAdapter(downstreamSender);
        adapter.setDownstreamConnectionFactory(factory);
        adapter.start(Future.future());
        adapter.addSender(client, downstreamSender);

        // WHEN the downstream connection fails
        factory.getDisconnectHandler().handle(con);

        // THEN the adapter tries to reconnect to the downstream container and has closed all upstream receivers
        factory.await(1, TimeUnit.SECONDS);
        verify(client).close(any(ErrorCondition.class));
        assertTrue(adapter.isActiveSendersEmpty());
        assertTrue(adapter.isSendersPerConnectionEmpty());
    }

    /**
     * Verifies that the downstream sender's detach handler closes the sender and corresponding
     * upstream client.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDownstreamLinkDetachHandlerClosesUpstreamReceivers() {
        testDownstreamLinkHandlerClosesUpstreamReceiver((sender, captor) -> verify(sender).detachHandler(captor.capture()));
    }

    /**
     * Verifies that the downstream sender's close handler closes the sender and corresponding
     * upstream client.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDownstreamLinkCloseHandlerClosesUpstreamReceivers() {
        testDownstreamLinkHandlerClosesUpstreamReceiver((sender, captor) -> verify(sender).closeHandler(captor.capture()));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void testDownstreamLinkHandlerClosesUpstreamReceiver(
            final BiConsumer<ProtonSender, ArgumentCaptor<Handler>> handlerCaptor) {

        final UpstreamReceiver client = newClient();
        final ProtonSender downstreamSender = newMockSender(false);
        when(downstreamSender.isOpen()).thenReturn(Boolean.FALSE);

        final HandlerCapturingConnectionFactory factory = new HandlerCapturingConnectionFactory(con);

        // GIVEN an adapter connected to a downstream container
        givenADownstreamAdapter(downstreamSender);
        adapter.setDownstreamConnectionFactory(factory);
        adapter.start(Future.future());
        adapter.onClientAttach(client, s -> {});
        final ArgumentCaptor<Handler> captor = ArgumentCaptor.forClass(Handler.class);
        handlerCaptor.accept(downstreamSender, captor);

        // WHEN the downstream container detaches the sender link
        captor.getValue().handle(Future.succeededFuture(downstreamSender));

        // THEN the upstream client is closed
        verify(client).close(any());
        // and the sender is removed from the list of active senders
        assertTrue(adapter.isActiveSendersEmpty());
    }

    /**
     * Verifies that all requests from upstream clients to attach are failed when the connection to the
     * downstream container is lost.
     * 
     * @param ctx The Vert.x test context.
     */
    @Test
    public void testDownstreamDisconnectFailsClientAttachRequests(final TestContext ctx) {

        when(con.isDisconnected()).thenReturn(Boolean.FALSE);
        final UpstreamReceiver client = newClient();
        when(client.getTargetAddress()).thenReturn(targetAddress.toString());
        final HandlerCapturingConnectionFactory factory = new HandlerCapturingConnectionFactory(con);
        final SenderFactory senderFactory = (con, address, qos, drainHandler, closeHook) -> {
            Future<ProtonSender> result = Future.future();
            return result;
        };
        final Async disconnected = ctx.async();

        // GIVEN an adapter connected to a downstream container with a client trying to attach
        givenADownstreamAdapter(senderFactory);
        adapter.setDownstreamConnectionFactory(factory);
        adapter.start(Future.future());
        assertTrue(adapter.isConnected());
        adapter.onClientAttach(client, attachAttempt -> {
            if (attachAttempt.failed()) {
                disconnected.countDown();
            } else {
                fail("client attach should not have succeeded");
            }
        });

        // WHEN the downstream connection fails
        factory.getDisconnectHandler().handle(con);

        // THEN the adapter tries to reconnect to the downstream container and has closed all upstream receivers
        disconnected.await(1000);
        assertTrue(adapter.isActiveSendersEmpty());
        assertTrue(adapter.isSendersPerConnectionEmpty());
    }

    /**
     * Verifies the adapter repreatedly tries to connect to downstream container until it succeeds.
     */
    @Test
    public void testConnectToDownstreamRetriesToConnectOnFailedAttempt() {

        final HandlerCapturingConnectionFactory factory = new HandlerCapturingConnectionFactory(null, 3);

        // GIVEN an adapter
        givenADownstreamAdapter();
        adapter.setDownstreamConnectionFactory(factory);

        // WHEN starting the adapter
        adapter.start(Future.future());

        // THEN the adapter continuously tries to connect to the downstream container
        assertTrue(factory.await(4 * Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS));
    }

    private void givenADownstreamAdapter() {
        givenADownstreamAdapter(newMockSender(false));
    }

    private void givenADownstreamAdapter(final ProtonSender senderToCreate) {
        givenADownstreamAdapter(newMockSenderFactory(senderToCreate));
    }

    private void givenADownstreamAdapter(final SenderFactory senderFactory) {

        adapter = new ForwardingDownstreamAdapter(vertx, senderFactory) {

            @Override
            protected ProtonQoS getDownstreamQos() {
                return ProtonQoS.AT_MOST_ONCE;
            }

            @Override
            protected void forwardMessage(final ProtonSender sender, final Message msg, final ProtonDelivery delivery) {
                // nothing to do
            }
        };
        adapter.setMetrics(mock(MessagingMetrics.class));
    }

    /**
     * A connection factory that provides access to the disconnect and close handlers
     * registered on the connections created by the factory.
     *
     */
    private class HandlerCapturingConnectionFactory implements ConnectionFactory {

        private Handler<ProtonConnection> disconnectHandler;
        private Handler<AsyncResult<ProtonConnection>> closeHandler;
        private CountDownLatch expectedConnectionAttempts;
        private ProtonConnection connectionToCreate;

        HandlerCapturingConnectionFactory(final ProtonConnection conToCreate) {
            this(conToCreate, 1);
        }

        HandlerCapturingConnectionFactory(final ProtonConnection conToCreate, final int expectedConnectionAttempts) {
            this.connectionToCreate = conToCreate;
            this.expectedConnectionAttempts = new CountDownLatch(expectedConnectionAttempts);
        }

        @Override
        public void connect(
                final ProtonClientOptions options,
                final Handler<AsyncResult<ProtonConnection>> closeHandler,
                final Handler<ProtonConnection> disconnectHandler,
                final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {
            connect(options, null, null, closeHandler, disconnectHandler, connectionResultHandler);
        }

        @Override
        public void connect(
                final ProtonClientOptions options,
                final String username,
                final String password,
                final Handler<AsyncResult<ProtonConnection>> closeHandler,
                final Handler<ProtonConnection> disconnectHandler,
                final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {

            if (expectedConnectionAttempts.getCount() > 0) {
                expectedConnectionAttempts.countDown();
                this.disconnectHandler = disconnectHandler;
                this.closeHandler = closeHandler;
                if (connectionToCreate == null) {
                    connectionResultHandler.handle(Future.failedFuture("cannot connect"));
                } else {
                    connectionResultHandler.handle(Future.succeededFuture(connectionToCreate));
                }
            }
        }

        @Override
        public String getName() {
            return "client";
        }

        @Override
        public String getHost() {
            return "server";
        }

        @Override
        public int getPort() {
            return 5672;
        }

        @Override
        public String getPathSeparator() {
            return Constants.DEFAULT_PATH_SEPARATOR;
        }

        public Handler<ProtonConnection> getDisconnectHandler() {
            return disconnectHandler;
        }

        public Handler<AsyncResult<ProtonConnection>> getCloseHandler() {
            return closeHandler;
        }

        public boolean await(final long timeout, final TimeUnit unit) {
            try {
                return expectedConnectionAttempts.await(timeout, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
