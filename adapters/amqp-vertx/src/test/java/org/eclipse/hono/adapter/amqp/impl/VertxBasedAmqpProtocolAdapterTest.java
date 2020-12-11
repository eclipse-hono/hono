/*******************************************************************************
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.Source;
import org.apache.qpid.proton.engine.Record;
import org.apache.qpid.proton.engine.impl.RecordImpl;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.amqp.AmqpAdapterConstants;
import org.eclipse.hono.adapter.amqp.AmqpAdapterMetrics;
import org.eclipse.hono.adapter.amqp.AmqpAdapterProperties;
import org.eclipse.hono.adapter.client.command.CommandConsumer;
import org.eclipse.hono.adapter.client.command.CommandContext;
import org.eclipse.hono.adapter.client.command.CommandResponse;
import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.adapter.client.command.Commands;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.limiting.ConnectionLimitManager;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.ConnectionAttemptOutcome;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.service.monitoring.ConnectionEventProducer;
import org.eclipse.hono.service.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.service.test.ProtocolAdapterTestSupport;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonServer;

/**
 * Verifies the behavior of {@link VertxBasedAmqpProtocolAdapter}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 6, timeUnit = TimeUnit.SECONDS)
public class VertxBasedAmqpProtocolAdapterTest extends ProtocolAdapterTestSupport<AmqpAdapterProperties, VertxBasedAmqpProtocolAdapter> {

    /**
     * A tenant identifier used for testing.
     */
    private static final String TEST_TENANT_ID = Constants.DEFAULT_TENANT;
    /**
     * A device used for testing.
     */
    private static final String TEST_DEVICE = "test-device";

    private AmqpAdapterMetrics metrics;
    private ResourceLimitChecks resourceLimitChecks;
    private ConnectionLimitManager connectionLimitManager;
    private Vertx vertx;
    private Context context;
    private Span span;
    private ProtonServer server;

    /**
     * Setups the protocol adapter.
     */
    @BeforeEach
    public void setup() {

        metrics = mock(AmqpAdapterMetrics.class);
        vertx = mock(Vertx.class);
        context = VertxMockSupport.mockContext(vertx);

        span = TracingMockSupport.mockSpan();

        this.properties = givenDefaultConfigurationProperties();
        createClientFactories();
        prepareClients();

        connectionLimitManager = mock(ConnectionLimitManager.class);

        resourceLimitChecks = mock(ResourceLimitChecks.class);
        when(resourceLimitChecks.isConnectionLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
        when(resourceLimitChecks.isConnectionDurationLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AmqpAdapterProperties givenDefaultConfigurationProperties() {
        properties = new AmqpAdapterProperties();
        properties.setAuthenticationRequired(false);
        properties.setInsecurePort(5672);
        return properties;
    }

    /**
     * Verifies that a client provided Proton server instance is used and started by the adapter instead of
     * creating/starting a new one.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testStartUsesClientProvidedAmqpServer(final VertxTestContext ctx) {
        // GIVEN an adapter with a client provided Amqp Server
        givenAnAdapter(properties);

        // WHEN starting the adapter
        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        startupTracker.future().onComplete(ctx.succeeding(result -> {
            ctx.verify(() -> {
                // THEN the client provided server is started
                verify(server).connectHandler(VertxMockSupport.anyHandler());
                verify(server).listen(VertxMockSupport.anyHandler());
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter offers the ANONYMOUS-RELAY capability
     * in its open frame when a device connects.
     */
    @Test
    public void testAdapterSupportsAnonymousRelay() {

        // GIVEN an AMQP adapter with a configured server.
        givenAnAdapter(properties);

        // WHEN a device connects
        final Device authenticatedDevice = new Device(TEST_TENANT_ID, TEST_DEVICE);
        final Record record = new RecordImpl();
        record.set(AmqpAdapterConstants.KEY_CLIENT_DEVICE, Device.class, authenticatedDevice);
        final ProtonConnection deviceConnection = mock(ProtonConnection.class);
        when(deviceConnection.attachments()).thenReturn(record);
        when(deviceConnection.getRemoteContainer()).thenReturn("deviceContainer");

        adapter.onConnectRequest(deviceConnection);

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> openHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceConnection).openHandler(openHandler.capture());
        openHandler.getValue().handle(Future.succeededFuture(deviceConnection));

        // THEN the adapter's open frame contains the ANONYMOUS-RELAY capability
        verify(deviceConnection).setOfferedCapabilities(argThat(caps -> Arrays.stream(caps).anyMatch(cap -> Constants.CAP_ANONYMOUS_RELAY.equals(cap))));
    }


    /**
     * Verifies that the AMQP Adapter rejects (closes) AMQP links that contains a target address.
     */
    @Test
    public void testAdapterAcceptsAnonymousRelayReceiverOnly() {
        // GIVEN an AMQP adapter with a configured server.
        givenAnAdapter(properties);

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
    public void testUploadTelemetryWithAtMostOnceDeliverySemantics(final VertxTestContext ctx) {
        // GIVEN an AMQP adapter with a configured server
        givenAnAdapter(properties);
        // sending of downstream telemetry message succeeds
        givenATelemetrySenderForAnyTenant();

        // which is enabled for a tenant
        final TenantObject tenantObject = givenAConfiguredTenant(TEST_TENANT_ID, true);

        // IF a device sends a 'fire and forget' telemetry message
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(true);
        final Buffer payload = Buffer.buffer("payload");
        final String to = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, TEST_DEVICE).toString();

        adapter.onMessageReceived(AmqpContext.fromMessage(delivery, getFakeMessage(to, payload), span, null))
            .onComplete(ctx.succeeding(d -> {
                ctx.verify(() -> {
                    // THEN the adapter has forwarded the message downstream
                    assertTelemetryMessageHasBeenSentDownstream(
                            QoS.AT_MOST_ONCE,
                            TEST_TENANT_ID,
                            TEST_DEVICE,
                            "text/plain");
                    // and acknowledged the message to the device
                    verify(delivery).disposition(any(Accepted.class), eq(true));
                    // and has reported the telemetry message
                    verify(metrics).reportTelemetry(
                            eq(EndpointType.TELEMETRY),
                            eq(TEST_TENANT_ID),
                            eq(tenantObject),
                            eq(ProcessingOutcome.FORWARDED),
                            eq(MetricsTags.QoS.AT_MOST_ONCE),
                            eq(payload.length()),
                            any());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to upload an "unsettled" telemetry message results in the sender sending the
     * message and waits for a response from the downstream peer.
     */
    @Test
    public void testUploadTelemetryWithAtLeastOnceDeliverySemantics() {
        // GIVEN an adapter configured to use a user-define server.
        givenAnAdapter(properties);
        final Promise<Void> sendResult = Promise.promise();
        givenATelemetrySenderForAnyTenant(sendResult);

        // which is enabled for a tenant
        final TenantObject tenantObject = givenAConfiguredTenant(TEST_TENANT_ID, true);

        // IF a device send telemetry data (with un-settled delivery)
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(false);
        final Buffer payload = Buffer.buffer("payload");
        final String to = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT_SHORT, TEST_TENANT_ID, TEST_DEVICE).toString();
        final Message mockMessage = getFakeMessage(to, payload);

        adapter.onMessageReceived(AmqpContext.fromMessage(delivery, mockMessage, span, null));

        // THEN the sender sends the message
        assertTelemetryMessageHasBeenSentDownstream(
                QoS.AT_LEAST_ONCE,
                TEST_TENANT_ID,
                TEST_DEVICE,
                "text/plain");
        //  and waits for the outcome from the downstream peer
        verify(delivery, never()).disposition(any(DeliveryState.class), anyBoolean());
        // until the transfer is settled
        sendResult.complete();
        verify(delivery).disposition(any(Accepted.class), eq(true));
        // and has reported the telemetry message
        verify(metrics).reportTelemetry(
                eq(EndpointType.TELEMETRY),
                eq(TEST_TENANT_ID),
                eq(tenantObject),
                eq(ProcessingOutcome.FORWARDED),
                eq(MetricsTags.QoS.AT_LEAST_ONCE),
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
    public void testUploadTelemetryMessageFailsForDisabledAdapter(final VertxTestContext ctx) {

        // GIVEN an adapter configured to use a user-define server.
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();

        // AND given a tenant for which the AMQP Adapter is disabled
        final TenantObject tenantObject = givenAConfiguredTenant(TEST_TENANT_ID, false);

        // WHEN a device uploads telemetry data to the adapter (and wants to be notified of failure)
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(false); // AT LEAST ONCE
        final String to = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, TEST_DEVICE).toString();
        final Buffer payload = Buffer.buffer("some payload");

        adapter.onMessageReceived(AmqpContext.fromMessage(delivery, getFakeMessage(to, payload), span, null))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the adapter does not send the message (regardless of the delivery mode).
                    assertNoTelemetryMessageHasBeenSentDownstream();

                    // AND notifies the device by sending back a REJECTED disposition
                    verify(delivery).disposition(any(Rejected.class), eq(true));

                    // AND has reported the message as unprocessable
                    verify(metrics).reportTelemetry(
                            eq(EndpointType.TELEMETRY),
                            eq(TEST_TENANT_ID),
                            eq(tenantObject),
                            eq(ProcessingOutcome.UNPROCESSABLE),
                            eq(MetricsTags.QoS.AT_LEAST_ONCE),
                            eq(payload.length()),
                            any());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request from a gateway to upload an event on behalf of a device that belongs
     * to another tenant than the gateway fails.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadEventFailsForGatewayOfDifferentTenant(final VertxTestContext ctx) {

        // GIVEN an adapter
        givenAnAdapter(properties);
        givenAnEventSenderForAnyTenant();

        // with an enabled tenant
        givenAConfiguredTenant(TEST_TENANT_ID, true);

        // WHEN a gateway uploads an event on behalf of a device of another tenant
        final Device gateway = new Device(TEST_TENANT_ID, "gw");
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(false); // AT LEAST ONCE
        final String to = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, "other-tenant", TEST_DEVICE).toString();
        final Buffer payload = Buffer.buffer("some payload");

        adapter.onMessageReceived(AmqpContext.fromMessage(delivery, getFakeMessage(to, payload), span, gateway))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the adapter does not send the event
                    assertNoEventHasBeenSentDownstream();

                    // AND notifies the device by sending back a REJECTED disposition
                    verify(delivery).disposition(any(Rejected.class), eq(true));
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the adapter rejects presettled messages with an event address.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadEventRejectsPresettledMessage(final VertxTestContext ctx) {

        // GIVEN an adapter
        givenAnAdapter(properties);
        givenAnEventSenderForAnyTenant();

        // with an enabled tenant
        givenAConfiguredTenant(TEST_TENANT_ID, true);

        // WHEN a device uploads an event using a presettled message
        final Device gateway = new Device(TEST_TENANT_ID, "device");
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(true); // AT MOST ONCE
        final String to = ResourceIdentifier.fromString(EventConstants.EVENT_ENDPOINT).toString();
        final Buffer payload = Buffer.buffer("some payload");

        adapter.onMessageReceived(AmqpContext.fromMessage(delivery, getFakeMessage(to, payload), span, gateway))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the adapter does not forward the event
                    assertNoEventHasBeenSentDownstream();

                    // AND notifies the device by sending back a REJECTED disposition
                    verify(delivery).disposition(any(Rejected.class), eq(true));
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that if a client device opens a receiver link to receive commands, then the AMQP adapter opens a link
     * for sending commands to the device and notifies the downstream application by sending an
     * <em>EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION</em> event a with TTD -1. An unauthenticated device is used in
     * this test setup to simulate the client device.
     */
    @Test
    public void testAdapterOpensSenderLinkAndNotifyDownstreamApplication() {
        // GIVEN an AMQP adapter configured to use a user-defined server
        givenAnAdapter(properties);
        givenAnEventSenderForAnyTenant();

        // WHEN an unauthenticated device opens a receiver link with a valid source address
        final ProtonConnection deviceConnection = mock(ProtonConnection.class);
        when(deviceConnection.attachments()).thenReturn(mock(Record.class));
        when(commandConsumerFactory.createCommandConsumer(eq(TEST_TENANT_ID), eq(TEST_DEVICE), VertxMockSupport.anyHandler(), any(), any()))
            .thenReturn(Future.succeededFuture(mock(CommandConsumer.class)));
        final String sourceAddress = String.format("%s/%s/%s", getCommandEndpoint(), TEST_TENANT_ID, TEST_DEVICE);
        final ProtonSender sender = getSender(sourceAddress);

        adapter.handleRemoteSenderOpenForCommands(deviceConnection, sender);

        // THEN the adapter opens the link upon success
        verify(sender).open();

        // AND sends an empty notification downstream (with a TTD of -1)
        assertEmptyNotificationHasBeenSentDownstream(TEST_TENANT_ID, TEST_DEVICE, -1);
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
        givenAnAdapter(properties);
        givenAnEventSenderForAnyTenant();

        // and a device that wants to receive commands
        final CommandConsumer commandConsumer = mock(CommandConsumer.class);
        when(commandConsumer.close(any())).thenReturn(Future.succeededFuture());
        when(commandConsumerFactory.createCommandConsumer(eq(TEST_TENANT_ID), eq(TEST_DEVICE), VertxMockSupport.anyHandler(), any(), any()))
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
        assertEmptyNotificationHasBeenSentDownstream(TEST_TENANT_ID, TEST_DEVICE, 0);
    }

    /**
     * Verifies that the adapter closes a corresponding command consumer if
     * the device closes the connection to the adapter.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if the test execution gets interrupted.
     */
    @Test
    public void testAdapterClosesCommandConsumerWhenDeviceClosesConnection(final VertxTestContext ctx) throws InterruptedException {

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
     * @throws InterruptedException if the test execution gets interrupted.
     */
    @Test
    public void testAdapterClosesCommandConsumerWhenConnectionToDeviceIsLost(final VertxTestContext ctx) throws InterruptedException {

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
     * @throws InterruptedException if the test execution gets interrupted.
     */
    @SuppressWarnings("unchecked")
    private void testAdapterClosesCommandConsumer(
            final VertxTestContext ctx,
            final Handler<ProtonConnection> connectionLossTrigger) throws InterruptedException {

        // GIVEN an AMQP adapter
        givenAnAdapter(properties);
        givenAnEventSenderForAnyTenant();

        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future().onComplete(ctx.completing());
        adapter.start(startupTracker);
        assertThat(ctx.awaitCompletion(2, TimeUnit.SECONDS)).isTrue();

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
        final CommandConsumer commandConsumer = mock(CommandConsumer.class);
        when(commandConsumer.close(any())).thenReturn(Future.succeededFuture());
        when(commandConsumerFactory.createCommandConsumer(eq(TEST_TENANT_ID), eq(TEST_DEVICE), VertxMockSupport.anyHandler(), any(), any()))
            .thenReturn(Future.succeededFuture(commandConsumer));
        final String sourceAddress = getCommandEndpoint();
        final ProtonSender sender = getSender(sourceAddress);

        adapter.handleRemoteSenderOpenForCommands(deviceConnection, sender);

        // WHEN the connection to the device is lost
        connectionLossTrigger.handle(deviceConnection);

        // THEN the adapter closes the command consumer
        verify(commandConsumer).close(any());
        // and sends an empty event with TTD = 0 downstream
        assertEmptyNotificationHasBeenSentDownstream(TEST_TENANT_ID, TEST_DEVICE, 0);
    }

    /**
     * Verifies that the adapter doesn't send a 'disconnectedTtdEvent' on connection loss
     * when removal of the command consumer mapping entry fails (which would be the case
     * when another command consumer mapping had been registered in the mean time, meaning
     * the device has already reconnected).
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if the test execution gets interrupted.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testAdapterSkipsTtdEventOnCmdConnectionCloseIfRemoveConsumerFails(final VertxTestContext ctx) throws InterruptedException {

        // GIVEN an AMQP adapter
        givenAnAdapter(properties);
        givenAnEventSenderForAnyTenant();

        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future().onComplete(ctx.completing());
        adapter.start(startupTracker);
        assertThat(ctx.awaitCompletion(2, TimeUnit.SECONDS)).isTrue();

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
        final CommandConsumer commandConsumer = mock(CommandConsumer.class);
        when(commandConsumer.close(any())).thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED)));
        when(commandConsumerFactory.createCommandConsumer(eq(TEST_TENANT_ID), eq(TEST_DEVICE), VertxMockSupport.anyHandler(), any(), any()))
                .thenReturn(Future.succeededFuture(commandConsumer));
        final String sourceAddress = getCommandEndpoint();
        final ProtonSender sender = getSender(sourceAddress);

        adapter.handleRemoteSenderOpenForCommands(deviceConnection, sender);

        // WHEN the connection to the device is lost
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> closeHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceConnection).closeHandler(closeHandler.capture());
        closeHandler.getValue().handle(Future.succeededFuture(deviceConnection));

        // THEN the adapter closes the command consumer
        verify(commandConsumer).close(any());
        // and since closing the command consumer fails with a precon-failed exception
        // there is only one notification sent during consumer creation,
        assertEmptyNotificationHasBeenSentDownstream(TEST_TENANT_ID, TEST_DEVICE, -1);
        //  no 'disconnectedTtdEvent' event with TTD = 0
        assertEmptyNotificationHasNotBeenSentDownstream(TEST_TENANT_ID, TEST_DEVICE, 0);
    }

    /**
     * Verify that the AMQP adapter forwards command responses downstream.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadCommandResponseSucceeds(final VertxTestContext ctx) {

        // GIVEN an AMQP adapter
        givenAnAdapter(properties);
        final CommandResponseSender responseSender = givenACommandResponseSenderForAnyTenant();
        when(responseSender.sendCommandResponse(any(CommandResponse.class), (SpanContext) any())).thenReturn(Future.succeededFuture());
        // which is enabled for the test tenant
        final TenantObject tenantObject = givenAConfiguredTenant(TEST_TENANT_ID, true);

        // WHEN an unauthenticated device publishes a command response
        final String replyToAddress = String.format("%s/%s/%s", getCommandResponseEndpoint(), TEST_TENANT_ID,
                Commands.getDeviceFacingReplyToId("test-reply-id", TEST_DEVICE));

        final Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(MessageHelper.APP_PROPERTY_STATUS, 200);
        final ApplicationProperties props = new ApplicationProperties(propertyMap);
        final Buffer payload = Buffer.buffer("some payload");
        final Message message = getFakeMessage(replyToAddress, payload);
        message.setCorrelationId("correlation-id");
        message.setApplicationProperties(props);

        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        adapter.onMessageReceived(AmqpContext.fromMessage(delivery, message, span, null)).onComplete(ctx.succeeding(ok -> {
            ctx.verify(() -> {
                // THEN the adapter forwards the command response message downstream
                verify(responseSender).sendCommandResponse((CommandResponse) any(), (SpanContext) any());
                // and reports the forwarded message
                verify(metrics).reportCommand(
                    eq(Direction.RESPONSE),
                    eq(TEST_TENANT_ID),
                    eq(tenantObject),
                    eq(ProcessingOutcome.FORWARDED),
                    eq(payload.length()),
                    any());
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verify that the AMQP adapter forwards command responses that do not contain a payload downstream.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadCommandResponseWithoutPayloadSucceeds(final VertxTestContext ctx) {

        // GIVEN an AMQP adapter
        givenAnAdapter(properties);
        final CommandResponseSender responseSender = givenACommandResponseSenderForAnyTenant();
        when(responseSender.sendCommandResponse(any(CommandResponse.class), (SpanContext) any()))
                .thenReturn(Future.succeededFuture());
        // which is enabled for the test tenant
        final TenantObject tenantObject = givenAConfiguredTenant(TEST_TENANT_ID, true);

        // WHEN an unauthenticated device publishes a command response
        final String replyToAddress = String.format("%s/%s/%s", getCommandResponseEndpoint(), TEST_TENANT_ID,
                Commands.getDeviceFacingReplyToId("test-reply-id", TEST_DEVICE));

        final Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(MessageHelper.APP_PROPERTY_STATUS, 200);
        final ApplicationProperties props = new ApplicationProperties(propertyMap);
        final Message message = getFakeMessage(replyToAddress, null);
        message.setCorrelationId("correlation-id");
        message.setApplicationProperties(props);

        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        adapter.onMessageReceived(AmqpContext.fromMessage(delivery, message, span, null)).onComplete(ctx.succeeding(ok -> {
            ctx.verify(() -> {
                // THEN the adapter forwards the command response message downstream
                verify(responseSender).sendCommandResponse((CommandResponse) any(), (SpanContext) any());
                // and reports the forwarded message
                verify(metrics).reportCommand(
                        eq(Direction.RESPONSE),
                        eq(TEST_TENANT_ID),
                        eq(tenantObject),
                        eq(ProcessingOutcome.FORWARDED),
                        eq(0),
                        any());
            });
            ctx.completeNow();
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
        testOneWayCommandOutcome(successfulDelivery, ctx -> verify(ctx).accept(), ProcessingOutcome.FORWARDED);
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
        testOneWayCommandOutcome(unsuccessfulDelivery, ctx -> verify(ctx).reject(any()), ProcessingOutcome.UNPROCESSABLE);
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
        testOneWayCommandOutcome(unsuccessfulDelivery, ctx -> verify(ctx).release(), ProcessingOutcome.UNDELIVERABLE);
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
        testOneWayCommandOutcome(unsuccessfulDelivery, ctx -> verify(ctx).release(), ProcessingOutcome.UNDELIVERABLE);
    }

    @SuppressWarnings("unchecked")
    private void testOneWayCommandOutcome(
            final ProtonDelivery deviceDisposition,
            final Consumer<CommandContext> outcomeAssertion,
            final ProcessingOutcome expectedProcessingOutcome) {

        // GIVEN an AMQP adapter
        givenAnAdapter(properties);
        //  to which a device is connected
        final ProtonSender deviceLink = mock(ProtonSender.class);
        when(deviceLink.getQoS()).thenReturn(ProtonQoS.AT_LEAST_ONCE);
        // that has subscribed to commands
        final TenantObject tenantObject = givenAConfiguredTenant(TEST_TENANT_ID, true);

        // WHEN an application sends a one-way command to the device
        final Buffer payload = Buffer.buffer("payload");

        final CommandContext context = givenAOneWayCommandContext(
                TEST_TENANT_ID,
                TEST_DEVICE,
                "commandToExecute",
                "text/plain",
                payload);

        adapter.onCommandReceived(tenantObject, deviceLink, context);
        // and the device settles it
        final ArgumentCaptor<Handler<ProtonDelivery>> deliveryUpdateHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceLink).send(any(Message.class), deliveryUpdateHandler.capture());
        deliveryUpdateHandler.getValue().handle(deviceDisposition);

        // THEN the command is handled according to the device's disposition update
        outcomeAssertion.accept(context);
        // and the command has been reported according to the outcome
        verify(metrics).reportCommand(
                eq(Direction.ONE_WAY),
                eq(TEST_TENANT_ID),
                eq(tenantObject),
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
        final ConnectionEventProducer connectionEventProducer = mock(ConnectionEventProducer.class);
        when(connectionEventProducer.connected(
                any(ConnectionEventProducer.Context.class),
                anyString(),
                anyString(),
                any(),
                any())).thenReturn(Future.succeededFuture());
        when(connectionEventProducer.disconnected(
                any(ConnectionEventProducer.Context.class),
                anyString(),
                anyString(),
                any(),
                any())).thenReturn(Future.succeededFuture());
        givenAnAdapter(properties);
        adapter.setConnectionEventProducer(connectionEventProducer);

        // WHEN a device connects
        final Device authenticatedDevice = new Device(TEST_TENANT_ID, TEST_DEVICE);
        final Record record = new RecordImpl();
        record.set(AmqpAdapterConstants.KEY_CLIENT_DEVICE, Device.class, authenticatedDevice);
        final ProtonConnection deviceConnection = mock(ProtonConnection.class);
        when(deviceConnection.attachments()).thenReturn(record);
        when(deviceConnection.getRemoteContainer()).thenReturn("deviceContainer");
        adapter.onConnectRequest(deviceConnection);
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> openHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceConnection).openHandler(openHandler.capture());
        openHandler.getValue().handle(Future.succeededFuture(deviceConnection));

        // THEN the connection count is incremented
        verify(metrics).incrementConnections(TEST_TENANT_ID);
        // and a connected event has been fired
        verify(connectionEventProducer).connected(
                any(ConnectionEventProducer.Context.class),
                anyString(),
                eq(adapter.getTypeName()),
                eq(authenticatedDevice),
                any());

        // WHEN the connection to the device is lost
        final ArgumentCaptor<Handler<ProtonConnection>> disconnectHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceConnection).disconnectHandler(disconnectHandler.capture());
        disconnectHandler.getValue().handle(deviceConnection);

        // THEN the connection count is decremented
        verify(metrics).decrementConnections(TEST_TENANT_ID);
        // and a disconnected event has been fired
        verify(connectionEventProducer).disconnected(
                any(ConnectionEventProducer.Context.class),
                eq("deviceContainer"),
                eq(adapter.getTypeName()),
                eq(authenticatedDevice),
                any());

        // WHEN the device closes its connection to the adapter
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> closeHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceConnection).closeHandler(closeHandler.capture());
        closeHandler.getValue().handle(Future.succeededFuture());

        // THEN the connection count is decremented
        verify(metrics, times(2)).decrementConnections(TEST_TENANT_ID);
        // and a disconnected event has been fired
        verify(connectionEventProducer, times(2)).disconnected(
                any(ConnectionEventProducer.Context.class),
                eq("deviceContainer"),
                eq(adapter.getTypeName()),
                eq(authenticatedDevice),
                any());
    }

    /**
     * Verifies that the adapter increments the connection count when
     * a device connects and decrement the count when the device disconnects.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectionCountForAnonymousDevice() {

        // GIVEN an AMQP adapter that does not require devices to authenticate
        properties.setAuthenticationRequired(false);
        givenAnAdapter(properties);

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
     * Verifies that a telemetry message is rejected due to the tenant's message limit having exceeded.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testMessageLimitExceededForATelemetryMessage(final VertxTestContext ctx) {

        final String to = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, TEST_DEVICE)
                .toString();
        final Buffer payload = Buffer.buffer("some payload");

        testMessageLimitExceededForADownstreamMessage(
                ctx,
                getFakeMessage(to, payload),
                s -> {
                    assertNoTelemetryMessageHasBeenSentDownstream();
                    verify(metrics).reportTelemetry(
                            eq(EndpointType.TELEMETRY),
                            eq(TEST_TENANT_ID),
                            any(TenantObject.class),
                            eq(ProcessingOutcome.UNPROCESSABLE),
                            eq(MetricsTags.QoS.AT_LEAST_ONCE),
                            eq(payload.length()),
                            any());
                });
    }

    /**
     * Verifies that an event message is rejected due to the limit exceeded.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testMessageLimitExceededForAnEventMessage(final VertxTestContext ctx) {

        final String to = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, TEST_TENANT_ID, TEST_DEVICE)
                .toString();
        final Buffer payload = Buffer.buffer("some payload");
        testMessageLimitExceededForADownstreamMessage(
                ctx,
                getFakeMessage(to, payload),
                s -> {
                    assertNoEventHasBeenSentDownstream();
                    verify(metrics).reportTelemetry(
                            eq(EndpointType.EVENT),
                            eq(TEST_TENANT_ID),
                            any(TenantObject.class),
                            eq(ProcessingOutcome.UNPROCESSABLE),
                            eq(MetricsTags.QoS.AT_LEAST_ONCE),
                            eq(payload.length()),
                            any());
                });
    }

    /**
     * Verifies that a command response message is rejected due to the limit exceeded.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testMessageLimitExceededForACommandResponseMessage(final VertxTestContext ctx) {

        final String replyToAddress = String.format("%s/%s/%s", getCommandResponseEndpoint(), TEST_TENANT_ID,
                Commands.getDeviceFacingReplyToId("test-reply-id", TEST_DEVICE));
        final Buffer payload = Buffer.buffer("payload");
        final Message message = getFakeMessage(replyToAddress, payload);
        message.setCorrelationId("correlation-id");
        final Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(MessageHelper.APP_PROPERTY_STATUS, 200);
        final ApplicationProperties props = new ApplicationProperties(propertyMap);
        message.setApplicationProperties(props);

        testMessageLimitExceededForADownstreamMessage(
                ctx,
                message,
                s -> {
                    assertNoCommandResponseHasBeenSentDownstream();
                    verify(metrics).reportCommand(
                            eq(Direction.RESPONSE),
                            eq(TEST_TENANT_ID),
                            any(TenantObject.class),
                            eq(ProcessingOutcome.UNPROCESSABLE),
                            eq(payload.length()),
                            any());
                });
    }

    private void testMessageLimitExceededForADownstreamMessage(
            final VertxTestContext ctx,
            final Message message,
            final Consumer<Void> postUploadAssertions) {

        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.remotelySettled()).thenReturn(false); // AT LEAST ONCE
        final AmqpContext amqpContext = AmqpContext.fromMessage(delivery, message, span, null);

        // GIVEN an AMQP adapter
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();

        // which is enabled for a tenant with exceeded message limit
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));
        // WHEN a device uploads a message to the adapter with AT_LEAST_ONCE delivery semantics

        adapter.onMessageReceived(amqpContext)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the message limit is exceeded
                        assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(HttpUtils.HTTP_TOO_MANY_REQUESTS);
                        // AND the client receives a corresponding REJECTED disposition
                        verify(delivery).disposition(
                                argThat(s -> {
                                    if (s instanceof Rejected) {
                                        return AmqpError.RESOURCE_LIMIT_EXCEEDED.equals(((Rejected) s).getError().getCondition());
                                    } else {
                                        return false;
                                    }
                                }),
                                eq(true));
                        // AND
                        postUploadAssertions.accept(null);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the adapter closes the link for sending commands to a device when no
     * delivery update is received after a certain amount of time.
     */
    @Test
    public void testLinkForSendingCommandsCloseAfterTimeout() {
        // GIVEN an AMQP adapter
        givenAnAdapter(properties);
        // to which a device is connected
        final ProtonSender deviceLink = mock(ProtonSender.class);
        when(deviceLink.getQoS()).thenReturn(ProtonQoS.AT_LEAST_ONCE);
        // that has subscribed to commands
        final TenantObject tenantObject = givenAConfiguredTenant(TEST_TENANT_ID, true);

        // WHEN an application sends a one-way command to the device
        final Buffer payload = Buffer.buffer("payload");
        final CommandContext context = givenAOneWayCommandContext(
                TEST_TENANT_ID,
                TEST_DEVICE,
                "commandToExecute",
                "text/plain",
                payload);

        // AND no delivery update is received from the device after sometime
        doAnswer(invocation -> {
            final Handler<Long> task =  invocation.getArgument(1);
            task.handle(1L);
            return 1L;
        }).when(vertx).setTimer(anyLong(), VertxMockSupport.anyHandler());

        adapter.onCommandReceived(tenantObject, deviceLink, context);
        // THEN the adapter releases the command
        verify(context).release();
    }

    /**
     * Verifies that the connection is rejected as the connection limit for 
     * the given tenant is exceeded.
     */
    @Test
    public void testConnectionFailsIfTenantLevelConnectionLimitIsExceeded() {
        // GIVEN an AMQP adapter that requires devices to authenticate
        properties.setAuthenticationRequired(true);
        givenAnAdapter(properties);
        // WHEN the connection limit for the given tenant exceeds
        when(resourceLimitChecks.isConnectionLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));
        // WHEN a device connects
        final Device authenticatedDevice = new Device(TEST_TENANT_ID, TEST_DEVICE);
        final Record record = new RecordImpl();
        record.set(AmqpAdapterConstants.KEY_CLIENT_DEVICE, Device.class, authenticatedDevice);
        final ProtonConnection deviceConnection = mock(ProtonConnection.class);
        when(deviceConnection.attachments()).thenReturn(record);
        adapter.onConnectRequest(deviceConnection);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> openHandler = ArgumentCaptor
                .forClass(Handler.class);
        verify(deviceConnection).openHandler(openHandler.capture());
        openHandler.getValue().handle(Future.succeededFuture(deviceConnection));
        // THEN the adapter does not accept the incoming connection request. 
        final ArgumentCaptor<ErrorCondition> errorConditionCaptor = ArgumentCaptor.forClass(ErrorCondition.class);
        verify(deviceConnection).setCondition(errorConditionCaptor.capture());
        assertEquals(AmqpError.UNAUTHORIZED_ACCESS, errorConditionCaptor.getValue().getCondition());
        verify(metrics).reportConnectionAttempt(
                ConnectionAttemptOutcome.TENANT_CONNECTIONS_EXCEEDED,
                TEST_TENANT_ID);
    }

    /**
     * Verifies that the connection is rejected as the adapter is disabled.
     */
    @Test
    public void testConnectionFailsIfAdapterIsDisabled() {
        // GIVEN an AMQP adapter that requires devices to authenticate
        properties.setAuthenticationRequired(true);
        givenAnAdapter(properties);
        // AND given a tenant for which the AMQP Adapter is disabled
        givenAConfiguredTenant(TEST_TENANT_ID, false);
        // WHEN a device connects
        final Device authenticatedDevice = new Device(TEST_TENANT_ID, TEST_DEVICE);
        final Record record = new RecordImpl();
        record.set(AmqpAdapterConstants.KEY_CLIENT_DEVICE, Device.class, authenticatedDevice);
        final ProtonConnection deviceConnection = mock(ProtonConnection.class);
        when(deviceConnection.attachments()).thenReturn(record);
        adapter.onConnectRequest(deviceConnection);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> openHandler = ArgumentCaptor
                .forClass(Handler.class);
        verify(deviceConnection).openHandler(openHandler.capture());
        openHandler.getValue().handle(Future.succeededFuture(deviceConnection));
        // THEN the adapter does not accept the incoming connection request. 
        final ArgumentCaptor<ErrorCondition> errorConditionCaptor = ArgumentCaptor.forClass(ErrorCondition.class);
        verify(deviceConnection).setCondition(errorConditionCaptor.capture());
        assertEquals(AmqpError.UNAUTHORIZED_ACCESS, errorConditionCaptor.getValue().getCondition());
        verify(metrics).reportConnectionAttempt(
                ConnectionAttemptOutcome.ADAPTER_DISABLED,
                TEST_TENANT_ID);
    }

    /**
     * Verifies that an authenticated device's attempt to establish a connection fails if
     * the adapter's connection limit is exceeded.
     */
    @Test
    public void testConnectionFailsForAuthenticatedDeviceIfAdapterLevelConnectionLimitIsExceeded() {

        // GIVEN an AMQP adapter that requires devices to authenticate
        properties.setAuthenticationRequired(true);
        givenAnAdapter(properties);
        // WHEN the adapter's connection limit exceeds
        when(connectionLimitManager.isLimitExceeded()).thenReturn(true);
        // WHEN a device connects
        final Device authenticatedDevice = new Device(TEST_TENANT_ID, TEST_DEVICE);
        final Record record = new RecordImpl();
        record.set(AmqpAdapterConstants.KEY_CLIENT_DEVICE, Device.class, authenticatedDevice);
        final ProtonConnection deviceConnection = mock(ProtonConnection.class);
        when(deviceConnection.attachments()).thenReturn(record);
        adapter.onConnectRequest(deviceConnection);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> openHandler = ArgumentCaptor
                .forClass(Handler.class);
        verify(deviceConnection).openHandler(openHandler.capture());
        openHandler.getValue().handle(Future.succeededFuture(deviceConnection));
        // THEN the connection count should be incremented when the connection is opened
        final InOrder metricsInOrderVerifier = inOrder(metrics);
        metricsInOrderVerifier.verify(metrics).incrementConnections(TEST_TENANT_ID);
        // AND the adapter should close the connection right after it opened it
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> closeHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceConnection).closeHandler(closeHandler.capture());
        closeHandler.getValue().handle(Future.succeededFuture());
        final ArgumentCaptor<ErrorCondition> errorConditionCaptor = ArgumentCaptor.forClass(ErrorCondition.class);
        verify(deviceConnection).setCondition(errorConditionCaptor.capture());
        assertEquals(AmqpError.UNAUTHORIZED_ACCESS, errorConditionCaptor.getValue().getCondition());
        // AND the connection count should be decremented accordingly when the connection is closed
        metricsInOrderVerifier.verify(metrics).decrementConnections(TEST_TENANT_ID);
        verify(metrics).reportConnectionAttempt(ConnectionAttemptOutcome.ADAPTER_CONNECTIONS_EXCEEDED, TEST_TENANT_ID);
    }

    /**
     * Verifies that an unauthenticated device's attempt to establish a connection fails if
     * the adapter's connection limit is exceeded.
     */
    @Test
    public void testConnectionFailsForUnauthenticatedDeviceIfAdapterLevelConnectionLimitIsExceeded() {

        // GIVEN an AMQP adapter that does not require devices to authenticate
        properties.setAuthenticationRequired(false);
        givenAnAdapter(properties);
        // WHEN the adapter's connection limit exceeds
        when(connectionLimitManager.isLimitExceeded()).thenReturn(true);
        // WHEN a device connects
        final ProtonConnection deviceConnection = mock(ProtonConnection.class);
        when(deviceConnection.attachments()).thenReturn(new RecordImpl());
        adapter.onConnectRequest(deviceConnection);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> openHandler = ArgumentCaptor
                .forClass(Handler.class);
        verify(deviceConnection).openHandler(openHandler.capture());
        openHandler.getValue().handle(Future.succeededFuture(deviceConnection));
        // THEN the connection count should be incremented when the connection is opened
        final InOrder metricsInOrderVerifier = inOrder(metrics);
        metricsInOrderVerifier.verify(metrics).incrementUnauthenticatedConnections();
        // AND the adapter should close the connection right after it opened it
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> closeHandler = ArgumentCaptor.forClass(Handler.class);
        verify(deviceConnection).closeHandler(closeHandler.capture());
        closeHandler.getValue().handle(Future.succeededFuture());
        final ArgumentCaptor<ErrorCondition> errorConditionCaptor = ArgumentCaptor.forClass(ErrorCondition.class);
        verify(deviceConnection).setCondition(errorConditionCaptor.capture());
        assertEquals(AmqpError.UNAUTHORIZED_ACCESS, errorConditionCaptor.getValue().getCondition());
        // AND the connection count should be decremented accordingly when the connection is closed
        metricsInOrderVerifier.verify(metrics).decrementUnauthenticatedConnections();
        verify(metrics).reportConnectionAttempt(ConnectionAttemptOutcome.ADAPTER_CONNECTIONS_EXCEEDED, null);
    }

    private String getCommandEndpoint() {
        return CommandConstants.COMMAND_ENDPOINT;
    }

    private String getCommandResponseEndpoint() {
        return CommandConstants.COMMAND_RESPONSE_ENDPOINT;
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

    private TenantObject givenAConfiguredTenant(final String tenantId, final boolean enabled) {
        final TenantObject tenantConfig = TenantObject.from(tenantId, Boolean.TRUE);
        tenantConfig.addAdapter(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_AMQP).setEnabled(enabled));
        when(tenantClient.get(eq(tenantId), (SpanContext) any())).thenReturn(Future.succeededFuture(tenantConfig));
        return tenantConfig;
    }

    private Message getFakeMessage(final String to, final Buffer payload) {
        return getFakeMessage(to, payload, "text/plain", "hello");
    }

    private Message getFakeMessage(final String to, final Buffer payload, final String contentType, final String subject) {

        final Message message = ProtonHelper.message();
        message.setMessageId("the-message-id");
        message.setSubject(subject);
        message.setAddress(to);

        if (payload != null) {
            message.setContentType(contentType);
            final Data data = new Data(new Binary(payload.getBytes()));
            message.setBody(data);
        }

        return message;
    }

    /**
     * Creates a new adapter instance to be tested.
     * <p>
     * This method
     * <ol>
     * <li>creates a new {@code ProtonServer} using {@link #getAmqpServer()}</li>
     * <li>assigns the result to property <em>server</em></li>
     * <li>passes the server in to {@link #newAdapter(ProtonServer, AmqpAdapterProperties)}</li>
     * <li>assigns the result to property <em>adapter</em></li>
     * </ol>
     *
     * @param configuration The configuration properties to use.
     * @return The adapter instance.
     */
    private VertxBasedAmqpProtocolAdapter givenAnAdapter(final AmqpAdapterProperties configuration) {
        this.server = getAmqpServer();
        this.adapter = newAdapter(this.server, configuration);
        return adapter;
    }

    /**
     * Creates an AMQP protocol adapter for a server and configuration.
     * <p>
     * The adapter will use the given server for its <em>insecure</em> port.
     *
     * @param server The AMQP Proton server.
     * @param configuration The adapter's configuration properties.
     * @return The AMQP adapter instance.
     */
    private VertxBasedAmqpProtocolAdapter newAdapter(
            final ProtonServer server,
            final AmqpAdapterProperties configuration) {

        final VertxBasedAmqpProtocolAdapter adapter = new VertxBasedAmqpProtocolAdapter();

        adapter.setConfig(configuration);
        adapter.setInsecureAmqpServer(server);
        adapter.setMetrics(metrics);
        adapter.setResourceLimitChecks(resourceLimitChecks);
        adapter.setConnectionLimitManager(connectionLimitManager);
        setServiceClients(adapter);
        adapter.init(vertx, context);
        return adapter;
    }

    /**
     * Creates and sets up a ProtonServer mock.
     *
     * @return The configured server instance.
     */
    private ProtonServer getAmqpServer() {

        final ProtonServer server = mock(ProtonServer.class);
        when(server.actualPort()).thenReturn(0, Constants.PORT_AMQP);
        when(server.connectHandler(VertxMockSupport.anyHandler())).thenReturn(server);
        when(server.listen(VertxMockSupport.anyHandler())).then(invocation -> {
            final Handler<AsyncResult<ProtonServer>> handler = invocation.getArgument(0);
            handler.handle(Future.succeededFuture(server));
            return server;
        });
        return server;
    }
}
