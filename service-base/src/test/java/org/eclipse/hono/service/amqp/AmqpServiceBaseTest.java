/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.apache.qpid.proton.amqp.transport.Target;
import org.apache.qpid.proton.engine.Record;
import org.apache.qpid.proton.engine.impl.RecordImpl;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.auth.AuthorizationService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;

/**
 * Tests verifying behavior of {@link AmqpServiceBase}.
 */
@RunWith(VertxUnitRunner.class)
public class AmqpServiceBaseTest {

    private static final String CON_ID = "connection-id";
    private static final String ENDPOINT = "anEndpoint";

    /**
     * Time out each test case after five seconds.
     * 
     */
    public final Timeout timeout = Timeout.seconds(5);

    private Vertx vertx;

    /**
     * Sets up common mock objects used by the test cases.
     */
    @Before
    public void initMocks() {
        vertx = mock(Vertx.class);
    }

    private AmqpServiceBase<ServiceConfigProperties> createServer(final AmqpEndpoint amqpEndpoint) {
        return createServer(amqpEndpoint, null);
    }

    private AmqpServiceBase<ServiceConfigProperties> createServer(final AmqpEndpoint amqpEndpoint, final Handler<ProtonConnection> onClientDisconnect) {

        final AmqpServiceBase<ServiceConfigProperties> server = new AmqpServiceBase<ServiceConfigProperties>() {

            @Override
            protected String getServiceName() {
                return "AmqpServiceBase";
            }

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
                setSpecificConfig(configuration);
            }

            @Override
            protected void publishConnectionClosedEvent(final ProtonConnection con) {
                if (onClientDisconnect != null) {
                    onClientDisconnect.handle(con);
                }
            }
        };
        server.setConfig(new ServiceConfigProperties());
        if (amqpEndpoint != null) {
            server.addEndpoint(amqpEndpoint);
        }
        server.init(vertx, mock(Context.class));
        return server;
    }

    /**
     * Verifies that the service notifies a registered endpoint about a client
     * that has established a link.
     */
    @Test
    public void testHandleReceiverOpenForwardsToEndpoint() {

        // GIVEN a server with an endpoint
        final ResourceIdentifier targetAddress = ResourceIdentifier.from(ENDPOINT, Constants.DEFAULT_TENANT, null);
        final AmqpEndpoint endpoint = mock(AmqpEndpoint.class);
        when(endpoint.getName()).thenReturn(ENDPOINT);
        final AuthorizationService authService = mock(AuthorizationService.class);
        when(authService.isAuthorized(Constants.PRINCIPAL_ANONYMOUS, targetAddress, Activity.WRITE))
            .thenReturn(Future.succeededFuture(Boolean.TRUE));
        final AmqpServiceBase<ServiceConfigProperties> server = createServer(endpoint);
        server.setAuthorizationService(authService);

        // WHEN a client connects to the server using this endpoint
        final Target target = getTarget(targetAddress);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteTarget()).thenReturn(target);
        when(receiver.attachments()).thenReturn(mock(Record.class));
        server.handleReceiverOpen(newConnection(Constants.PRINCIPAL_ANONYMOUS), receiver);

        // THEN the server delegates link establishment to the endpoint
        verify(endpoint).onLinkAttach(any(ProtonConnection.class), eq(receiver), eq(targetAddress));
    }

    /**
     * Verifies that the service rejects sender links on resources that
     * the client is not authorized to write to.
     */
    @Test
    public void testHandleReceiverOpenRejectsUnauthorizedClient() {

        // GIVEN a server with a endpoint
        final ResourceIdentifier restrictedTargetAddress = ResourceIdentifier.from(ENDPOINT, "RESTRICTED_TENANT", null);
        final AmqpEndpoint endpoint = mock(AmqpEndpoint.class);
        when(endpoint.getName()).thenReturn(ENDPOINT);
        final AuthorizationService authService = mock(AuthorizationService.class);
        when(authService.isAuthorized(Constants.PRINCIPAL_ANONYMOUS, restrictedTargetAddress, Activity.WRITE))
            .thenReturn(Future.succeededFuture(Boolean.FALSE));
        final AmqpServiceBase<ServiceConfigProperties> server = createServer(endpoint);
        server.setAuthorizationService(authService);

        // WHEN a client connects to the server using a address for a tenant it is not authorized to write to
        final Target target = getTarget(restrictedTargetAddress);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteTarget()).thenReturn(target);
        when(receiver.setCondition(any())).thenReturn(receiver);
        server.handleReceiverOpen(newConnection(Constants.PRINCIPAL_ANONYMOUS), receiver);

        // THEN the server closes the link with the client
        verify(receiver).close();
    }

    /**
     * Verifies that the service invokes the <em>publishConnectionClosedEvent</em>
     * method when a client disconnects.
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testServerCallsPublishEventOnClientDisconnect() {

        // GIVEN a server to which a client is connected
        final Handler<ProtonConnection> publishConnectionClosedEvent = mock(Handler.class);
        final AmqpServiceBase<ServiceConfigProperties> server = createServer(null, publishConnectionClosedEvent);
        final ProtonConnection con = newConnection(Constants.PRINCIPAL_ANONYMOUS);
        server.onRemoteConnectionOpen(con);
        final ArgumentCaptor<Handler> closeHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(con).disconnectHandler(closeHandlerCaptor.capture());

        // WHEN the client disconnects from the service
        closeHandlerCaptor.getValue().handle(con);

        // THEN the publishConnectionClosedEvent method is invoked
        verify(publishConnectionClosedEvent).handle(any(ProtonConnection.class));
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
