/*******************************************************************************
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.amqp.impl;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.Source;
import org.apache.qpid.proton.engine.Record;
import org.apache.qpid.proton.engine.impl.RecordImpl;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.amqp.AmqpAdapterConstants;
import org.eclipse.hono.adapter.amqp.AmqpAdapterMetrics;
import org.eclipse.hono.adapter.amqp.AmqpAdapterProperties;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandConsumerFactory;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.DownstreamSenderFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.service.metric.MetricsTags.QoS;
import org.eclipse.hono.service.plan.ResourceLimitChecks;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonServer;

/**
 * Verifies the behavior of {@link VertxBasedAmqpProtocolAdapter}.
 */
@RunWith(VertxUnitRunner.class)
public class VertxBasedAmqpProtocolAdapterTest {

    /**
     * A tenant identifier used for testing.
     */
    private static final String TEST_TENANT_ID = Constants.DEFAULT_TENANT;
    /**
     * A device used for testing.
     */
    private static final String TEST_DEVICE = "test-device";

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(6, TimeUnit.SECONDS);

    private TenantClientFactory tenantClientFactory;
    private CredentialsClientFactory credentialsClientFactory;
    private DownstreamSenderFactory downstreamSenderFactory;
    private RegistrationClientFactory registrationClientFactory;
    private CommandConsumerFactory commandConsumerFactory;

    private RegistrationClient registrationClient;
    private TenantClient tenantClient;

    private AmqpAdapterProperties config;
    private AmqpAdapterMetrics metrics;
    private ResourceLimitChecks resourceLimitChecks;

