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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.event.impl.ForwardingEventDownstreamAdapter;
import org.eclipse.hono.messaging.MessagingMetrics;
import org.eclipse.hono.messaging.UpstreamReceiver;
import org.eclipse.hono.util.MessageHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
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
        connectionFactory = newMockConnectionFactory(false);
    }

    /**
     * Verifies that telemetry data uploaded by an upstream client is forwarded to the
     * downstream container.
     * 
     * @param ctx The test context.
     */
    @Test
    public void testProcessMessageForwardsMessageToDownstreamSender(final TestContext ctx) {

        final UpstreamReceiver client = newClient();
        final ProtonDelivery delivery = mock(ProtonDelivery.class);

        // GIVEN an adapter with a connection to a downstream container
        final Async msgSent = ctx.async();
        ProtonSender sender = newMockSender(false);
        when(sender.send(any(Message.class))).then(invocation -> {
            msgSent.complete();
            return null;
        });
        ForwardingTelemetryDownstreamAdapter adapter = new ForwardingTelemetryDownstreamAdapter(vertx, newMockSenderFactory(sender));
        adapter.setMetrics(mock(MessagingMetrics.class));
        adapter.setDownstreamConnectionFactory(connectionFactory);
        adapter.start(Future.future());
        adapter.addSender(client, sender);

        // WHEN processing a telemetry message
        Message msg = ProtonHelper.message(TELEMETRY_MSG_CONTENT);
        MessageHelper.addDeviceId(msg, DEVICE_ID);
        adapter.processMessage(client, delivery, msg);

        // THEN the message has been delivered to the downstream container
        msgSent.await(1000);
    }

    /**
     * Verifies that telemetry data is discarded if no downstream credit is available.
     * 
     * @param ctx The test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testProcessMessageDiscardsMessageIfNoCreditIsAvailable(final TestContext ctx) {

        final UpstreamReceiver client = newClient();
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(Boolean.FALSE);

        // GIVEN an adapter with a connection to a downstream container
        ProtonSender sender = newMockSender(false);
        when(sender.sendQueueFull()).thenReturn(true);
        ForwardingEventDownstreamAdapter adapter = new ForwardingEventDownstreamAdapter(vertx, newMockSenderFactory(sender));
        adapter.setMetrics(mock(MessagingMetrics.class));
        adapter.setDownstreamConnectionFactory(newMockConnectionFactory(false));
        adapter.start(Future.future());
        adapter.addSender(client, sender);

        // WHEN processing an event
        Message msg = ProtonHelper.message(TELEMETRY_MSG_CONTENT);
        MessageHelper.addDeviceId(msg, DEVICE_ID);
        adapter.processMessage(client, delivery, msg);

        // THEN the the message is accepted
        verify(delivery).disposition(any(Accepted.class), eq(Boolean.TRUE));
        // but is not delivered to the downstream container
        verify(sender, never()).send(any(Message.class), any(Handler.class));
    }

}
