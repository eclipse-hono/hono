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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

import org.apache.qpid.proton.message.Message;
import org.junit.Test;

import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * 
 *
 */
public class ForwardingTelemetryAdapterTest {

    private static final String TELEMETRY_MSG_CONTENT = "hello";
    private static final String TENANT_ID = "myTenant";
    private static final String TELEMETRY_MSG_ADDRESS = "telemetry/myTenant/myDevice";

    @Test
    public void testAdapterForwardsMessageToSender() throws InterruptedException {
        // GIVEN an adapter with a connection to a downstream container
        ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        ForwardingTelemetryAdapter adapter = new ForwardingTelemetryAdapter();
        adapter.addSender(TENANT_ID, sender);

        // WHEN processing a telemetry message
        Message msg = ProtonHelper.message(TELEMETRY_MSG_ADDRESS, TELEMETRY_MSG_CONTENT);
        final CountDownLatch latch = new CountDownLatch(1);
        adapter.processTelemetryData(msg, TENANT_ID, ok -> {
            if (ok) {
                latch.countDown();
            }
        });

        // THEN the message has been delivered to the downstream container
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        verify(sender).send(any(), any());
    }

    @Test
    public void testAdapterDetectsMissingConnectionToDownstreamContainer() throws InterruptedException {
        // GIVEN an adapter with a broken connection to a downstream container
        ForwardingTelemetryAdapter adapter = new ForwardingTelemetryAdapter();

        // WHEN processing a telemetry message
        Message msg = ProtonHelper.message(TELEMETRY_MSG_ADDRESS, TELEMETRY_MSG_CONTENT);
        final CountDownLatch latch = new CountDownLatch(1);
        adapter.processTelemetryData(msg, TENANT_ID, ok -> {
            if (!ok) {
                latch.countDown();
            }
        });

        // THEN the message has not been delivered to the downstream container
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }
}
