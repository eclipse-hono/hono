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
package org.eclipse.hono.telemetry.impl;

import static org.eclipse.hono.TestSupport.CLIENT_ID;
import static org.eclipse.hono.TestSupport.DEFAULT_CREDITS;
import static org.eclipse.hono.TestSupport.newClient;
import static org.eclipse.hono.TestSupport.newMockSender;
import static org.eclipse.hono.TestSupport.newMockSenderFactory;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.server.SenderFactory;
import org.eclipse.hono.server.UpstreamReceiver;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Test;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * Verifies behavior of the {@code ForwardingTelemetryDownstreamAdapter}.
 *
 */
public class ForwardingTelemetryDownstreamAdapterTest {

    private static final String TELEMETRY_MSG_CONTENT = "hello";
    private static final String DEVICE_ID = "myDevice";

    @Test
    public void testProcessTelemetryDataForwardsMessageToDownstreamSender() throws InterruptedException {

        final Vertx vertx = mock(Vertx.class);
        final UpstreamReceiver client = newClient();
        final ProtonDelivery delivery = mock(ProtonDelivery.class);

        // GIVEN an adapter with a connection to a downstream container
        final CountDownLatch latch = new CountDownLatch(1);
        ProtonSender sender = newMockSender(false);
        when(sender.send(any(Message.class))).then(invocation -> {
            latch.countDown();
            return null;
        });
        ForwardingTelemetryDownstreamAdapter adapter = new ForwardingTelemetryDownstreamAdapter(vertx, newMockSenderFactory(sender));
        adapter.addSender("CON_ID", CLIENT_ID, sender);

        // WHEN processing a telemetry message
        Message msg = ProtonHelper.message(TELEMETRY_MSG_CONTENT);
        MessageHelper.addDeviceId(msg, DEVICE_ID);
        adapter.processMessage(client, delivery, msg);

        // THEN the message has been delivered to the downstream container
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testClientAttachedReplenishesClientOnSuccess() throws InterruptedException {

        final Vertx vertx = mock(Vertx.class);
        final ProtonSender sender = newMockSender(false);
        final ProtonConnection con = mock(ProtonConnection.class);
        final SenderFactory senderFactory = newMockSenderFactory(sender);
        final ResourceIdentifier targetAddress = ResourceIdentifier.from(TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX, "myTenant", null);
        final UpstreamReceiver client = newClient();

        when(client.getTargetAddress()).thenReturn(targetAddress.toString());

        // GIVEN an adapter with a connection to the downstream container
        ForwardingTelemetryDownstreamAdapter adapter = new ForwardingTelemetryDownstreamAdapter(vertx, senderFactory);
        adapter.setDownstreamConnection(con);

        // WHEN a client wants to attach to Hono for uploading telemetry data
        adapter.onClientAttach(client, s -> {});

        // THEN assert that the client is given some credit
        verify(client).replenish(DEFAULT_CREDITS);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testHandleFlowForwardsDrainRequestUpstream() throws InterruptedException {

        final Vertx vertx = mock(Vertx.class);
        final ProtonSender sender = newMockSender(false);
        final ProtonConnection con = mock(ProtonConnection.class);
        final SenderFactory senderFactory = newMockSenderFactory(sender);
        final ResourceIdentifier targetAddress = ResourceIdentifier.from(TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX, "myTenant", null);
        final UpstreamReceiver client = newClient();
        when(client.getTargetAddress()).thenReturn(targetAddress.toString());

        // GIVEN an adapter with a connection to the downstream container and a client attached
        ForwardingTelemetryDownstreamAdapter adapter = new ForwardingTelemetryDownstreamAdapter(vertx, senderFactory);
        adapter.setDownstreamConnection(con);
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
        Vertx vertx = mock(Vertx.class);

        when(client.getTargetAddress()).thenReturn(targetAddress.toString());
        when(client.getConnectionId()).thenReturn("CON_ID");

        // GIVEN an adapter without connection to the downstream container
        ForwardingTelemetryDownstreamAdapter adapter = new ForwardingTelemetryDownstreamAdapter(vertx, newMockSenderFactory(null));

        // WHEN a client wants to attach to Hono for uploading telemetry data
        // THEN assert that no sender can be created
        adapter.onClientAttach(client, s -> {
            assertFalse(s.succeeded());
        });
    }
}
