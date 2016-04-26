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

    /**
     * 
     */
    private static final String TELEMETRY_MSG_CONTENT = "hello";
    /**
     * 
     */
    private static final String TELEMETRY_MSG_ADDRESS = "telemetry/myTenant";

    @Test
    public void testAdapterForwardsMessageToSender() {
        // GIVEN an adapter with a connection to a downstream container
        ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        ForwardingTelemetryAdapter adapter = new ForwardingTelemetryAdapter();
        adapter.setSender(sender);

        // WHEN processing a telemetry message
        Message msg = ProtonHelper.message(TELEMETRY_MSG_ADDRESS, TELEMETRY_MSG_CONTENT);
        assertTrue(adapter.processTelemetryData(msg));

        // THEN the message has been delivered to the downstream container
        verify(sender).send(any(), any());
    }

    @Test
    public void testAdapterDetectsMissingConnectionToDownstreamContainer() {
        // GIVEN an adapter with a broken connection to a downstream container
        ForwardingTelemetryAdapter adapter = new ForwardingTelemetryAdapter();

        // WHEN processing a telemetry message
        Message msg = ProtonHelper.message(TELEMETRY_MSG_ADDRESS, TELEMETRY_MSG_CONTENT);
        boolean result = adapter.processTelemetryData(msg);

        // THEN the message has not been delivered to the downstream container
        assertFalse(result);
    }
}
