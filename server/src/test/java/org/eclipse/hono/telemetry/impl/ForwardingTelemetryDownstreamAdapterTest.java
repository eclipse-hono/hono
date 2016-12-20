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

import static org.eclipse.hono.TestSupport.*;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.server.UpstreamReceiver;
import org.eclipse.hono.util.MessageHelper;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
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
    private ConnectionFactory connectionFactory;

    @Before
    public void setup() {
        connectionFactory = newMockConnectionFactory(false);
    }

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
        adapter.setDownstreamConnectionFactory(connectionFactory);
        adapter.start(Future.future());
        adapter.addSender("CON_ID", CLIENT_ID, sender);

        // WHEN processing a telemetry message
        Message msg = ProtonHelper.message(TELEMETRY_MSG_CONTENT);
        MessageHelper.addDeviceId(msg, DEVICE_ID);
        adapter.processMessage(client, delivery, msg);

        // THEN the message has been delivered to the downstream container
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }
}
