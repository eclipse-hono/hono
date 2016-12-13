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
import static org.mockito.Mockito.*;

import org.apache.qpid.proton.message.Message;
import org.junit.Test;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * Verifies standard behavior of {@code ForwardingDownstreamAdapter}.
 */
public class ForwardingDownstreamAdapterTest {

    private ForwardingDownstreamAdapter adapter;

    @Test
    public void testOnClientDisconnectClosesDownstreamSenders() {

        final String upstreamConnection = "upstream-connection-id";
        final String linkId = "link-id";
        final ProtonSender downstreamSender = newMockSender(false);

        givenADownstreamAdapter();
        adapter.addSender(upstreamConnection, linkId, downstreamSender);

        // WHEN the upstream client disconnects
        adapter.onClientDisconnect(upstreamConnection);

        // THEN the downstream sender is closed and removed from the sender list
        verify(downstreamSender).close();
    }

    private void givenADownstreamAdapter() {

        Vertx vertx = mock(Vertx.class);
        SenderFactory senderFactory = mock(SenderFactory.class);
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
