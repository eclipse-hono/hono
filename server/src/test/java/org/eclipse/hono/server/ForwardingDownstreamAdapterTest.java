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

import static org.eclipse.hono.TestSupport.*;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * Verifies standard behavior of {@code ForwardingDownstreamAdapter}.
 */
public class ForwardingDownstreamAdapterTest {

    private ForwardingDownstreamAdapter adapter;
    private ConnectionFactory connectionFactory;
    private Vertx vertx;

    /**
     * Initializes mocks etc.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        vertx = mock(Vertx.class);
        // make sure timer tasks are executed immediately
        doAnswer(invocation -> {
            invocation.getArgumentAt(1, Handler.class).handle(0L);
            return null;
            }).when(vertx).setTimer(anyLong(), any(Handler.class));

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
     */
    @Test
    public void testGetDownstreamSenderClosesLinkIfDownstreamConnectionIsBroken() {

        final ResourceIdentifier targetAddress = ResourceIdentifier.from(TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX, "myTenant", null);
        final UpstreamReceiver client = newClient();

        when(client.getTargetAddress()).thenReturn(targetAddress.toString());
        when(client.getConnectionId()).thenReturn("CON_ID");

        // GIVEN an adapter without connection to the downstream container
        givenADownstreamAdapter();
        adapter.setDownstreamConnectionFactory(newMockConnectionFactory(true));
        adapter.start(Future.future());

        // WHEN a client wants to attach to Hono for uploading telemetry data
        // THEN assert that no sender can be created
        adapter.onClientAttach(client, s -> {
            assertFalse(s.succeeded());
        });
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
    }

    /**
     * Verifies that all links to upstream clients are closed when the connection to the
     * downstream container is lost.
     */
    @Test
    public void testDownstreamDisconnectClosesUpstreamReceivers() {

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
    }

    private void givenADownstreamAdapter() {
        givenADownstreamAdapter(newMockSender(false));
    }

    private void givenADownstreamAdapter(final ProtonSender senderToCreate) {

        final SenderFactory senderFactory = newMockSenderFactory(senderToCreate);
        adapter = new ForwardingDownstreamAdapter(vertx, senderFactory) {

            @Override
            protected ProtonQoS getDownstreamQos() {
                return ProtonQoS.AT_MOST_ONCE;
            }

            @Override
            protected void forwardMessage(ProtonSender sender, Message msg, ProtonDelivery delivery) {
                // nothing to do
            }
        };
    }

    private class DisconnectHandlerProvidingConnectionFactory implements ConnectionFactory {

        private Handler<ProtonConnection> disconnectHandler;
        private CountDownLatch expectedConnectionAttemps;
        private ProtonConnection connectionToCreate;

        public DisconnectHandlerProvidingConnectionFactory(final ProtonConnection conToCreate, final int expectedConnectionAttempts) {
            this.connectionToCreate = conToCreate;
            this.expectedConnectionAttemps = new CountDownLatch(expectedConnectionAttempts);
        }

        @Override
        public void connect(
                final ProtonClientOptions options,
                final Handler<AsyncResult<ProtonConnection>> closeHandler,
                final Handler<ProtonConnection> disconnectHandler,
                final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {

            expectedConnectionAttemps.countDown();
            this.disconnectHandler = disconnectHandler;
            connectionResultHandler.handle(Future.succeededFuture(connectionToCreate));
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

        public boolean await(long timeout, TimeUnit unit) {
            try {
                return expectedConnectionAttemps.await(timeout, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
