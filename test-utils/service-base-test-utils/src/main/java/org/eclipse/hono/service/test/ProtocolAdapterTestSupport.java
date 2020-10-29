/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.service.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.DeviceConnectionClientFactory;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.DownstreamSenderFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.monitoring.ConnectionEventProducer;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.mockito.ArgumentCaptor;

import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;

/**
 * A base class for implementing tests for protocol adapters.
 *
 * @param <T> The type of protocol adapter to test.
 * @param <C> The type of configuration properties the adapter uses.
 */
public abstract class ProtocolAdapterTestSupport<C extends ProtocolAdapterProperties, T extends AbstractProtocolAdapterBase<C>> {

    protected C properties;
    protected T adapter;

    protected ProtocolAdapterCommandConsumerFactory commandConsumerFactory;
    protected CommandTargetMapper commandTargetMapper;
    protected ConnectionEventProducer.Context connectionEventProducerContext;
    protected CredentialsClientFactory credentialsClientFactory;
    protected DeviceConnectionClientFactory deviceConnectionClientFactory;
    protected DownstreamSender downstreamSender;
    protected DownstreamSenderFactory downstreamSenderFactory;
    protected RegistrationClient registrationClient;
    protected RegistrationClientFactory registrationClientFactory;
    protected TenantClient tenantClient;
    protected TenantClientFactory tenantClientFactory;

    /**
     * Creates default configuration properties for the adapter.
     *
     * @return The configuration properties.
     */
    protected abstract C givenDefaultConfigurationProperties();

