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

import static org.eclipse.hono.TestSupport.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.messaging.ForwardingDownstreamAdapter;
import org.eclipse.hono.messaging.SenderFactory;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

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

/**
 * Verifies standard behavior of {@code ForwardingDownstreamAdapter}.
 */
@RunWith(VertxUnitRunner.class)
public class ForwardingDownstreamAdapterTest {

    private ForwardingDownstreamAdapter adapter;
    private ConnectionFactory connectionFactory;
    private Vertx vertx;

    /**
     * Initializes mocks etc.
     */
    @Before
    public void setup() {
        vertx = Vertx.vertx();
        connectionFactory = newMockConnectionFactory(false);
    }

    /**
     * Verifies that an upstream client is replenished with credits from the downstream container
     * when a link is successfully established.
     */
    @Test
    public void testClientAttachedReplenishesClientOnSuccess() {

        final ResourceIdentifier targetAddress = ResourceIdentifier.from(TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX, "myTenant", null);
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
     * @throws InterruptedException
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testHandleFlowForwardsDrainRequestUpstream() throws InterruptedException {

        final ResourceIdentifier targetAddress = ResourceIdentifier.from(TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX, "myTenant", null);
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
        verify(client).drain(anyInt(), any(Handler.class));
    }

    /**
     * Verifies that the adapter refuses to accept a link from an upstream client
     * when there is no connection to the downstream container.
     * 
     * @param ctx The Vert.x test context. 
     */
    @Test
    public void testGetDownstreamSenderClosesLinkIfDownstreamConnectionIsBroken(final TestContext ctx) {

        final ResourceIdentifier targetAddress = ResourceIdentifier.from(TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX, "myTenant", null);
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

        final ProtonConnection connectionToCreate = mock(ProtonConnection.class);
        when(connectionToCreate.getRemoteContainer()).thenReturn("downstream");
        // expect the connection factory to be invoked twice
        // first on initial connection
        // second on re-connect attempt
        DisconnectHandlerProvidingConnectionFactory factory = new DisconnectHandlerProvidingConnectionFactory(connectionToCreate, 2);

        // GIVEN an adapter connected to a downstream container
        givenADownstreamAdapter();
        adapter.setDownstreamConnectionFactory(factory);
        adapter.start(Future.future());

        // WHEN the downstream connection fails
        factory.getDisconnectHandler().handle(connectionToCreate);

        // THEN the adapter tries to reconnect to the downstream container
        factory.await(1, TimeUnit.SECONDS);
        assertTrue(adapter.isActiveSendersEmpty());
    }

    /**
     * Verifies that all links to upstream clients are closed when the connection to the
     * downstream container is lost.
     * 
     * @throws Exception if the test fails.
     */
    @Test
    public void testDownstreamDisconnectClosesUpstreamReceivers() throws Exception {

        final ProtonConnection connectionToCreate = mock(ProtonConnection.class);
        when(connectionToCreate.getRemoteContainer()).thenReturn("downstream");
        final UpstreamReceiver client = newClient();
        final ProtonSender downstreamSender = newMockSender(false);
        // expect the connection factory to be invoked twice
        // first on initial connection
        // second on re-connect attempt
        DisconnectHandlerProvidingConnectionFactory factory = new DisconnectHandlerProvidingConnectionFactory(connectionToCreate, 2);

        // GIVEN an adapter connected to a downstream container
        givenADownstreamAdapter(downstreamSender);
        adapter.setDownstreamConnectionFactory(factory);
        adapter.start(Future.future());
        adapter.addSender(client, downstreamSender);

        // WHEN the downstream connection fails
        factory.getDisconnectHandler().handle(connectionToCreate);

        // THEN the adapter tries to reconnect to the downstream container and has closed all upstream receivers
        factory.await(1, TimeUnit.SECONDS);
        verify(client).close(any(ErrorCondition.class));
        assertTrue(adapter.isActiveSendersEmpty());
        assertTrue(adapter.isSendersPerConnectionEmpty());
    }

    /**
     * Verifies that all requests from upstream clients to attach are failed when the connection to the
     * downstream container is lost.
     * 
     * @param ctx The Vert.x test context.
     */
    @Test
    public void testDownstreamDisconnectFailsClientAttachRequests(final TestContext ctx) {

        final ProtonConnection connectionToCreate = mock(ProtonConnection.class);
        when(connectionToCreate.getRemoteContainer()).thenReturn("downstream");
        when(connectionToCreate.isDisconnected()).thenReturn(Boolean.FALSE);
        final UpstreamReceiver client = newClient();
        when(client.getTargetAddress()).thenReturn("telemetry/TENANT");
        final DisconnectHandlerProvidingConnectionFactory factory = new DisconnectHandlerProvidingConnectionFactory(connectionToCreate);
        final SenderFactory senderFactory = (con, address, qos, drainHandler) -> {
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
        factory.getDisconnectHandler().handle(connectionToCreate);

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

        final DisconnectHandlerProvidingConnectionFactory factory = new DisconnectHandlerProvidingConnectionFactory(null, 3);

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
    }

    private class DisconnectHandlerProvidingConnectionFactory implements ConnectionFactory {

        private Handler<ProtonConnection> disconnectHandler;
        private CountDownLatch expectedConnectionAttempts;
        private ProtonConnection connectionToCreate;

        public DisconnectHandlerProvidingConnectionFactory(final ProtonConnection conToCreate) {
            this(conToCreate, 1);
        }

        public DisconnectHandlerProvidingConnectionFactory(final ProtonConnection conToCreate, final int expectedConnectionAttempts) {
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
