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
 */
package org.eclipse.hono.messaging;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.amqp.transport.Target;
import org.apache.qpid.proton.engine.Record;
import org.apache.qpid.proton.engine.impl.RecordImpl;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.messaging.HonoMessaging;
import org.eclipse.hono.messaging.HonoMessagingConfigProperties;
import org.eclipse.hono.service.amqp.BaseEndpoint;
import org.eclipse.hono.service.amqp.Endpoint;
import org.eclipse.hono.service.auth.AuthorizationService;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;

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

    private HonoMessaging createServer(final Endpoint telemetryEndpoint) {

        HonoMessaging server = new HonoMessaging();
        server.setConfig(new HonoMessagingConfigProperties());
        if (telemetryEndpoint != null) {
            server.addEndpoint(telemetryEndpoint);
        }
        server.init(vertx, mock(Context.class));
        return server;
    }

    @Test
    public void testHandleReceiverOpenForwardsToTelemetryEndpoint() throws InterruptedException {

        // GIVEN a server with a telemetry endpoint
        final ResourceIdentifier targetAddress = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, Constants.DEFAULT_TENANT, null);
        final CountDownLatch linkEstablished = new CountDownLatch(1);
        final Endpoint telemetryEndpoint = new BaseEndpoint<ServiceConfigProperties>(vertx) {

            @Override
            public String getName() {
                return TelemetryConstants.TELEMETRY_ENDPOINT;
            }

            @Override
            public void onLinkAttach(final ProtonConnection con, final ProtonReceiver receiver, final ResourceIdentifier targetResource) {
                linkEstablished.countDown();
            }

            @Override
            protected boolean passesFormalVerification(ResourceIdentifier targetAddress, Message message) {
                return true;
            }
        };
        AuthorizationService authService = mock(AuthorizationService.class);
        when(authService.isAuthorized(Constants.PRINCIPAL_ANONYMOUS, targetAddress, Activity.WRITE)).thenReturn(Future.succeededFuture(Boolean.TRUE));
        HonoMessaging server = createServer(telemetryEndpoint);
        server.setAuthorizationService(authService);

        // WHEN a client connects to the server using a telemetry address
        final Target target = getTarget(targetAddress);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteTarget()).thenReturn(target);
        when(receiver.attachments()).thenReturn(mock(Record.class));
        server.handleReceiverOpen(newConnection(Constants.PRINCIPAL_ANONYMOUS), receiver);

        // THEN the server delegates link establishment to the telemetry endpoint 
        assertTrue(linkEstablished.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testHandleReceiverOpenRejectsUnauthorizedClient() throws InterruptedException {

        // GIVEN a server with a telemetry endpoint
        final ResourceIdentifier restrictedTargetAddress = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, "RESTRICTED_TENANT", null);
        final Endpoint telemetryEndpoint = mock(Endpoint.class);
        when(telemetryEndpoint.getName()).thenReturn(TelemetryConstants.TELEMETRY_ENDPOINT);
        AuthorizationService authService = mock(AuthorizationService.class);
        when(authService.isAuthorized(Constants.PRINCIPAL_ANONYMOUS, restrictedTargetAddress, Activity.WRITE)).thenReturn(Future.succeededFuture(Boolean.FALSE));
        HonoMessaging server = createServer(telemetryEndpoint);
        server.setAuthorizationService(authService);

        // WHEN a client connects to the server using a telemetry address for a tenant it is not authorized to write to
        final CountDownLatch linkClosed = new CountDownLatch(1);
        final Target target = getTarget(restrictedTargetAddress);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteTarget()).thenReturn(target);
        when(receiver.setCondition(any())).thenReturn(receiver);
        when(receiver.close()).thenAnswer(invocation -> {
            linkClosed.countDown();
            return receiver;
        });
        server.handleReceiverOpen(newConnection(Constants.PRINCIPAL_ANONYMOUS), receiver);

        // THEN the server closes the link with the client
        assertTrue(linkClosed.await(1, TimeUnit.SECONDS));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testServerPublishesConnectionIdOnClientDisconnect() {

        // GIVEN a Hono server
        HonoMessaging server = createServer(null);

        // WHEN a client connects to the server
        ArgumentCaptor<Handler> closeHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        final ProtonConnection con = newConnection(Constants.PRINCIPAL_ANONYMOUS);
        when(con.disconnectHandler(closeHandlerCaptor.capture())).thenReturn(con);

        server.onRemoteConnectionOpen(con);

        // THEN a handler is registered with the connection that publishes
        // an event on the event bus when the client disconnects
        closeHandlerCaptor.getValue().handle(con);
        verify(eventBus).publish(Constants.EVENT_BUS_ADDRESS_CONNECTION_CLOSED, CON_ID);
    }

    /**
     * Verifies that a Hono server defines AMQP and AMQPS as it's default ports.
     */
    @Test
    public void verifyHonoDefaultPortNumbers() {
        HonoMessaging server = createServer(null);
        assertThat(server.getPortDefaultValue(), is(Constants.PORT_AMQPS));
        assertThat(server.getInsecurePortDefaultValue(), is(Constants.PORT_AMQP));
    }


    private static Target getTarget(final ResourceIdentifier targetAddress) {
        Target result = mock(Target.class);
        when(result.getAddress()).thenReturn(targetAddress.toString());
        return result;
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
