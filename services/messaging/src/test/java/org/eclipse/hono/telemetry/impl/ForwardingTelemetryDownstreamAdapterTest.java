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
package org.eclipse.hono.telemetry.impl;

import static org.eclipse.hono.TestSupport.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.TestSupport;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.event.impl.ForwardingEventDownstreamAdapter;
import org.eclipse.hono.messaging.MessagingMetrics;
import org.eclipse.hono.messaging.UpstreamReceiver;
import org.eclipse.hono.util.MessageHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * Verifies behavior of the {@code ForwardingTelemetryDownstreamAdapter}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class ForwardingTelemetryDownstreamAdapterTest {

    /**
     * Time out each test after 5 seconds.
     */
    @Rule
    public final Timeout globalTimeout = Timeout.seconds(5);

    private static final String TELEMETRY_MSG_CONTENT = "hello";
    private static final String DEVICE_ID = "myDevice";

    private ConnectionFactory connectionFactory;
    private Vertx vertx;

    /**
     * Initializes mocks etc.
     */
    @Before
    public void setup() {
        vertx = mock(Vertx.class);
        connectionFactory = TestSupport.newMockConnectionFactory(false);
    }

    /**
     * Verifies that pre-settled telemetry data uploaded by an upstream client is
     * forwarded to the downstream container and is accepted and settled immediately.
     * 
     * @param ctx The test context.
     */
    @Test
    public void testProcessMessageForwardsMessageToDownstreamSender(final TestContext ctx) {

        final UpstreamReceiver client = TestSupport.newClient();

        // GIVEN an adapter with a connection to a downstream container
        final ProtonSender sender = TestSupport.newMockSender(false);
        final ForwardingTelemetryDownstreamAdapter adapter = new ForwardingTelemetryDownstreamAdapter(
                vertx, TestSupport.newMockSenderFactory(sender));
        adapter.setMetrics(mock(MessagingMetrics.class));
        adapter.setDownstreamConnectionFactory(connectionFactory);
        adapter.start(Future.future());
        adapter.addSender(client, sender);

        // WHEN processing a pre-settled telemetry message
        final Message msg = ProtonHelper.message(TELEMETRY_MSG_CONTENT);
        MessageHelper.addDeviceId(msg, DEVICE_ID);
        final ProtonDelivery upstreamDelivery = mock(ProtonDelivery.class);
        when(upstreamDelivery.remotelySettled()).thenReturn(Boolean.TRUE);
        adapter.processMessage(client, upstreamDelivery, msg);

        // THEN the message is being delivered to the downstream container
        verify(sender).send(eq(msg));
        // and the upstream delivery is settled with the accepted outcome
        verify(upstreamDelivery).disposition(any(Accepted.class), eq(Boolean.TRUE));
    }

    /**
     * Verifies that an unsettled telemetry message received from an upstream client is
     * forwarded to the downstream container and the downstream container's disposition
     * is forwarded to the upstream client.
     * 
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testProcessMessageForwardsDownstreamDisposition(final TestContext ctx) {

        final UpstreamReceiver client = TestSupport.newClient();

        // GIVEN an adapter with a connection to a downstream container
        final ProtonSender sender = TestSupport.newMockSender(false);
        final ForwardingTelemetryDownstreamAdapter adapter = new ForwardingTelemetryDownstreamAdapter(
                vertx, TestSupport.newMockSenderFactory(sender));
        adapter.setMetrics(mock(MessagingMetrics.class));
        adapter.setDownstreamConnectionFactory(connectionFactory);
        adapter.start(Future.future());
        adapter.addSender(client, sender);

        // WHEN processing an unsettled telemetry message
        final Message msg = ProtonHelper.message(TELEMETRY_MSG_CONTENT);
        MessageHelper.addDeviceId(msg, DEVICE_ID);
        final ProtonDelivery upstreamDelivery = mock(ProtonDelivery.class);
        when(upstreamDelivery.remotelySettled()).thenReturn(Boolean.FALSE);
        adapter.processMessage(client, upstreamDelivery, msg);

        // THEN the message is being delivered to the downstream container
        final ArgumentCaptor<Handler> deliveryHandler = ArgumentCaptor.forClass(Handler.class);
        verify(sender).send(eq(msg), deliveryHandler.capture());

        // and when the downstream container rejects the message
        final ProtonDelivery downstreamDelivery = mock(ProtonDelivery.class);
        when(downstreamDelivery.remotelySettled()).thenReturn(Boolean.TRUE);
        when(downstreamDelivery.getRemoteState()).thenReturn(new Rejected());
        deliveryHandler.getValue().handle(downstreamDelivery);

        // then the upstream delivery is settled with the rejected outcome
        verify(upstreamDelivery).disposition(any(Rejected.class), eq(Boolean.TRUE));
    }

    /**
     * Verifies that telemetry data is discarded if no downstream credit is available.
     * 
     * @param ctx The test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testProcessMessageDiscardsMessageIfNoCreditIsAvailable(final TestContext ctx) {

        final UpstreamReceiver client = TestSupport.newClient();
        final ProtonDelivery upstreamDelivery = mock(ProtonDelivery.class);
        when(upstreamDelivery.remotelySettled()).thenReturn(Boolean.FALSE);

        // GIVEN an adapter with a connection to a downstream container
        ProtonSender sender = TestSupport.newMockSender(false);
        when(sender.sendQueueFull()).thenReturn(true);
        final ForwardingEventDownstreamAdapter adapter = new ForwardingEventDownstreamAdapter(vertx, newMockSenderFactory(sender));
        adapter.setMetrics(mock(MessagingMetrics.class));
        adapter.setDownstreamConnectionFactory(TestSupport.newMockConnectionFactory(false));
        adapter.start(Future.future());
        adapter.addSender(client, sender);

        // WHEN processing an event
        final Message msg = ProtonHelper.message(TELEMETRY_MSG_CONTENT);
        MessageHelper.addDeviceId(msg, DEVICE_ID);
        adapter.processMessage(client, upstreamDelivery, msg);

        // THEN the the message is accepted
        verify(upstreamDelivery).disposition(any(Released.class), eq(Boolean.TRUE));
        // but is not delivered to the downstream container
        verify(sender, never()).send(any(Message.class), any(Handler.class));
    }
}
