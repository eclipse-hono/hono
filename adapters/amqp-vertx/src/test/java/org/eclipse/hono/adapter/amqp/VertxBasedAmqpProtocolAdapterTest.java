/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.adapter.amqp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.engine.Record;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.command.CommandConnection;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonServer;

/**
 * Verifies the behaviour of {@link VertxBasedAmqpProtocolAdapter}.
 */
@RunWith(VertxUnitRunner.class)
public class VertxBasedAmqpProtocolAdapterTest {

    @Rule
    public Timeout globalTimeout = new Timeout(5, TimeUnit.HOURS);

    /**
     * A tenant identifier used for testing.
     */
    private static final String TEST_TENANT_ID = Constants.DEFAULT_TENANT;

    /**
     * A device used for testing.
     */
    private static final String TEST_DEVICE = "test-device";

    private HonoClient tenantServiceClient;
    private HonoClient credentialsServiceClient;
    private HonoClient messagingServiceClient;
    private HonoClient registrationServiceClient;
    private CommandConnection commandConnection;

    private RegistrationClient registrationClient;
    private TenantClient tenantClient;

    private ProtocolAdapterProperties config;

    /**
     * Setups the protocol adapter.
     * 
     * @param context The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setup(final TestContext context) {

        tenantClient = mock(TenantClient.class);

        tenantServiceClient = mock(HonoClient.class);
        when(tenantServiceClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(tenantServiceClient));
        when(tenantServiceClient.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        credentialsServiceClient = mock(HonoClient.class);
        when(credentialsServiceClient.connect(any(Handler.class)))
                .thenReturn(Future.succeededFuture(credentialsServiceClient));

        messagingServiceClient = mock(HonoClient.class);
        when(messagingServiceClient.connect(any(Handler.class)))
                .thenReturn(Future.succeededFuture(messagingServiceClient));

        registrationClient = mock(RegistrationClient.class);
        final JsonObject regAssertion = new JsonObject().put(RegistrationConstants.FIELD_ASSERTION, "assert-token");
        when(registrationClient.assertRegistration(anyString(), any()))
                .thenReturn(Future.succeededFuture(regAssertion));

        registrationServiceClient = mock(HonoClient.class);
        when(registrationServiceClient.connect(any(Handler.class)))
                .thenReturn(Future.succeededFuture(registrationServiceClient));
        when(registrationServiceClient.getOrCreateRegistrationClient(anyString()))
                .thenReturn(Future.succeededFuture(registrationClient));

        commandConnection = mock(CommandConnection.class);
        when(commandConnection.connect(any(Handler.class))).thenReturn(Future.succeededFuture(commandConnection));

        config = new ProtocolAdapterProperties();
        config.setAuthenticationRequired(false);
        config.setInsecurePort(4040);
    }

    /**
     * Verifies that a client provided Proton server instance is used and started by the adapter instead of
     * creating/starting a new one.
     * 
     * @param ctx The test context to use for running asynchronous tests.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testStartUsesClientProvidedAmqpServer(final TestContext ctx) {
        // GIVEN an adapter with a client provided Amqp Server
        final ProtonServer server = getAmqpServer();
        final VertxBasedAmqpProtocolAdapter adapter = getAdapter(server);

        // WHEN starting the adapter
        final Async startup = ctx.async();
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(result -> {
            startup.complete();
        }));
        adapter.start(startupTracker);

        // THEN the client provided server is started
        startup.await();
        verify(server).connectHandler(any(Handler.class));
        verify(server).listen(any(Handler.class));
    }

    /**
     * Verifies that the AMQP Adapter rejects (closes) AMQP links that contains a target address.
     */
    @Test
    public void testAnonymousRelayRequired() {
        // GIVEN an AMQP adapter with a configured server.
        final ProtonServer server = getAmqpServer();
        final VertxBasedAmqpProtocolAdapter adapter = getAdapter(server);

        // WHEN the adapter receives a link that contains a target address
        final ResourceIdentifier targetAddress = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, TEST_DEVICE);
        final ProtonReceiver link = getReceiver(ProtonQoS.AT_LEAST_ONCE, getTarget(targetAddress));

        adapter.handleRemoteReceiverOpen(link, getConnection(null));

