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

package org.eclipse.hono.registration.impl;

import static org.eclipse.hono.registration.RegistrationConstants.REGISTRATION_ENDPOINT;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.qpid.proton.amqp.transport.Source;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Tests
 */
@RunWith(MockitoJUnitRunner.class)
public class RegistrationEndpointTest {

    private static final ResourceIdentifier resource = ResourceIdentifier.from(REGISTRATION_ENDPOINT, "tenant", null);

    @Mock private EventBus eventBus;
    @Mock private Vertx    vertx;
    @Mock private Source   source;

    private ProtonReceiver receiver;
    private ProtonSender sender;


    private RegistrationEndpoint endpoint;

    @Before
    public void setUp() throws Exception {
        when(vertx.eventBus()).thenReturn(eventBus);
        receiver = mock(ProtonReceiver.class);
        sender = mock(ProtonSender.class);
        endpoint = new RegistrationEndpoint(vertx, false);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testExpectAtLeastOnceQoS() {
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_MOST_ONCE);
        when(receiver.handler(any())).thenReturn(receiver);
        when(receiver.closeHandler(any())).thenReturn(receiver);

        endpoint.onLinkAttach(receiver, resource);

        verify(receiver).close();
    }

    @Test
    public void testLinkClosedIfReplyAddressIsMissing() {

        endpoint.onLinkAttach(sender, resource);

        verify(sender).setCondition(any());
        verify(sender).close();
    }
}
