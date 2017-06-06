/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
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

package org.eclipse.hono.service.registration;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Tests
 */
@RunWith(MockitoJUnitRunner.class)
public class RegistrationEndpointTest {

    private static final ResourceIdentifier resource = ResourceIdentifier.from(RegistrationConstants.REGISTRATION_ENDPOINT, "tenant", null);

    @Mock private EventBus eventBus;
    @Mock private Vertx    vertx;
    @Mock private ProtonConnection connection;

    private ProtonReceiver receiver;
    private ProtonSender sender;


    private RegistrationEndpoint endpoint;

    @Before
    public void setUp() throws Exception {
        when(vertx.eventBus()).thenReturn(eventBus);
        receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_MOST_ONCE);
        when(receiver.handler(any())).thenReturn(receiver);
        when(receiver.closeHandler(any())).thenReturn(receiver);
        when(receiver.setAutoAccept(any(Boolean.class))).thenReturn(receiver);
        when(receiver.setPrefetch(any(Integer.class))).thenReturn(receiver);
        when(receiver.setQoS(any(ProtonQoS.class))).thenReturn(receiver);
        sender = mock(ProtonSender.class);
        endpoint = new RegistrationEndpoint(vertx);
    }

    @Test
    public void testExpectAtLeastOnceQoS() {

        endpoint.onLinkAttach(connection, receiver, resource);

        verify(receiver).close();
    }

    @Test
    public void testLinkClosedIfReplyAddressIsMissing() {

        endpoint.onLinkAttach(connection, sender, resource);

        verify(sender).setCondition(any());
        verify(sender).close();
    }
}