    /**
     * Creates mock instances of the service client factories.
     * <p>
     * All factories are configured to successfully connect/disconnect to/from their peer.
     */
    @SuppressWarnings("unchecked")
    protected void createClientFactories() {

        commandConsumerFactory = mock(ProtocolAdapterCommandConsumerFactory.class);
        when(commandConsumerFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(commandConsumerFactory.isConnected()).thenReturn(Future.succeededFuture());
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(commandConsumerFactory).disconnect(any(Handler.class));

        commandTargetMapper = mock(CommandTargetMapper.class);

        credentialsClientFactory = mock(CredentialsClientFactory.class);
        when(credentialsClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(credentialsClientFactory.isConnected()).thenReturn(Future.succeededFuture());
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(credentialsClientFactory).disconnect(any(Handler.class));

        deviceConnectionClientFactory = mock(DeviceConnectionClientFactory.class);
        when(deviceConnectionClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(deviceConnectionClientFactory.isConnected()).thenReturn(Future.succeededFuture());
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(deviceConnectionClientFactory).disconnect(any(Handler.class));

        downstreamSenderFactory = mock(DownstreamSenderFactory.class);
        when(downstreamSenderFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(downstreamSenderFactory.isConnected()).thenReturn(Future.succeededFuture());
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(downstreamSenderFactory).disconnect(any(Handler.class));

        registrationClientFactory = mock(RegistrationClientFactory.class);
        when(registrationClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(registrationClientFactory.isConnected()).thenReturn(Future.succeededFuture());
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(registrationClientFactory).disconnect(any(Handler.class));

        tenantClientFactory = mock(TenantClientFactory.class);
        when(tenantClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(tenantClientFactory.isConnected()).thenReturn(Future.succeededFuture());
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(tenantClientFactory).disconnect(any(Handler.class));

        connectionEventProducerContext = mock(ConnectionEventProducer.Context.class);
        when(connectionEventProducerContext.getMessageSenderClient()).thenReturn(downstreamSenderFactory);
        when(connectionEventProducerContext.getTenantClientFactory()).thenReturn(tenantClientFactory);
    }

    /**
     * Creates mock instances of the service clients and
     * configures the factories to return them.
     * <p>
     * This method is separate from {@link #createClientFactories()} in order to
     * support setups where the factories are created once for all test cases but
     * the client instances need to be (re-)set for each individual test case.
     * <p>
     * All clients are configured to return succeeded futures
     * containing <em>happy-path</em> results.
     * <p>
     * Creates a {@link TenantClient} and a {@link RegistrationClient}.
     *
     * @throws IllegalStateException if any of factories for which
     *         a mock client instance is to be created is {@code null}.
     */
    protected void createClients() {

        if (tenantClientFactory == null
                || registrationClientFactory == null) {
            throw new IllegalStateException("factories are not initialized");
        }

        tenantClient = mock(TenantClient.class);
        when(tenantClient.get(anyString())).thenAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0), true));
        });
        when(tenantClient.get(anyString(), any(SpanContext.class))).thenAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0), true));
        });
        when(tenantClientFactory.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        registrationClient = mock(RegistrationClient.class);
        when(registrationClient.assertRegistration(anyString(), any(), (SpanContext) any()))
                .thenAnswer(invocation -> {
                    final String deviceId = invocation.getArgument(0);
                    final JsonObject regAssertion = new JsonObject()
                            .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
                    return Future.succeededFuture(regAssertion);
                });
        when(registrationClientFactory.getOrCreateRegistrationClient(anyString()))
            .thenReturn(Future.succeededFuture(registrationClient));
    }

    /**
     * Sets the (mock) service clients on an adapter.
     *
     * @param adapter The adapter.
     */
    protected void setServiceClients(final T adapter) {
        adapter.setCommandConsumerFactory(commandConsumerFactory);
        adapter.setCommandTargetMapper(commandTargetMapper);
        adapter.setCredentialsClientFactory(credentialsClientFactory);
        adapter.setDeviceConnectionClientFactory(deviceConnectionClientFactory);
        adapter.setDownstreamSenderFactory(downstreamSenderFactory);
        adapter.setRegistrationClientFactory(registrationClientFactory);
        adapter.setTenantClientFactory(tenantClientFactory);
    }

    /**
     * Configures the downstream sender factory to create a mock sender
     * for telemetry messages regardless of tenant ID.
     * <p>
     * The returned sender's send methods will always return a succeeded future.
     *
     * @return The sender that the factory will create.
     */
    protected DownstreamSender givenATelemetrySenderForAnyTenant() {
        final Promise<ProtonDelivery> delivery = Promise.promise();
        delivery.complete(mock(ProtonDelivery.class));
        return givenATelemetrySenderForAnyTenant(delivery);
    }

    /**
     * Configures the downstream sender factory to create a mock sender
     * for telemetry messages regardless of tenant ID.
     * <p>
     * The returned sender's send methods will return the given promise's
     * corresponding future.
     *
     * @param outcome The outcome of sending a message using the returned sender.
     * @return The sender that the factory will create.
     */
    protected DownstreamSender givenATelemetrySenderForAnyTenant(final Promise<ProtonDelivery> outcome) {
        this.downstreamSender = mock(DownstreamSender.class);
        when(this.downstreamSender.send(any(Message.class), any(SpanContext.class)))
            .thenReturn(outcome.future());
        when(this.downstreamSender.sendAndWaitForOutcome(any(Message.class), any(SpanContext.class)))
            .thenReturn(outcome.future());

        when(downstreamSenderFactory.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(this.downstreamSender));
        return this.downstreamSender;
    }

    /**
     * Configures the downstream sender factory to create a mock sender
     * for events regardless of tenant ID.
     * <p>
     * The returned sender's send methods will always return a succeeded future.
     *
     * @return The sender that the factory will create.
     */
    protected DownstreamSender givenAnEventSenderForAnyTenant() {
        final Promise<ProtonDelivery> delivery = Promise.promise();
        delivery.complete(mock(ProtonDelivery.class));
        return givenAnEventSenderForAnyTenant(delivery);
    }


    /**
     * Configures the downstream sender factory to create a mock sender
     * for events regardless of tenant ID.
     * <p>
     * The returned sender's send methods will return the given promise's
     * corresponding future.
     *
     * @param outcome The outcome of sending a message using the returned sender.
     * @return The sender that the factory will create.
     */
    protected DownstreamSender givenAnEventSenderForAnyTenant(final Promise<ProtonDelivery> outcome) {
        this.downstreamSender = mock(DownstreamSender.class);
        when(this.downstreamSender.sendAndWaitForOutcome(any(Message.class), (SpanContext) any())).thenReturn(outcome.future());

        when(downstreamSenderFactory.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(this.downstreamSender));
        return this.downstreamSender;
    }

    /**
     * Configures the command consumer factory to create a mock sender
     * for command responses regardless of tenant ID.
     * <p>
     * The returned sender's send methods will always return a succeeded future.
     *
     * @return The sender that the factory will create.
     */
    protected CommandResponseSender givenACommandResponseSenderForAnyTenant() {
        final Promise<ProtonDelivery> delivery = Promise.promise();
        delivery.complete(mock(ProtonDelivery.class));
        return givenACommandResponseSenderForAnyTenant(delivery);
    }

    /**
     * Configures the command consumer factory to create a mock sender
     * for command responses regardless of tenant ID.
     * <p>
     * The returned sender's send methods will return the given promise's
     * corresponding future.
     *
     * @param outcome The outcome of sending a response using the returned sender.
     * @return The sender that the factory will create.
     */
    protected CommandResponseSender givenACommandResponseSenderForAnyTenant(final Promise<ProtonDelivery> outcome) {
        final CommandResponseSender responseSender = mock(CommandResponseSender.class);
        when(responseSender.sendCommandResponse(any(CommandResponse.class), (SpanContext) any())).thenReturn(outcome.future());

        when(commandConsumerFactory.getCommandResponseSender(anyString(), anyString()))
                .thenReturn(Future.succeededFuture(responseSender));
        return responseSender;
    }

    /**
     * Configures all mock collaborators to return a failed future
     * when checking their connection status.
     */
    protected void forceClientMocksToDisconnected() {
        when(tenantClientFactory.isConnected())
            .thenReturn(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
        when(downstreamSenderFactory.isConnected())
            .thenReturn(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
        when(registrationClientFactory.isConnected())
            .thenReturn(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
        when(credentialsClientFactory.isConnected())
            .thenReturn(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
        when(commandConsumerFactory.isConnected())
            .thenReturn(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
        when(deviceConnectionClientFactory.isConnected())
            .thenReturn(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
    }

    private void assertMessageProperties(
            final Message msg,
            final String endpoint,
            final String tenant,
            final String deviceId,
            final String contentType) {

        final ResourceIdentifier address = ResourceIdentifier.fromString(msg.getAddress());
        assertThat(address.getEndpoint()).isEqualTo(endpoint);
        Optional.ofNullable(tenant)
            .ifPresent(v -> assertThat(address.getTenantId()).isEqualTo(v));
        Optional.ofNullable(deviceId)
            .ifPresent(id -> assertThat(MessageHelper.getDeviceIdAnnotation(msg)).isEqualTo(id));
        Optional.ofNullable(contentType)
            .ifPresent(ct -> assertThat(msg.getContentType()).isEqualTo(ct));
    }

    /**
     * Asserts that a telemetry message has been sent downstream.
     *
     * @param qos The delivery semantics used for sending the message.
     * @param tenant The tenant to check the message against or {@code null} if the
     *               message's tenant should not be checked.
     * @param deviceId The device to check the message against or {@code null} if the
     *                 message's device ID should not be checked.
     * @param contentType The content type value to check the message against or {@code null}
     *                    if the message's content-type property should not be checked.
     * @throws NullPointerException if qos is {@code null}.
     * @throws AssertionError if no message matching the given parameters has been sent.
     */
    protected void assertTelemetryMessageHasBeenSentDownstream(
            final QoS qos,
            final String tenant,
            final String deviceId,
            final String contentType) {

        Objects.requireNonNull(qos);

        final ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);

        if (QoS.AT_MOST_ONCE == qos) {
            verify(downstreamSender).send(
                    msgCaptor.capture(),
                    (SpanContext) any());
        } else {
            verify(downstreamSender).sendAndWaitForOutcome(
                    msgCaptor.capture(),
                    (SpanContext) any());
        }
        assertThat(MessageHelper.getApplicationProperty(
                msgCaptor.getValue().getApplicationProperties(),
                MessageHelper.APP_PROPERTY_QOS,
                Integer.class))
            .isEqualTo(qos.ordinal());
        assertMessageProperties(
                msgCaptor.getValue(),
                TelemetryConstants.TELEMETRY_ENDPOINT,
                tenant,
                deviceId,
                contentType);
    }

    /**
     * Asserts that an empty notification has been sent downstream.
     *
     * @param tenant The tenant to check the message against.
     * @param deviceId The device to check the message against.
     * @param ttd The time-until-disconnect value to check the message against.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws AssertionError if no empty notification matching the given parameters has been sent.
     */
    protected void assertEmptyNotificationHasBeenSentDownstream(
            final String tenant,
            final String deviceId,
            final Integer ttd) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(ttd);

        final ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);

        verify(downstreamSender, atLeastOnce()).sendAndWaitForOutcome(
                msgCaptor.capture(),
                (SpanContext) any());

        assertMessageProperties(
                msgCaptor.getValue(),
                EventConstants.EVENT_ENDPOINT,
                tenant,
                deviceId,
                EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION);
        assertThat(MessageHelper.getTimeUntilDisconnect(msgCaptor.getValue())).isEqualTo(ttd);
    }

    /**
     * Asserts that an empty notification has not been sent downstream.
     *
     * @param tenant The tenant to check the message against.
     * @param deviceId The device to check the message against.
     * @param ttd The time-until-disconnect value to check the message against.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws AssertionError if an empty notification matching the given parameters has been sent.
     */
    protected void assertEmptyNotificationHasNotBeenSentDownstream(
            final String tenant,
            final String deviceId,
            final Integer ttd) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(ttd);

        verify(downstreamSender, never()).sendAndWaitForOutcome(
                argThat(msg -> ttd.equals(MessageHelper.getTimeUntilDisconnect(msg))),
                (SpanContext) any());
    }

    /**
     * Asserts that an event has been sent downstream.
     *
     * @param tenant The tenant to check the message against or {@code null} if the
     *               message's tenant should not be checked.
     * @param deviceId The device to check the message against or {@code null} if the
     *                 message's device ID should not be checked.
     * @param contentType The content type value to check the message against or {@code null}
     *                    if the message's content-type property should not be checked.
     * @throws AssertionError if no message matching the given parameters has been sent.
     */
    protected void assertEventHasBeenSentDownstream(
            final String tenant,
            final String deviceId,
            final String contentType) {
        assertEventHasBeenSentDownstream(tenant, deviceId, contentType, null);
    }

    /**
     * Asserts that an event has been sent downstream.
     *
     * @param tenant The tenant to check the message against or {@code null} if the
     *               message's tenant should not be checked.
     * @param deviceId The device to check the message against or {@code null} if the
     *                 message's device ID should not be checked.
     * @param contentType The content type value to check the message against or {@code null}
     *                    if the message's content-type property should not be checked.
     * @param ttl The time-to-live (milliseconds) value to check the message against or {@code null}
     *                    if the message's ttl property should not be checked.
     * @throws AssertionError if no message matching the given parameters has been sent.
     */
    protected void assertEventHasBeenSentDownstream(
            final String tenant,
            final String deviceId,
            final String contentType,
            final Long ttl) {

        final ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);

        verify(downstreamSender).sendAndWaitForOutcome(
                msgCaptor.capture(),
                (SpanContext) any());

        assertMessageProperties(
                msgCaptor.getValue(),
                EventConstants.EVENT_ENDPOINT,
                tenant,
                deviceId,
                contentType);
        assertThat(MessageHelper.getApplicationProperty(
                msgCaptor.getValue().getApplicationProperties(),
                MessageHelper.APP_PROPERTY_QOS,
                Integer.class))
            .isEqualTo(QoS.AT_LEAST_ONCE.ordinal());
        Optional.ofNullable(ttl)
            .ifPresent(v -> assertThat(msgCaptor.getValue().getTtl()).isEqualTo(v));
    }

    /**
     * Asserts that no message has been sent using the telemetry sender.
     *
     * @throws AssertionError if a message has been sent.
     */
    protected void assertNoTelemetryMessageHasBeenSentDownstream() {
        verify(downstreamSender, never()).send(any(Message.class), any());
        verify(downstreamSender, never()).sendAndWaitForOutcome(any(Message.class), any());
    }

    /**
     * Asserts that no message has been sent using the event sender.
     *
     * @throws AssertionError if a message has been sent.
     */
    protected void assertNoEventHasBeenSentDownstream() {
        verify(downstreamSender, never()).sendAndWaitForOutcome(any(Message.class), any());
    }
}
