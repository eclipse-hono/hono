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
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.telemetry.TelemetryConstants;
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

    @Test
    public void testClientAttachedReplenishesClientOnSuccess() throws InterruptedException {

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

    @SuppressWarnings("unchecked")
    @Test
    public void testHandleFlowForwardsDrainRequestUpstream() throws InterruptedException {

        final ResourceIdentifier targetAddress = ResourceIdentifier.from(TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX, "myTenant", null);
        final UpstreamReceiver client = newClient();
        when(client.getTargetAddress()).thenReturn(targetAddress.toString());

        // GIVEN an adapter with a connection to the downstream container and a client attached
        givenADownstreamAdapter();
        adapter.setDownstreamConnectionFactory(connectionFactory);
        adapter.start(Future.future());
        adapter.onClientAttach(client, s -> {
            assertTrue(s.succeeded());
        });

        // WHEN the downstream sender drains the adapter
        ProtonSender drainingSender = newMockSender(true);
        adapter.handleFlow(drainingSender, client);

        // THEN assert that the upstream client has been drained
        verify(client).drain(anyInt(), any(Handler.class));
    }

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

    @Test
    public void testOnClientDisconnectClosesDownstreamSenders() {

        final String upstreamConnection = "upstream-connection-id";
        final String linkId = "link-id";
        final ProtonSender downstreamSender = newMockSender(false);

        givenADownstreamAdapter(downstreamSender);
        adapter.setDownstreamConnectionFactory(connectionFactory);
        adapter.start(Future.future());
        adapter.addSender(upstreamConnection, linkId, downstreamSender);

        // WHEN the upstream client disconnects
        adapter.onClientDisconnect(upstreamConnection);

        // THEN the downstream sender is closed and removed from the sender list
        verify(downstreamSender).close();
    }

    @Test
    public void testDownstreamDisconnectTriggersReconnect() throws InterruptedException {

        final ProtonConnection connectionToCreate = mock(ProtonConnection.class);
        when(connectionToCreate.getRemoteContainer()).thenReturn("downstream");
        // expect the connection factory to be invoked twice
        // first on initial connection
        // second on re-connect attempt
        CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<Handler> disconnectHandlerRef = new AtomicReference<>();
        ConnectionFactory factory = new ConnectionFactory() {

            @Override
            public void setHostname(final String hostname) {
            }

            @Override
            public void connect(
                    final ProtonClientOptions options,
                    final Handler<AsyncResult<ProtonConnection>> closeHandler,
                    final Handler<ProtonConnection> disconnectHandler,
                    final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {

                latch.countDown();
                disconnectHandlerRef.set(disconnectHandler);
                connectionResultHandler.handle(Future.succeededFuture(connectionToCreate));
            }
        };

        // GIVEN an adapter connected to a downstream container
        givenADownstreamAdapter();
        adapter.setDownstreamConnectionFactory(factory);
        adapter.start(Future.future());

        // WHEN the downstream connection fails
        disconnectHandlerRef.get().handle(connectionToCreate);

        // THEN the adapter tries to reconnect to the downstream container
        assertTrue(latch.await(1, TimeUnit.SECONDS));
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
}
