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
 *
 */

package org.eclipse.hono.event.impl;

import static org.eclipse.hono.TestSupport.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.message.Message;
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
 * Tests {@link ForwardingEventDownstreamAdapter}.
 */
@RunWith(VertxUnitRunner.class)
public class ForwardingEventDownstreamAdapterTest {

    private static final Accepted ACCEPTED = new Accepted();
    private static final String EVENT_MSG_CONTENT = "the_event";
    private static final String DEVICE_ID = "theDevice";

    private Vertx vertx;

    /**
     * Sets up mocks.
     * 
     */
    @Before
    public void init() {
        vertx = mock(Vertx.class);
    }

    /**
     * Verifies that an event uploaded by an upstream client is forwarded to the
     * downstream container.
     * 
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testProcessMessageForwardsMessageToDownstreamSender(final TestContext ctx) {

        final UpstreamReceiver client = newClient();
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final ProtonDelivery downstreamDelivery = mock(ProtonDelivery.class);
        when(downstreamDelivery.getRemoteState()).thenReturn(ACCEPTED);
        when(downstreamDelivery.remotelySettled()).thenReturn(true);

        // GIVEN an adapter with a connection to a downstream container
        final Async msgSent = ctx.async();
        ProtonSender sender = newMockSender(false);
        when(sender.send(any(Message.class), any(Handler.class))).then(invocation -> {
            msgSent.complete();
            final Handler handler = invocation.getArgument(1);
            handler.handle(downstreamDelivery);
            return null;
        });
        ForwardingEventDownstreamAdapter adapter = new ForwardingEventDownstreamAdapter(vertx, newMockSenderFactory(sender));
        adapter.setMetrics(mock(MessagingMetrics.class));
        adapter.setDownstreamConnectionFactory(newMockConnectionFactory(false));
        adapter.start(Future.future());
        adapter.addSender(client, sender);

        // WHEN processing an event
        Message msg = ProtonHelper.message(EVENT_MSG_CONTENT);
        MessageHelper.addDeviceId(msg, DEVICE_ID);
        adapter.processMessage(client, delivery, msg);

        // THEN the message has been delivered to the downstream container
        msgSent.await(1000);
        // and disposition was returned
        verify(delivery).disposition(any(Accepted.class), eq(Boolean.TRUE));
    }

    /**
     * Verifies that an event is released if no downstream credit is available.
     * 
     * @param ctx The test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testProcessMessageReleasesMessageIfNoCreditIsAvailable(final TestContext ctx) {

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
        Message msg = ProtonHelper.message(EVENT_MSG_CONTENT);
        MessageHelper.addDeviceId(msg, DEVICE_ID);
        adapter.processMessage(client, delivery, msg);

        // THEN the message is released
        verify(delivery).disposition(any(Released.class), eq(Boolean.TRUE));
        // and not delivered to the downstream container
        verify(sender, never()).send(any(Message.class), any(Handler.class));
    }
}