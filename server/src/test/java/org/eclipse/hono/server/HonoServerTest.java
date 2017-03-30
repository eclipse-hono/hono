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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Future;
import org.apache.qpid.proton.amqp.transport.Target;
import org.apache.qpid.proton.engine.Record;
import org.apache.qpid.proton.engine.impl.RecordImpl;
import org.eclipse.hono.TestSupport;
import org.eclipse.hono.authorization.AuthorizationConstants;
import org.eclipse.hono.authorization.Permission;
import org.eclipse.hono.config.HonoConfigProperties;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;

/**
 * Unit tests for Hono Server.
 *
 */
public class HonoServerTest {

    private static final String CON_ID = "connection-id";

    private static HonoServer createServer(final Endpoint telemetryEndpoint) {

        HonoServer result = new HonoServer();
        if (telemetryEndpoint != null) {
            result.addEndpoint(telemetryEndpoint);
        }
        return result;
    }

    @Test
    public void testHandleReceiverOpenForwardsToTelemetryEndpoint() throws InterruptedException {

        // GIVEN a server with a telemetry endpoint
        final String targetAddress = TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX + Constants.DEFAULT_TENANT;
        final EventBus eventBus = mock(EventBus.class);
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        final CountDownLatch linkEstablished = new CountDownLatch(1);
        final Endpoint telemetryEndpoint = new BaseEndpoint(vertx) {

            @Override
            public String getName() {
                return TelemetryConstants.TELEMETRY_ENDPOINT;
            }

            @Override
            public void onLinkAttach(final ProtonReceiver receiver, final ResourceIdentifier targetResource) {
                linkEstablished.countDown();
            }
        };
        HonoServer server = createServer(telemetryEndpoint);
        server.init(vertx, mock(Context.class));
        final JsonObject authMsg = AuthorizationConstants.getAuthorizationMsg(Constants.DEFAULT_SUBJECT, targetAddress, Permission.WRITE.toString());
        TestSupport.expectReplyForMessage(eventBus, server.getAuthServiceAddress(), authMsg, AuthorizationConstants.ALLOWED);

        // WHEN a client connects to the server using a telemetry address
        final Target target = getTarget(targetAddress);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteTarget()).thenReturn(target);
        when(receiver.attachments()).thenReturn(mock(Record.class));
        server.handleReceiverOpen(newAuthenticatedConnection(Constants.DEFAULT_SUBJECT), receiver);

        // THEN the server delegates link establishment to the telemetry endpoint 
        assertTrue(linkEstablished.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testHandleReceiverOpenRejectsUnauthorizedClient() throws InterruptedException {

        final String UNAUTHORIZED_SUBJECT = "unauthorized";
        // GIVEN a server with a telemetry endpoint
        final String restrictedTargetAddress = TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX + "RESTRICTED_TENANT";
        final EventBus eventBus = mock(EventBus.class);
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);

        final Endpoint telemetryEndpoint = mock(Endpoint.class);
        when(telemetryEndpoint.getName()).thenReturn(TelemetryConstants.TELEMETRY_ENDPOINT);
        HonoServer server = createServer(telemetryEndpoint);
        server.init(vertx, mock(Context.class));
        final JsonObject authMsg = AuthorizationConstants.getAuthorizationMsg(UNAUTHORIZED_SUBJECT, restrictedTargetAddress, Permission.WRITE.toString());
        TestSupport.expectReplyForMessage(eventBus, server.getAuthServiceAddress(), authMsg, AuthorizationConstants.DENIED);

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
        server.handleReceiverOpen(newAuthenticatedConnection(UNAUTHORIZED_SUBJECT), receiver);

        // THEN the server closes the link with the client
        assertTrue(linkClosed.await(1, TimeUnit.SECONDS));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testServerPublishesConnectionIdOnClientDisconnect() {

        // GIVEN a Hono server
        final EventBus eventBus = mock(EventBus.class);
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        HonoServer server = createServer(null);
        server.init(vertx, mock(Context.class));

        // WHEN a client connects to the server
        ArgumentCaptor<Handler> closeHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        final ProtonConnection con = newAuthenticatedConnection(Constants.DEFAULT_SUBJECT);
        when(con.disconnectHandler(closeHandlerCaptor.capture())).thenReturn(con);

        server.handleRemoteConnectionOpen(con);

        // THEN a handler is registered with the connection that publishes
        // an event on the event bus when the client disconnects
        closeHandlerCaptor.getValue().handle(con);
        verify(eventBus).publish(Constants.EVENT_BUS_ADDRESS_CONNECTION_CLOSED, CON_ID);
    }

    @Test
    public void testIanaPortConfigurations() throws InterruptedException {

        // GIVEN a server with a telemetry endpoint
        final EventBus eventBus = mock(EventBus.class);
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);

        checkSecurePortAutoSelect(vertx);
        checkSecurePortExplicitlySet(vertx);

        checkNoPortsSet(vertx);

        checkInsecureOnlyPort(vertx);
        checkInsecureOnlyPortExplicitlySet(vertx);

        checkBothPortsOpen(vertx);
        checkBothPortsSetToSame(vertx);
    }