    /**
     * Setups the protocol adapter.
     * 
     * @param context The vert.x test context.
     */
    @Before
    public void setup(final TestContext context) {

        metrics = mock(AmqpAdapterMetrics.class);

        tenantClient = mock(TenantClient.class);
        when(tenantClient.get(anyString(), (SpanContext) any())).thenAnswer(invocation -> {
           return Future.succeededFuture(TenantObject.from(invocation.getArgument(0), true));
        });

        tenantClientFactory = mock(TenantClientFactory.class);
        when(tenantClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(tenantClientFactory.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        credentialsClientFactory = mock(CredentialsClientFactory.class);
        when(credentialsClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));

        downstreamSenderFactory = mock(DownstreamSenderFactory.class);
        when(downstreamSenderFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));

        registrationClient = mock(RegistrationClient.class);
        final JsonObject regAssertion = new JsonObject();
        when(registrationClient.assertRegistration(anyString(), any(), (SpanContext) any()))
                .thenReturn(Future.succeededFuture(regAssertion));

        registrationClientFactory = mock(RegistrationClientFactory.class);
        when(registrationClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(registrationClientFactory.getOrCreateRegistrationClient(anyString()))
                .thenReturn(Future.succeededFuture(registrationClient));

        commandConsumerFactory = mock(CommandConsumerFactory.class);
        when(commandConsumerFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));

        resourceLimitChecks = mock(ResourceLimitChecks.class);
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong()))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));

        config = new AmqpAdapterProperties();
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
    public void testAdapterAcceptsAnonymousRelayReceiverOnly() {
        // GIVEN an AMQP adapter with a configured server.
        final ProtonServer server = getAmqpServer();
        final VertxBasedAmqpProtocolAdapter adapter = getAdapter(server);

        // WHEN the adapter receives a link that contains a target address
        final ResourceIdentifier targetAddress = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, TEST_DEVICE);
        final ProtonReceiver link = getReceiver(ProtonQoS.AT_LEAST_ONCE, getTarget(targetAddress));

        adapter.handleRemoteReceiverOpen(getConnection(null), link);

        // THEN the adapter closes the link.
        verify(link).close();
    }

    /**
     * Verifies that a request to upload a pre-settled telemetry message results
     * in the downstream sender not waiting for the consumer's acknowledgment.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadTelemetryWithAtMostOnceDeliverySemantics(final TestContext ctx) {
        // GIVEN an AMQP adapter with a configured server
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        final DownstreamSender telemetrySender = givenATelemetrySenderForAnyTenant();
        when(telemetrySender.send(any(Message.class), (SpanContext) any())).thenReturn(Future.succeededFuture(mock(ProtonDelivery.class)));

        // which is enabled for a tenant
        givenAConfiguredTenant(TEST_TENANT_ID, true);

        // IF a device sends a 'fire and forget' telemetry message
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(true);
        final Buffer payload = Buffer.buffer("payload");
        final String to = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, TEST_DEVICE).toString();

        adapter.onMessageReceived(AmqpContext.fromMessage(delivery, getFakeMessage(to, payload), null)).setHandler(ctx.asyncAssertSuccess(d -> {
            // THEN the adapter has forwarded the message downstream
            verify(telemetrySender).send(any(Message.class), (SpanContext) any());
            // and acknowledged the message to the device
            verify(delivery).disposition(any(Accepted.class), eq(true));
            // and has reported the telemetry message
            verify(metrics).reportTelemetry(
                    eq(EndpointType.TELEMETRY),
                    eq(TEST_TENANT_ID),
                    eq(ProcessingOutcome.FORWARDED),
                    eq(QoS.AT_MOST_ONCE),
                    eq(payload.length()),
                    any());
        }));
    }

    /**
     * Verifies that a request to upload an "unsettled" telemetry message results in the sender sending the
     * message and waits for a response from the downstream peer.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadTelemetryWithAtLeastOnceDeliverySemantics(final TestContext ctx) {
        // GIVEN an adapter configured to use a user-define server.
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        final DownstreamSender telemetrySender = givenATelemetrySenderForAnyTenant();
        final Future<ProtonDelivery> downstreamDelivery = Future.future();
        when(telemetrySender.sendAndWaitForOutcome(any(Message.class), (SpanContext) any())).thenReturn(downstreamDelivery);

        // which is enabled for a tenant
        givenAConfiguredTenant(TEST_TENANT_ID, true);

        // IF a device send telemetry data (with un-settled delivery)
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(false);
        final Buffer payload = Buffer.buffer("payload");
        final String to = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT_SHORT, TEST_TENANT_ID, TEST_DEVICE).toString();
        final Message mockMessage = getFakeMessage(to, payload);

        adapter.onMessageReceived(AmqpContext.fromMessage(delivery, mockMessage, null));

        // THEN the sender sends the message
        verify(telemetrySender).sendAndWaitForOutcome(any(Message.class), (SpanContext) any());
        // using the canonical endpoint name
        verify(mockMessage).setAddress(TelemetryConstants.TELEMETRY_ENDPOINT + "/" + TEST_TENANT_ID);
        //  and waits for the outcome from the downstream peer
        verify(delivery, never()).disposition(any(DeliveryState.class), anyBoolean());
        // until the transfer is settled
        downstreamDelivery.complete(mock(ProtonDelivery.class));
        verify(delivery).disposition(any(Accepted.class), eq(true));
        // and has reported the telemetry message
        verify(metrics).reportTelemetry(
                eq(EndpointType.TELEMETRY),
                eq(TEST_TENANT_ID),
                eq(ProcessingOutcome.FORWARDED),
                eq(QoS.AT_LEAST_ONCE),
                eq(payload.length()),
                any());
    }

    /**
     * Verifies that a request to upload an "unsettled" telemetry message from a device that belongs to a tenant for which the AMQP
     * adapter is disabled fails and that the device is notified when the message cannot be processed.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadTelemetryMessageFailsForDisabledAdapter(final TestContext ctx) {

        // GIVEN an adapter configured to use a user-define server.
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        final DownstreamSender telemetrySender = givenATelemetrySenderForAnyTenant();

        // AND given a tenant for which the AMQP Adapter is disabled
        givenAConfiguredTenant(TEST_TENANT_ID, false);

        // WHEN a device uploads telemetry data to the adapter (and wants to be notified of failure)
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(false); // AT LEAST ONCE
        final String to = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, TEST_DEVICE).toString();
        final Buffer payload = Buffer.buffer("some payload");

        adapter.onMessageReceived(AmqpContext.fromMessage(delivery, getFakeMessage(to, payload), null)).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the adapter does not send the message (regardless of the delivery mode).
            verify(telemetrySender, never()).send(any(Message.class), (SpanContext) any());
            verify(telemetrySender, never()).sendAndWaitForOutcome(any(Message.class), (SpanContext) any());

            // AND notifies the device by sending back a REJECTED disposition
            verify(delivery).disposition(any(Rejected.class), eq(true));

            // AND has reported the message as unprocessable
            verify(metrics).reportTelemetry(
                    eq(EndpointType.TELEMETRY),
                    eq(TEST_TENANT_ID),
                    eq(ProcessingOutcome.UNPROCESSABLE),
                    eq(QoS.AT_LEAST_ONCE),
                    eq(payload.length()),
                    any());
        }));
    }

    /**
     * Verifies that a request from a gateway to upload an event on behalf of a device that belongs
     * to another tenant than the gateway fails.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadEventFailsForGatewayOfDifferentTenant(final TestContext ctx) {

        // GIVEN an adapter
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        final DownstreamSender eventSender = givenAnEventSender(Future.future());

        // with an enabled tenant
        givenAConfiguredTenant(TEST_TENANT_ID, true);

        // WHEN a gateway uploads an event on behalf of a device of another tenant
        final Device gateway = new Device(TEST_TENANT_ID, "gw");
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(false); // AT LEAST ONCE
        final String to = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, "other-tenant", TEST_DEVICE).toString();
        final Buffer payload = Buffer.buffer("some payload");

        adapter.onMessageReceived(AmqpContext.fromMessage(delivery, getFakeMessage(to, payload), gateway)).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the adapter does not send the event
            verify(eventSender, never()).send(any(Message.class), (SpanContext) any());
            verify(eventSender, never()).sendAndWaitForOutcome(any(Message.class), (SpanContext) any());

            // AND notifies the device by sending back a REJECTED disposition
            verify(delivery).disposition(any(Rejected.class), eq(true));
        }));
    }

    /**
     * Verifies that if a client device opens a receiver link to receive commands, then the AMQP adapter opens a link
     * for sending commands to the device and notifies the downstream application by sending an
     * <em>EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION</em> event a with TTD -1. An unauthenticated device is used in
     * this test setup to simulate the client device.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testAdapterOpensSenderLinkAndNotifyDownstreamApplication() {
        // GIVEN an AMQP adapter configured to use a user-defined server
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        final Future<ProtonDelivery> outcome = Future.future();
        final DownstreamSender eventSender = givenAnEventSender(outcome);

        // WHEN an unauthenticated device opens a receiver link with a valid source address
        final ProtonConnection deviceConnection = mock(ProtonConnection.class);
        when(deviceConnection.attachments()).thenReturn(mock(Record.class));
        when(commandConsumerFactory.createCommandConsumer(eq(TEST_TENANT_ID), eq(TEST_DEVICE), any(Handler.class), any(Handler.class), anyLong()))
            .thenReturn(Future.succeededFuture(mock(MessageConsumer.class)));
        final String sourceAddress = String.format("%s/%s/%s", getCommandEndpoint(), TEST_TENANT_ID, TEST_DEVICE);
        final ProtonSender sender = getSender(sourceAddress);

        adapter.handleRemoteSenderOpenForCommands(deviceConnection, sender);

        // THEN the adapter opens the link upon success
        verify(sender).open();

        // AND sends an empty notification downstream (with a TTD of -1)
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(eventSender).sendAndWaitForOutcome(messageCaptor.capture(), any());
        assertThat(messageCaptor.getValue().getContentType(), equalTo(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
        assertThat(MessageHelper.getTimeUntilDisconnect(messageCaptor.getValue()), equalTo(-1));
    }

    /**
     * Verify that if a client device closes the link for receiving commands, then the AMQP
     * adapter sends an empty notification downstream with TTD 0 and closes the command
     * consumer.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testAdapterClosesCommandConsumerWhenDeviceClosesReceiverLink() {

        // GIVEN an AMQP adapter
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        final Future<ProtonDelivery> outcome = Future.future();
        final DownstreamSender eventSender = givenAnEventSender(outcome);

        // and a device that wants to receive commands
        final MessageConsumer commandConsumer = mock(MessageConsumer.class);
        when(commandConsumerFactory.createCommandConsumer(eq(TEST_TENANT_ID), eq(TEST_DEVICE), any(Handler.class), any(Handler.class), anyLong()))
            .thenReturn(Future.succeededFuture(commandConsumer));
        final String sourceAddress = String.format("%s", getCommandEndpoint());
        final ProtonSender sender = getSender(sourceAddress);
        final Device authenticatedDevice = new Device(TEST_TENANT_ID, TEST_DEVICE);
        final ProtonConnection deviceConnection = mock(ProtonConnection.class);
        final Record attachments = mock(Record.class);
        when(attachments.get(AmqpAdapterConstants.KEY_CLIENT_DEVICE, Device.class)).thenReturn(authenticatedDevice);
        when(deviceConnection.attachments()).thenReturn(attachments);
        adapter.handleRemoteSenderOpenForCommands(deviceConnection, sender);

        // WHEN the client device closes its receiver link (unsubscribe)
        final ArgumentCaptor<Handler<AsyncResult<ProtonSender>>> closeHookCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(sender).closeHandler(closeHookCaptor.capture());
        closeHookCaptor.getValue().handle(null);

        // THEN the adapter closes the command consumer
        verify(commandConsumer).close(any());

        // AND sends an empty notification downstream
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(eventSender, times(2)).sendAndWaitForOutcome(messageCaptor.capture(), any());
        assertThat(messageCaptor.getValue().getContentType(), equalTo(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
        assertThat(MessageHelper.getTimeUntilDisconnect(messageCaptor.getValue()), equalTo(0));
    }

    /**
     * Verifies that the adapter closes a corresponding command consumer if
     * the device closes the connection to the adapter.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAdapterClosesCommandConsumerWhenDeviceClosesConnection(final TestContext ctx) {

        final Handler<ProtonConnection> trigger = deviceConnection -> {
            @SuppressWarnings("unchecked")
            final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> closeHandler = ArgumentCaptor.forClass(Handler.class);
            verify(deviceConnection).closeHandler(closeHandler.capture());
            closeHandler.getValue().handle(Future.succeededFuture(deviceConnection));
        };
        testAdapterClosesCommandConsumer(ctx, trigger);
    }

    /**
     * Verifies that the adapter closes a corresponding command consumer if
     * the connection to a device fails unexpectedly.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAdapterClosesCommandConsumerWhenConnectionToDeviceIsLost(final TestContext ctx) {

        final Handler<ProtonConnection> trigger = deviceConnection -> {
            @SuppressWarnings("unchecked")
            final ArgumentCaptor<Handler<ProtonConnection>> disconnectHandler = ArgumentCaptor.forClass(Handler.class);
            verify(deviceConnection).disconnectHandler(disconnectHandler.capture());
            disconnectHandler.getValue().handle(deviceConnection);
        };
        testAdapterClosesCommandConsumer(ctx, trigger);
    }

    /**
     * Verifies that the adapter closes a corresponding command consumer if
     * the connection to a device fails unexpectedly.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    private void testAdapterClosesCommandConsumer(final TestContext ctx, final Handler<ProtonConnection> connectionLossTrigger) {

        // GIVEN an AMQP adapter
        final DownstreamSender downstreamEventSender = givenAnEventSender(Future.succeededFuture());
        final ProtonServer server = getAmqpServer();
        final VertxBasedAmqpProtocolAdapter adapter = getAdapter(server);

        final Async startup = ctx.async();
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(ok -> {
            startup.complete();
        }));
        adapter.start(startupTracker);
        startup.await();

        // to which a device is connected
        final Device authenticatedDevice = new Device(TEST_TENANT_ID, TEST_DEVICE);
        final Record record = new RecordImpl();
        record.set(AmqpAdapterConstants.KEY_CLIENT_DEVICE, Device.class, authenticatedDevice);
        final ProtonConnection deviceConnection = mock(ProtonConnection.class);
        when(deviceConnection.attachments()).thenReturn(record);
        final ArgumentCaptor<Handler<ProtonConnection>> connectHandler = ArgumentCaptor.forClass(Handler.class);
        verify(server).connectHandler(connectHandler.capture());
        connectHandler.getValue().handle(deviceConnection);

        // that wants to receive commands
        final MessageConsumer commandConsumer = mock(MessageConsumer.class);
        when(commandConsumerFactory.createCommandConsumer(eq(TEST_TENANT_ID), eq(TEST_DEVICE), any(Handler.class), any(Handler.class), anyLong()))
            .thenReturn(Future.succeededFuture(commandConsumer));
        final String sourceAddress = getCommandEndpoint();
        final ProtonSender sender = getSender(sourceAddress);

        adapter.handleRemoteSenderOpenForCommands(deviceConnection, sender);

        // WHEN the connection to the device is lost
        connectionLossTrigger.handle(deviceConnection);

        // THEN the adapter closes the command consumer
        verify(commandConsumer).close(any());
        // and sends an empty event with TTD = 0 downstream
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(downstreamEventSender, times(2)).sendAndWaitForOutcome(messageCaptor.capture(), any());
        assertThat(messageCaptor.getValue().getContentType(), equalTo(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
        assertThat(MessageHelper.getTimeUntilDisconnect(messageCaptor.getValue()), equalTo(0));
    }

    /**
     * Verify that the AMQP adapter forwards command responses downstream.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadCommandResponseSucceeds(final TestContext ctx) {

        // GIVEN an AMQP adapter
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        final CommandResponseSender responseSender = givenACommandResponseSenderForAnyTenant();
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(responseSender.sendCommandResponse(any(CommandResponse.class), (SpanContext) any())).thenReturn(Future.succeededFuture(delivery));
        // which is enabled for the test tenant
        givenAConfiguredTenant(TEST_TENANT_ID, true);

        // WHEN an unauthenticated device publishes a command response
        final String replyToAddress = String.format("%s/%s/%s", getCommandEndpoint(), TEST_TENANT_ID,
                Command.getDeviceFacingReplyToId("test-reply-id", TEST_DEVICE, false));

        final Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(MessageHelper.APP_PROPERTY_STATUS, 200);
        final ApplicationProperties props = new ApplicationProperties(propertyMap);
        final Buffer payload = Buffer.buffer("some payload");
        final Message message = getFakeMessage(replyToAddress, payload);
        when(message.getCorrelationId()).thenReturn("correlation-id");
        when(message.getApplicationProperties()).thenReturn(props);

        adapter.onMessageReceived(AmqpContext.fromMessage(delivery, message, null)).setHandler(ctx.asyncAssertSuccess(ok -> {
            // THEN the adapter forwards the command response message downstream
            verify(responseSender).sendCommandResponse((CommandResponse) any(), (SpanContext) any());
            // and reports the forwarded message
            verify(metrics).reportCommand(
                eq(Direction.RESPONSE),
                eq(TEST_TENANT_ID),
                eq(ProcessingOutcome.FORWARDED),
                eq(payload.length()),
                any());
        }));
    }

    /**
     * Verifies that the adapter signals successful forwarding of a one-way command
     * back to the sender of the command.
     */
    @Test
    public void testOneWayCommandAccepted() {

        final ProtonDelivery successfulDelivery = mock(ProtonDelivery.class);
        when(successfulDelivery.remotelySettled()).thenReturn(true);
        when(successfulDelivery.getRemoteState()).thenReturn(new Accepted());
        testOneWayCommandOutcome(successfulDelivery, Accepted.class, ProcessingOutcome.FORWARDED);
    }

    /**
     * Verifies that the adapter signals a rejected one-way command
     * back to the sender of the command.
     */
    @Test
    public void testOneWayCommandRejected() {

        final DeliveryState remoteState = new Rejected();
        final ProtonDelivery unsuccessfulDelivery = mock(ProtonDelivery.class);
        when(unsuccessfulDelivery.remotelySettled()).thenReturn(true);
        when(unsuccessfulDelivery.getRemoteState()).thenReturn(remoteState);
        testOneWayCommandOutcome(unsuccessfulDelivery, Rejected.class, ProcessingOutcome.UNPROCESSABLE);
    }

    /**
     * Verifies that the adapter signals a released one-way command
     * back to the sender of the command.
     */
    @Test
    public void testOneWayCommandReleased() {

        final DeliveryState remoteState = new Released();
        final ProtonDelivery unsuccessfulDelivery = mock(ProtonDelivery.class);
        when(unsuccessfulDelivery.remotelySettled()).thenReturn(true);
        when(unsuccessfulDelivery.getRemoteState()).thenReturn(remoteState);
        testOneWayCommandOutcome(unsuccessfulDelivery, Released.class, ProcessingOutcome.UNDELIVERABLE);
    }

    /**
     * Verifies that the adapter signals a released one-way command
     * back to the sender of the command.
     */
    @Test
    public void testOneWayCommandUnsettled() {

        final DeliveryState remoteState = mock(DeliveryState.class);
        final ProtonDelivery unsuccessfulDelivery = mock(ProtonDelivery.class);
        when(unsuccessfulDelivery.remotelySettled()).thenReturn(false);
        when(unsuccessfulDelivery.getRemoteState()).thenReturn(remoteState);
        testOneWayCommandOutcome(unsuccessfulDelivery, Released.class, ProcessingOutcome.UNDELIVERABLE);
    }

    @SuppressWarnings("unchecked")
    private void testOneWayCommandOutcome(
            final ProtonDelivery outcome,
            final Class<? extends DeliveryState> expectedDeliveryState,
            final ProcessingOutcome expectedProcessingOutcome) {

        // GIVEN an AMQP adapter
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        //  to which a device is connected
        final ProtonSender deviceLink = mock(ProtonSender.class);
        when(deviceLink.getQoS()).thenReturn(ProtonQoS.AT_LEAST_ONCE);
        // that has subscribed to commands
        final ProtonReceiver commandReceiver = mock(ProtonReceiver.class);

        // WHEN an application sends a one-way command to the device
        final ProtonDelivery commandDelivery = mock(ProtonDelivery.class);
        final String commandAddress = String.format("%s/%s/%s", getCommandEndpoint(), TEST_TENANT_ID, TEST_DEVICE);
        final Buffer payload = Buffer.buffer("payload");
        final Message message = getFakeMessage(commandAddress, payload, "commandToEecute");
        final Command command = Command.from(message, TEST_TENANT_ID, TEST_DEVICE);
        final CommandContext context = CommandContext.from(command, commandDelivery, commandReceiver, mock(Span.class));
        adapter.onCommandReceived(deviceLink, context);
        // and the device settles it
        final ArgumentCaptor<Handler<ProtonDelivery>> deliveryUpdateHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceLink).send(any(Message.class), deliveryUpdateHandler.capture());
        deliveryUpdateHandler.getValue().handle(outcome);

        // THEN the command message is settled on the receiver link
        // with the same outcome
        verify(commandDelivery).disposition(any(expectedDeliveryState), eq(true));
        // and a credit is issued
        verify(commandReceiver).flow(1);
        // and the command has been reported according to the outcome
        verify(metrics).reportCommand(
                eq(Direction.ONE_WAY),
                eq(TEST_TENANT_ID),
                eq(expectedProcessingOutcome),
                eq(payload.length()),
                any());
    }

    /**
     * Verifies that the adapter increments the connection count when
     * a device connects and decrement the count when the device disconnects.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectionCount() {

        // GIVEN an AMQP adapter
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();

        // WHEN a device connects
        final Device authenticatedDevice = new Device(TEST_TENANT_ID, TEST_DEVICE);
        final Record record = new RecordImpl();
        record.set(AmqpAdapterConstants.KEY_CLIENT_DEVICE, Device.class, authenticatedDevice);
        final ProtonConnection deviceConnection = mock(ProtonConnection.class);
        when(deviceConnection.attachments()).thenReturn(record);
        adapter.onConnectRequest(deviceConnection);
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> openHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceConnection).openHandler(openHandler.capture());
        openHandler.getValue().handle(Future.succeededFuture(deviceConnection));

        // THEN the connection count is incremented
        verify(metrics).incrementConnections(TEST_TENANT_ID);

        // WHEN the connection to the device is lost
        final ArgumentCaptor<Handler<ProtonConnection>> disconnectHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceConnection).disconnectHandler(disconnectHandler.capture());
        disconnectHandler.getValue().handle(deviceConnection);

        // THEN the connection count is decremented
        verify(metrics).decrementConnections(TEST_TENANT_ID);

        // WHEN the device closes its connection to the adapter
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> closeHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceConnection).closeHandler(closeHandler.capture());
        closeHandler.getValue().handle(Future.succeededFuture());

        // THEN the connection count is decremented
        verify(metrics, times(2)).decrementConnections(TEST_TENANT_ID);
    }

    /**
     * Verifies that the adapter increments the connection count when
     * a device connects and decrement the count when the device disconnects.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectionCountForAnonymousDevice() {

        // GIVEN an AMQP adapter that does not require devices to authenticate
        config.setAuthenticationRequired(false);
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();

        // WHEN a device connects
        final ProtonConnection deviceConnection = mock(ProtonConnection.class);
        when(deviceConnection.attachments()).thenReturn(mock(Record.class));
        adapter.onConnectRequest(deviceConnection);
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> openHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceConnection).openHandler(openHandler.capture());
        openHandler.getValue().handle(Future.succeededFuture(deviceConnection));

        // THEN the connection count is incremented
        verify(metrics).incrementUnauthenticatedConnections();

        // WHEN the connection to the device is lost
        final ArgumentCaptor<Handler<ProtonConnection>> disconnectHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceConnection).disconnectHandler(disconnectHandler.capture());
        disconnectHandler.getValue().handle(deviceConnection);

        // THEN the connection count is decremented
        verify(metrics).decrementUnauthenticatedConnections();

        // WHEN the device closes its connection to the adapter
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> closeHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceConnection).closeHandler(closeHandler.capture());
        closeHandler.getValue().handle(Future.succeededFuture());

        // THEN the connection count is decremented
        verify(metrics, times(2)).decrementUnauthenticatedConnections();
    }

    /**
     * Verifies that a telemetry message is rejected due to the limit exceeded.
     * 
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testMessageLimitExceededForATelemetryMessage(final TestContext ctx) {

        // GIVEN an AMQP adapter
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        final DownstreamSender telemetrySender = givenATelemetrySenderForAnyTenant();

        // WHEN the message limit exceeds
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong()))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));
        // WHEN a device uploads telemetry data to the adapter (and wants to be notified of failure)
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(false); // AT LEAST ONCE
        final String to = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, TEST_DEVICE)
                .toString();
        final Buffer payload = Buffer.buffer("some payload");

        adapter.onMessageReceived(AmqpContext.fromMessage(delivery, getFakeMessage(to, payload), null))
                .setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the adapter does not send the message (regardless of the delivery mode).
                    verify(telemetrySender, never()).send(any(Message.class), (SpanContext) any());
                    verify(telemetrySender, never()).sendAndWaitForOutcome(any(Message.class), (SpanContext) any());
                    // because the message limit is exceeded
                    ctx.assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS.code(),
                            ((ClientErrorException) t).getErrorCode());
                    // AND notifies the device by sending back a REJECTED disposition
                    verify(delivery).disposition(any(Rejected.class), eq(true));
                    // AND has reported the message as unprocessable
                    verify(metrics).reportTelemetry(
                            eq(EndpointType.TELEMETRY),
                            eq(TEST_TENANT_ID),
                            eq(ProcessingOutcome.UNPROCESSABLE),
                            eq(QoS.AT_LEAST_ONCE),
                            eq(payload.length()),
                            any());
                }));
    }

    /**
     * Verifies that an event message is rejected due to the limit exceeded.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testMessageLimitExceededForAnEventMessage(final TestContext ctx) {

        // GIVEN an AMQP adapter
        final VertxBasedAmqpProtocolAdapter adapter = givenAnAmqpAdapter();
        final DownstreamSender eventSender = givenAnEventSender(Future.future());

        // WHEN the message limit exceeds
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong()))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));
        // WHEN a device uploads telemetry data to the adapter (and wants to be notified of failure)
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(false); // AT LEAST ONCE
        final String to = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, TEST_TENANT_ID, TEST_DEVICE)
                .toString();
        final Buffer payload = Buffer.buffer("some payload");

        adapter.onMessageReceived(AmqpContext.fromMessage(delivery, getFakeMessage(to, payload), null))
                .setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the adapter does not send the message (regardless of the delivery mode).
                    verify(eventSender, never()).send(any(Message.class), (SpanContext) any());
                    verify(eventSender, never()).sendAndWaitForOutcome(any(Message.class), (SpanContext) any());
                    // because the message limit is exceeded
                    ctx.assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS.code(),
                            ((ClientErrorException) t).getErrorCode());
                    // AND notifies the device by sending back a REJECTED disposition
                    verify(delivery).disposition(any(Rejected.class), eq(true));
                    // AND has reported the message as unprocessable
                    verify(metrics).reportTelemetry(
                            eq(EndpointType.EVENT),
                            eq(TEST_TENANT_ID),
                            eq(ProcessingOutcome.UNPROCESSABLE),
                            eq(QoS.AT_LEAST_ONCE),
                            eq(payload.length()),
                            any());
                }));
    }

    private String getCommandEndpoint() {
        return useLegacyCommandEndpoint() ? CommandConstants.COMMAND_LEGACY_ENDPOINT : CommandConstants.COMMAND_ENDPOINT;
    }

    /**
     * Checks whether the legacy Command & Control endpoint shall be used.
     * <p>
     * Returns {@code false} by default. Subclasses may return {@code true} here to perform tests using the legacy
     * command endpoint.
     *
     * @return {@code true} if the legacy command endpoint shall be used.
     */
    protected boolean useLegacyCommandEndpoint() {
        return false;
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

    private ProtonSender getSender(final String sourceAddress) {
        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.getRemoteSource()).thenReturn(mock(Source.class));
        when(sender.getRemoteSource().getAddress()).thenReturn(sourceAddress);
        return sender;
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
        when(tenantClient.get(eq(tenantId), (SpanContext) any())).thenReturn(Future.succeededFuture(tenantConfig));
    }

    private Message getFakeMessage(final String to, final Buffer payload) {
        return getFakeMessage(to, payload, "hello");
    }

    private Message getFakeMessage(final String to, final Buffer payload, final String subject) {

        final Data data = new Data(new Binary(payload.getBytes()));
        final Message message = mock(Message.class);
        when(message.getMessageId()).thenReturn("the-message-id");
        when(message.getSubject()).thenReturn(subject);
        when(message.getContentType()).thenReturn("text/plain");
        when(message.getBody()).thenReturn(data);
        when(message.getAddress()).thenReturn(to);
        return message;
    }

    private DownstreamSender givenATelemetrySenderForAnyTenant() {
        final DownstreamSender sender = mock(DownstreamSender.class);
        when(downstreamSenderFactory.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
        return sender;
    }

    private CommandResponseSender givenACommandResponseSenderForAnyTenant() {
        final CommandResponseSender responseSender = mock(CommandResponseSender.class);
        when(commandConsumerFactory.getCommandResponseSender(anyString(), anyString()))
                .thenReturn(Future.succeededFuture(responseSender));
        return responseSender;
    }

    private DownstreamSender givenAnEventSender(final Future<ProtonDelivery> outcome) {
        final DownstreamSender sender = mock(DownstreamSender.class);
        when(sender.sendAndWaitForOutcome(any(Message.class), (SpanContext) any())).thenReturn(outcome);

        when(downstreamSenderFactory.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(sender));
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
        adapter.setTenantClientFactory(tenantClientFactory);
        adapter.setDownstreamSenderFactory(downstreamSenderFactory);
        adapter.setRegistrationClientFactory(registrationClientFactory);
        adapter.setCredentialsClientFactory(credentialsClientFactory);
        adapter.setCommandConsumerFactory(commandConsumerFactory);
        adapter.setMetrics(metrics);
        adapter.setResourceLimitChecks(resourceLimitChecks);
        return adapter;
    }

    /**
     * Creates and sets up a ProtonServer mock.
     *
     * @return The configured server instance.
     */
    @SuppressWarnings("unchecked")
    private ProtonServer getAmqpServer() {

        final ProtonServer server = mock(ProtonServer.class);
        when(server.actualPort()).thenReturn(0, Constants.PORT_AMQP);
        when(server.connectHandler(any(Handler.class))).thenReturn(server);
        when(server.listen(any(Handler.class))).then(invocation -> {
            final Handler<AsyncResult<ProtonServer>> handler = invocation.getArgument(0);
            handler.handle(Future.succeededFuture(server));
            return server;
        });
        return server;
    }
}