        // THEN the adapter closes the link.
        verify(link).close();
    }

    /**
     * Verifies that a request to upload a "settled" telemetry message results in the sender sending the message
     * without waiting for a response from the downstream peer.
     * 
     * AT_MOST_ONCE delivery semantics
     */
    @Test
    public void uploadTelemetryMessageWithSettledDeliverySemantics() {
        // GIVEN an AMQP adapter with a configured server
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        final MessageSender telemetrySender = givenATelemetrySenderForAnyTenant();

        // which is enabled for a tenant
        givenAConfiguredTenant(TEST_TENANT_ID, true);

        // IF a device sends a 'fire and forget' telemetry message
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(true);

        final String to = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, TEST_DEVICE).toString();

        adapter.uploadMessage(new AmqpContext(delivery, getFakeMessage(to), null));

        // THEN the adapter sends the message and does not wait for response from the peer.
        verify(telemetrySender).send(any(Message.class));
    }

    /**
     * Verifies that a request to upload an "unsettled" telemetry message results in the sender sending the
     * message and waits for a response from the downstream peer.
     * 
     * AT_LEAST_ONCE delivery semantics.
     */
    @Test
    public void uploadTelemetryMessageWithUnsettledDeliverySemantics() {
        // GIVEN an adapter configured to use a user-define server.
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        final MessageSender telemetrySender = givenATelemetrySenderForAnyTenant();

        // which is enabled for a tenant
        givenAConfiguredTenant(TEST_TENANT_ID, true);

        // IF a device send telemetry data (with un-settled delivery)
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(false);

        final String to = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, TEST_DEVICE).toString();

        adapter.uploadMessage(new AmqpContext(delivery, getFakeMessage(to), null));

        // THEN the sender sends the message and waits for the outcome from the downstream peer
        verify(telemetrySender).sendAndWaitForOutcome(any(Message.class));
    }

    /**
     * Verifies that a request to upload an "unsettled" telemetry message from a device that belongs to a tenant for which the AMQP
     * adapter is disabled fails and that the device is notified when the message cannot be processed.
     * 
     */
    @Test
    public void testUploadTelemetryMessageFailsForDisabledTenant() {
        // GIVEN an adapter configured to use a user-define server.
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        final MessageSender telemetrySender = givenATelemetrySenderForAnyTenant();

        // AND given a tenant which is ENABLED but DISABLED for the AMQP Adapter
        givenAConfiguredTenant(TEST_TENANT_ID, Boolean.FALSE);

        // WHEN a device uploads telemetry data to the adapter (and wants to be notified of failure)
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(false);
        final String to = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, TEST_DEVICE).toString();

        final AmqpContext context = spy(new AmqpContext(delivery, getFakeMessage(to), null));
        adapter.uploadMessage(context);

        // THEN the adapter does not send the message (regardless of the delivery mode).
        verify(telemetrySender, never()).send(any(Message.class));
        verify(telemetrySender, never()).sendAndWaitForOutcome(any(Message.class));

        // AND notifies the device by sending back a REJECTED disposition
        verify(context).handleFailure(any(ServiceInvocationException.class));
        verify(delivery).disposition(isA(Rejected.class), eq(true));
    }

    private Target getTarget(final ResourceIdentifier resource) {
        final Target target = new Target();
        target.setAddress(resource.toString());
        return target;
    }

    private ProtonReceiver getReceiver(final ProtonQoS qos, final Target target) {
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteTarget()).thenReturn(target);
        when(receiver.getRemoteQoS()).thenReturn(qos);
        return receiver;
    }

    private ProtonConnection getConnection(final Device device) {
        final ProtonConnection conn = mock(ProtonConnection.class);
        final Record record = mock(Record.class);
        when(record.get(anyString(), eq(Device.class))).thenReturn(device);
        when(conn.attachments()).thenReturn(record);
        return conn;
    }

    private void givenAConfiguredTenant(final String tenantId, final boolean enabled) {
        final TenantObject tenantConfig = TenantObject.from(tenantId, Boolean.TRUE);
        tenantConfig
                .addAdapterConfiguration(TenantObject.newAdapterConfig(Constants.PROTOCOL_ADAPTER_TYPE_AMQP, enabled));
        when(tenantClient.get(tenantId)).thenReturn(Future.succeededFuture(tenantConfig));
    }

    private Message getFakeMessage(final String to) {
        final Message message = mock(Message.class);
        when(message.getContentType()).thenReturn("application/text");
        when(message.getBody()).thenReturn(new AmqpValue("some payload"));
        when(message.getAddress()).thenReturn(to);
        return message;
    }

    private MessageSender givenATelemetrySenderForAnyTenant() {
        final MessageSender sender = mock(MessageSender.class);
        when(messagingServiceClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
        return sender;
    }

    /**
     * Gets an AMQP adapter configured to use a given server.
     * 
     * @return The AMQP adapter instance.
     */
    private VertxBasedAmqpProtocolAdapter givenAnAmqpAdapter() {
        final ProtonServer server = getAmqpServer();
        return getAdapter(server);
    }

    /**
     * Creates a protocol adapter for a given AMQP Proton server.
     * 
     * @param server The AMQP Proton server.
     * @return The AMQP adapter instance.
     */
    private VertxBasedAmqpProtocolAdapter getAdapter(final ProtonServer server) {
        final VertxBasedAmqpProtocolAdapter adapter = new VertxBasedAmqpProtocolAdapter();

        adapter.setConfig(config);
        adapter.setInsecureAmqpServer(server);
        adapter.setTenantServiceClient(tenantServiceClient);
        adapter.setHonoMessagingClient(messagingServiceClient);
        adapter.setRegistrationServiceClient(registrationServiceClient);
        adapter.setCredentialsServiceClient(credentialsServiceClient);
        adapter.setCommandConnection(commandConnection);
        return adapter;
    }

    /**
     * Creates and sets up a ProtonServer instance.
     *
     * @return The configured server instance.
     */
    @SuppressWarnings("unchecked")
    private ProtonServer getAmqpServer() {
        final ProtonServer server = mock(ProtonServer.class);
        when(server.actualPort()).thenReturn(0, 4040);
        when(server.connectHandler(any(Handler.class))).thenReturn(server);
        when(server.listen(any(Handler.class))).then(invocation -> {
            final Handler<AsyncResult<ProtonServer>> handler = (Handler<AsyncResult<ProtonServer>>) invocation
                    .getArgument(0);
            handler.handle(Future.succeededFuture(server));
            return server;
        });
        return server;
    }
}
