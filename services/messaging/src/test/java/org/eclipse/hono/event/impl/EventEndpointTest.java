/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.event.impl;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.messaging.DownstreamAdapter;
import org.eclipse.hono.messaging.ErrorConditions;
import org.eclipse.hono.messaging.HonoMessagingConfigProperties;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;


/**
 * Tests verifying behavior of {@link EventEndpoint}.
 *
 */
public class EventEndpointTest {

    private Vertx vertx;
    private HonoMessagingConfigProperties props;
    private EventEndpoint endpoint;
    private DownstreamAdapter downstreamAdapter;

    /**
     * Sets up mocks.
     */
    @Before
    public void init() {
        vertx = mock(Vertx.class);
        props = new HonoMessagingConfigProperties();
        downstreamAdapter = mock(DownstreamAdapter.class);
        endpoint = new EventEndpoint(vertx);
        endpoint.setConfiguration(props);
        endpoint.setEventAdapter(downstreamAdapter);
    }

    /**
     * Verifies that the endpoint rejects a client's attempt to create a link using <em>AT_MOST_ONCE</em>
     * delivery mode.
     */
    @Test
    public void testOnLinkAttachDisconnectsClientsUsingWrongQos() {

        ProtonConnection con = mock(ProtonConnection.class);
        ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_MOST_ONCE);
        ResourceIdentifier targetAddress = ResourceIdentifier.from("event", "tenant", null);

        endpoint.onLinkAttach(con, receiver, targetAddress);

        ArgumentCaptor<ErrorCondition> errorCondition = ArgumentCaptor.forClass(ErrorCondition.class);
        verify(receiver).setCondition(errorCondition.capture());
        assertThat(errorCondition.getValue(), is(ErrorConditions.ERROR_UNSUPPORTED_DELIVERY_MODE));
        verify(receiver).close();
    }
}
