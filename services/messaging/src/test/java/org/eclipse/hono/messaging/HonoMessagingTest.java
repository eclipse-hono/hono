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
package org.eclipse.hono.messaging;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

import org.apache.qpid.proton.engine.Record;
import org.apache.qpid.proton.engine.impl.RecordImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.service.amqp.AmqpEndpoint;
import org.eclipse.hono.util.Constants;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.proton.ProtonConnection;

/**
 * Unit tests for Messaging Service.
 *
 */
public class HonoMessagingTest {

    private static final String CON_ID = "connection-id";

    private Vertx vertx;
    private EventBus eventBus;

    /**
     * Sets up common mock objects used by the test cases.
     */
    @Before
    public void initMocks() {
        eventBus = mock(EventBus.class);
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
    }

    private HonoMessaging createServer(final AmqpEndpoint telemetryEndpoint) {

        final HonoMessaging server = new HonoMessaging();
        server.setConfig(new HonoMessagingConfigProperties());
        if (telemetryEndpoint != null) {
            server.addEndpoint(telemetryEndpoint);
        }
        server.init(vertx, mock(Context.class));
        return server;
    }

    /**
     * Verifies that Hono Messaging notifies downstream components
     * about an upstream connection being closed.
     */
    @Test
    public void testServerPublishesConnectionIdOnClientDisconnect() {

        // GIVEN a Hono server
        final HonoMessaging server = createServer(null);

        // WHEN the server's publish close event method is called
        final ProtonConnection con = newConnection(Constants.PRINCIPAL_ANONYMOUS);
        server.publishConnectionClosedEvent(con);

        // THEN an appropriate closed event is published on the event bus
        verify(eventBus).publish(Constants.EVENT_BUS_ADDRESS_CONNECTION_CLOSED, CON_ID);
    }

    /**
     * Verifies that a Hono server defines AMQP and AMQPS as it's default ports.
     */
    @Test
    public void verifyHonoDefaultPortNumbers() {

        final HonoMessaging server = createServer(null);
        assertThat(server.getPortDefaultValue(), is(Constants.PORT_AMQPS));
        assertThat(server.getInsecurePortDefaultValue(), is(Constants.PORT_AMQP));
    }

    private static ProtonConnection newConnection(final HonoUser user) {
        final Record attachments = new RecordImpl();
        attachments.set(Constants.KEY_CONNECTION_ID, String.class, CON_ID);
        attachments.set(Constants.KEY_CLIENT_PRINCIPAL, HonoUser.class, user);
        final ProtonConnection con = mock(ProtonConnection.class);
        when(con.attachments()).thenReturn(attachments);
        when(con.getRemoteContainer()).thenReturn("test-client");
        return con;
    }
}
