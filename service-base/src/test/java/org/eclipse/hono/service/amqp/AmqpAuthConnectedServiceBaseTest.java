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
 *
 */
package org.eclipse.hono.service.amqp;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.qpid.proton.engine.Record;
import org.apache.qpid.proton.engine.impl.RecordImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.runners.MockitoJUnitRunner;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.proton.ProtonConnection;

/**
 * Tests verifying behavior of {@link AmqpAuthConnectedServiceBase}.
 */
@RunWith(MockitoJUnitRunner.class)
public class AmqpAuthConnectedServiceBaseTest {

    private Vertx vertx;
    private EventBus eventBus;
    private boolean publishCalled = false;

    /**
     * Sets up common mock objects used by the test cases.
     */
    @Before
    public void initMocks() {
        eventBus = mock(EventBus.class);
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
    }

    private AmqpAuthConnectedServiceBase<ServiceConfigProperties> createServer() {
        publishCalled = false;
        AmqpAuthConnectedServiceBase<ServiceConfigProperties> server = new AmqpAuthConnectedServiceBase<ServiceConfigProperties>() {

            @Override
            protected String getServiceName() {
                return "AmqpAuthConnectedServiceBase";
            }
            @Override
            protected void publishConnectionClosedEvent(ProtonConnection con) {
                publishCalled = true;
            }
        };
        server.setConfig(new ServiceConfigProperties());
        server.init(vertx, mock(Context.class));
        return server;
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testServerCallsPublishEvemtOnClientDisconnect() {

        // GIVEN a server
        AmqpAuthConnectedServiceBase server = createServer();

        // WHEN a client connects to the server
        ArgumentCaptor<Handler> closeHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        final ProtonConnection con = newConnection(Constants.PRINCIPAL_ANONYMOUS);
        when(con.disconnectHandler(closeHandlerCaptor.capture())).thenReturn(con);

        server.onRemoteConnectionOpen(con);

        // THEN a handler is registered with the connection that publishes
        // an event on the event bus when the client disconnects
        closeHandlerCaptor.getValue().handle(con);

        assertTrue(publishCalled);
    }

    private static ProtonConnection newConnection(final HonoUser user) {
        final Record attachments = new RecordImpl();
        attachments.set(Constants.KEY_CLIENT_PRINCIPAL, HonoUser.class, user);
        final ProtonConnection con = mock(ProtonConnection.class);
        when(con.attachments()).thenReturn(attachments);
        when(con.getRemoteContainer()).thenReturn("test-client");
        return con;
    }
}
