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
 *
 */

package org.eclipse.hono.event.impl;

import static org.eclipse.hono.TestSupport.CLIENT_ID;
import static org.eclipse.hono.TestSupport.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.server.UpstreamReceiver;
import org.eclipse.hono.util.MessageHelper;
import org.junit.Test;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * Tests {@link ForwardingEventDownstreamAdapter}.
 */
public class ForwardingEventDownstreamAdapterTest {

    public static final Accepted ACCEPTED = new Accepted();
    private static final String EVENT_MSG_CONTENT = "the_event";
    private static final String DEVICE_ID = "theDevice";

    @Test
    public void testProcessMessageForwardsEventMessageToDownstreamSender() throws InterruptedException {

        final Vertx vertx = mock(Vertx.class);
        final UpstreamReceiver client = newClient();
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final ProtonDelivery downstreamDelivery = mock(ProtonDelivery.class);
        when(downstreamDelivery.getRemoteState()).thenReturn(ACCEPTED);
        when(downstreamDelivery.remotelySettled()).thenReturn(true);

        // GIVEN an adapter with a connection to a downstream container
        final CountDownLatch latch = new CountDownLatch(1);
        ProtonSender sender = newMockSender(false);
        when(sender.send(any(Message.class), any(Handler.class))).then(invocation -> {
            latch.countDown();
            invocation.getArgumentAt(1, Handler.class).handle(downstreamDelivery);
            return null;
        });
        ForwardingEventDownstreamAdapter adapter = new ForwardingEventDownstreamAdapter(vertx, newMockSenderFactory(sender));
        adapter.setDownstreamConnectionFactory(newMockConnectionFactory(false));
        adapter.start(Future.future());
        adapter.addSender("CON_ID", CLIENT_ID, sender);

        // WHEN processing an event
        Message msg = ProtonHelper.message(EVENT_MSG_CONTENT);
        MessageHelper.addDeviceId(msg, DEVICE_ID);
        adapter.processMessage(client, delivery, msg);

        // THEN the message has been delivered to the downstream container
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        // and disposition was returned
        verify(delivery).disposition(ACCEPTED, true);
    }
}