    private void checkSecurePortAutoSelect(Vertx vertx) {
        // secure port config: no port set -> secure IANA port selected
        HonoServer server = createServer(null);
        server.init(vertx, mock(Context.class));


        Future<Void> portConfigurationTracker;HonoConfigProperties configProperties = new HonoConfigProperties();
        configProperties.setKeyStorePath("/etc/hono/certs/honoKeyStore.p12");
        server.setHonoConfiguration(configProperties);

        portConfigurationTracker = Future.future();
        server.determinePortConfigurations(portConfigurationTracker);

        assertTrue(portConfigurationTracker.succeeded());
        assertTrue(server.isOpenSecurePort());
        assertTrue(server.getPort() == Constants.PORT_AMQP);
        assertFalse(server.isOpenInsecurePort());
    }

    private void checkSecurePortExplicitlySet(Vertx vertx) {

        // secure port config: explicit port set -> port used
        HonoServer server = createServer(null);
        server.init(vertx, mock(Context.class));

        Future<Void> portConfigurationTracker;
        HonoConfigProperties configProperties = new HonoConfigProperties();
        configProperties.setKeyStorePath("/etc/hono/certs/honoKeyStore.p12");
        configProperties.setPort(8989);
        server.setHonoConfiguration(configProperties);

        portConfigurationTracker = Future.future();
        server.determinePortConfigurations(portConfigurationTracker);

        assertTrue(portConfigurationTracker.succeeded());
        assertTrue(server.isOpenSecurePort());
        assertTrue(server.getPort() == 8989);
        assertFalse(server.isOpenInsecurePort());
    }

    private void checkNoPortsSet(Vertx vertx) {

        // secure and insecure port config: nothing set
        HonoServer server = createServer(null);
        server.init(vertx, mock(Context.class));

        Future<Void> portConfigurationTracker;HonoConfigProperties configProperties = new HonoConfigProperties();
        server.setHonoConfiguration(configProperties);

        portConfigurationTracker = Future.future();
        server.determinePortConfigurations(portConfigurationTracker);

        assertTrue(portConfigurationTracker.failed());
    }

    private void checkInsecureOnlyPort(Vertx vertx) {

        // insecure port config
        HonoServer server = createServer(null);
        server.init(vertx, mock(Context.class));

        HonoConfigProperties configProperties = new HonoConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        server.setHonoConfiguration(configProperties);

        Future<Void> portConfigurationTracker = Future.future();
        server.determinePortConfigurations(portConfigurationTracker);

        assertTrue(portConfigurationTracker.succeeded());
        assertFalse(server.isOpenSecurePort());
        assertTrue(server.isOpenInsecurePort());
        assertTrue(server.getInsecurePort() == Constants.PORT_AMQP_INSECURE);
    }

    private void checkInsecureOnlyPortExplicitlySet(Vertx vertx) {

        // insecure port config
        HonoServer server = createServer(null);
        server.init(vertx, mock(Context.class));

        HonoConfigProperties configProperties = new HonoConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setInsecurePort(8888);
        server.setHonoConfiguration(configProperties);

        Future<Void> portConfigurationTracker = Future.future();
        server.determinePortConfigurations(portConfigurationTracker);

        assertTrue(portConfigurationTracker.succeeded());
        assertFalse(server.isOpenSecurePort());
        assertTrue(server.isOpenInsecurePort());
        assertTrue(server.getInsecurePort() == 8888);
    }


    private void checkBothPortsOpen(Vertx vertx)
    {

        HonoServer server = createServer(null);
        server.init(vertx, mock(Context.class));

        HonoConfigProperties configProperties = new HonoConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setKeyStorePath("/etc/hono/certs/honoKeyStore.p12");
        server.setHonoConfiguration(configProperties);

        Future<Void> portConfigurationTracker = Future.future();
        server.determinePortConfigurations(portConfigurationTracker);

        assertTrue(portConfigurationTracker.succeeded());
        assertTrue(server.isOpenSecurePort());
        assertTrue(server.getPort() == Constants.PORT_AMQP);
        assertTrue(server.isOpenInsecurePort());
        assertTrue(server.getInsecurePort() == Constants.PORT_AMQP_INSECURE);
    }

    private void checkBothPortsSetToSame(Vertx vertx)
    {

        HonoServer server = createServer(null);
        server.init(vertx, mock(Context.class));

        HonoConfigProperties configProperties = new HonoConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setKeyStorePath("/etc/hono/certs/honoKeyStore.p12");
        configProperties.setInsecurePort(8888);
        configProperties.setPort(8888);
        server.setHonoConfiguration(configProperties);

        Future<Void> portConfigurationTracker = Future.future();
        server.determinePortConfigurations(portConfigurationTracker);

        assertTrue(portConfigurationTracker.failed());
    }

    private static Target getTarget(final String targetAddress) {
        Target result = mock(Target.class);
        when(result.getAddress()).thenReturn(targetAddress);
        return result;
    }

    private static ProtonConnection newAuthenticatedConnection(final String name) {
        final Record attachments = new RecordImpl();
        attachments.set(Constants.KEY_CONNECTION_ID, String.class, CON_ID);
        attachments.set(Constants.KEY_CLIENT_PRINCIPAL, Principal.class, new Principal() {

            @Override
            public String getName() {
                return name;
            }
        });
        final ProtonConnection con = mock(ProtonConnection.class);
        when(con.attachments()).thenReturn(attachments);
        when(con.getRemoteContainer()).thenReturn("test-client");
        return con;
    }
}
