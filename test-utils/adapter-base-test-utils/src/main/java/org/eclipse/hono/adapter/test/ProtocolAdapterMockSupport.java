/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.command.CommandRouterClient;
import org.eclipse.hono.client.command.Commands;
import org.eclipse.hono.client.command.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.registry.CredentialsClient;
import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.TelemetrySender;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.mockito.ArgumentCaptor;

import io.opentracing.SpanContext;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;

/**
 * A base class for implementing tests around protocol adapters.
 * <p>
 * This class provides means to create mock instances of clients for the services that protocol adapters depend on.
 * It also provides helper methods to set up the fixture for these clients.
 *
 */
public abstract class ProtocolAdapterMockSupport {

    protected CredentialsClient credentialsClient;
    protected CommandRouterClient commandRouterClient;
    protected EventSender eventSender;
    protected ProtocolAdapterCommandConsumerFactory commandConsumerFactory;
    protected CommandResponseSender commandResponseSender;
    protected DeviceRegistrationClient registrationClient;
    protected TenantClient tenantClient;
    protected TelemetrySender telemetrySender;

    private ProtocolAdapterCommandConsumerFactory createCommandConsumerFactory() {
        final ProtocolAdapterCommandConsumerFactory factory = mock(ProtocolAdapterCommandConsumerFactory.class);
        when(factory.start()).thenReturn(Future.succeededFuture());
        when(factory.stop()).thenReturn(Future.succeededFuture());
        return factory;
    }

    private CommandResponseSender createCommandResponseSender() {
        final CommandResponseSender client = mock(CommandResponseSender.class);
        when(client.getMessagingType()).thenReturn(MessagingType.amqp);
        when(client.start()).thenReturn(Future.succeededFuture());
        when(client.stop()).thenReturn(Future.succeededFuture());
        return client;
    }

    private TenantClient createTenantClientMock() {
        final TenantClient client = mock(TenantClient.class);
        when(client.start()).thenReturn(Future.succeededFuture());
        when(client.stop()).thenReturn(Future.succeededFuture());
        return client;
    }

    private DeviceRegistrationClient createDeviceRegistrationClientMock() {
        final DeviceRegistrationClient client = mock(DeviceRegistrationClient.class);
        when(client.start()).thenReturn(Future.succeededFuture());
        when(client.stop()).thenReturn(Future.succeededFuture());
        return client;
    }

    private CredentialsClient createCredentialsClientMock() {
        final CredentialsClient client = mock(CredentialsClient.class);
        when(client.start()).thenReturn(Future.succeededFuture());
        when(client.stop()).thenReturn(Future.succeededFuture());
        return client;
    }

    private CommandRouterClient createCommandRouterClientMock() {
        final CommandRouterClient client = mock(CommandRouterClient.class);
        when(client.start()).thenReturn(Future.succeededFuture());
        when(client.stop()).thenReturn(Future.succeededFuture());
        return client;
    }

    private TelemetrySender createTelemetrySenderMock() {
        final TelemetrySender sender = mock(TelemetrySender.class);
        when(sender.getMessagingType()).thenReturn(MessagingType.amqp);
        when(sender.start()).thenReturn(Future.succeededFuture());
        when(sender.stop()).thenReturn(Future.succeededFuture());
        return sender;
    }

    private EventSender createEventSenderMock() {
        final EventSender sender = mock(EventSender.class);
        when(sender.getMessagingType()).thenReturn(MessagingType.amqp);
        when(sender.start()).thenReturn(Future.succeededFuture());
        when(sender.stop()).thenReturn(Future.succeededFuture());
        return sender;
    }

    /**
     * Creates mock instances of the service clients.
     * <p>
     * All clients are configured to successfully connect/disconnect to/from their peer.
     */
    protected void createClients() {

        this.commandConsumerFactory = createCommandConsumerFactory();
        this.commandResponseSender = createCommandResponseSender();

        this.commandRouterClient = createCommandRouterClientMock();
        this.tenantClient = createTenantClientMock();
        this.registrationClient = createDeviceRegistrationClientMock();
        this.credentialsClient = createCredentialsClientMock();

        this.telemetrySender = createTelemetrySenderMock();
        this.eventSender = createEventSenderMock();
    }

    /**
     * Prepares the (mock) service clients with default behavior.
     * <p>
     * This method is separate from {@link #createClients()} in order to
     * support setups where the clients are created once for all test cases but
     * the client instances need to be (re-)set for each individual test case.
     * <p>
     * This method configures
     * <ul>
     * <li>the Tenant client to always return a succeeded future containing a TenantObject
     * for the given tenant</li>
     * <li>the Device Registration service client to always return a succeeded future containing
     * a {@link RegistrationAssertion} for the given device</li>
     * </ul>
     */
    protected void prepareClients() {

        if (tenantClient != null) {
            when(tenantClient.get(anyString(), (SpanContext) any())).thenAnswer(invocation -> {
                return Future.succeededFuture(TenantObject.from(invocation.getArgument(0), true));
            });
        }

        if (registrationClient != null) {
            when(registrationClient.assertRegistration(anyString(), anyString(), any(), (SpanContext) any()))
            .thenAnswer(invocation -> {
                final String deviceId = invocation.getArgument(1);
                final RegistrationAssertion regAssertion = new RegistrationAssertion(deviceId);
                return Future.succeededFuture(regAssertion);
            });
        }
    }

    /**
     * Creates a mocked context for a one-way command.
     *
     * @param tenantId The tenant that the command's target device belongs to.
     * @param deviceId The target device's identifier.
     * @param name The command name.
     * @param contentType The type of the payload or {@code null} if the command has no payload.
     * @param payload The command's payload.
     * @return The mocked context.
     */
    protected CommandContext givenAOneWayCommandContext(
            final String tenantId,
            final String deviceId,
            final String name,
            final String contentType,
            final Buffer payload) {

        final Command command = newOneWayCommand(tenantId, deviceId, name, contentType, payload);

        final CommandContext context = mock(CommandContext.class);
        when(context.getCommand()).thenReturn(command);
        when(context.getTracingSpan()).thenReturn(NoopSpan.INSTANCE);
        return context;
    }

    /**
     * Creates a mocked context for a request-response command.
     *
     * @param tenantId The tenant that the command's target device belongs to.
     * @param deviceId The target device's identifier.
     * @param name The command name.
     * @param contentType The type of the payload or {@code null} if the command has no payload.
     * @param payload The command's payload.
     * @param replyToId The reply-to-id to use.
     * @param messagingType The type of messaging system to use.
     * @return The mocked context.
     */
    protected CommandContext givenARequestResponseCommandContext(
            final String tenantId,
            final String deviceId,
            final String name,
            final String replyToId,
            final String contentType,
            final Buffer payload,
            final MessagingType messagingType) {

        final Command command = newRequestResponseCommand(tenantId, deviceId, name, replyToId, contentType, payload, messagingType);

        final CommandContext context = mock(CommandContext.class);
        when(context.getCommand()).thenReturn(command);
        when(context.getTracingSpan()).thenReturn(NoopSpan.INSTANCE);
        return context;
    }

    private Command newOneWayCommand(
            final String tenantId,
            final String deviceId,
            final String name,
            final String contentType,
            final Buffer payload) {

        final Command command = mock(Command.class);
        when(command.getTenant()).thenReturn(tenantId);
        when(command.getGatewayOrDeviceId()).thenReturn(deviceId);
        when(command.getDeviceId()).thenReturn(deviceId);
        when(command.getName()).thenReturn(name);
        when(command.getContentType()).thenReturn(contentType);
        when(command.getPayload()).thenReturn(payload);
        when(command.getPayloadSize()).thenReturn(Optional.ofNullable(payload).map(Buffer::length).orElse(0));
        when(command.isOneWay()).thenReturn(true);
        when(command.isValid()).thenReturn(true);
        return command;
    }

    private Command newRequestResponseCommand(
            final String tenantId,
            final String deviceId,
            final String name,
            final String replyToId,
            final String contentType,
            final Buffer payload,
            final MessagingType messagingType) {

        final Command command = newOneWayCommand(tenantId, deviceId, name, contentType, payload);
        when(command.isOneWay()).thenReturn(false);
        when(command.getRequestId())
                .thenReturn(Commands.encodeRequestIdParameters("correlation-id", replyToId, deviceId, messagingType));
        return command;
    }

    /**
     * Configures the telemetry sender to always return a succeeded future regardless of tenant ID.
     *
     * @return The configured sender.
     */
    protected TelemetrySender givenATelemetrySenderForAnyTenant() {
        final Promise<Void> delivery = Promise.promise();
        delivery.complete();
        return givenATelemetrySenderForAnyTenant(delivery);
    }

    /**
     * Configures the telemetry sender to return a given promise's
     * corresponding future regardless of tenant ID.
     *
     * @param outcome The outcome of sending a message using the returned sender.
     * @return The configured sender.
     */
    protected TelemetrySender givenATelemetrySenderForAnyTenant(final Promise<Void> outcome) {
        when(this.telemetrySender.sendTelemetry(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                any(org.eclipse.hono.util.QoS.class),
                any(),
                any(),
                any(),
                any())).thenReturn(outcome.future());
        return this.telemetrySender;
    }

    /**
     * Configures the event sender to always return a succeeded future regardless of tenant ID.
     *
     * @return The configured sender.
     */
    protected EventSender givenAnEventSenderForAnyTenant() {
        final Promise<Void> delivery = Promise.promise();
        delivery.complete();
        return givenAnEventSenderForAnyTenant(delivery);
    }


    /**
     * Configures the event sender to return a given promise's
     * corresponding future regardless of tenant ID.
     *
     * @param outcome The outcome of sending a message using the returned sender.
     * @return The configured sender.
     */
    protected EventSender givenAnEventSenderForAnyTenant(final Promise<Void> outcome) {
        when(this.eventSender.sendEvent(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                any(),
                any(),
                any(),
                any())).thenReturn(outcome.future());
        return this.eventSender;
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
        final Promise<Void> delivery = Promise.promise();
        delivery.complete();
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
    protected CommandResponseSender givenACommandResponseSenderForAnyTenant(final Promise<Void> outcome) {
        when(commandResponseSender.sendCommandResponse(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                any(CommandResponse.class),
                (SpanContext) any()))
            .thenReturn(outcome.future());
        return commandResponseSender;
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

        final ArgumentCaptor<TenantObject> tenantCaptor = ArgumentCaptor.forClass(TenantObject.class);
        final ArgumentCaptor<RegistrationAssertion> assertionCaptor = ArgumentCaptor.forClass(RegistrationAssertion.class);
        final ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);

        verify(telemetrySender).sendTelemetry(
                tenantCaptor.capture(),
                assertionCaptor.capture(),
                eq(qos),
                contentTypeCaptor.capture(),
                any(),
                any(),
                any());

        Optional.ofNullable(tenant)
            .ifPresent(v -> {
                assertThat(tenantCaptor.getValue().getTenantId()).isEqualTo(v);
            });
        Optional.ofNullable(deviceId)
            .ifPresent(v -> {
                assertThat(assertionCaptor.getValue().getDeviceId()).isEqualTo(v);
            });
        Optional.ofNullable(contentType)
            .ifPresent(v -> {
                assertThat(contentTypeCaptor.getValue()).ignoringCase().isEqualTo(v);
            });
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

        verify(eventSender).sendEvent(
                argThat(tenantObject -> tenantObject.getTenantId().equals(tenant)),
                argThat(assertion -> assertion.getDeviceId().equals(deviceId)),
                eq(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION),
                any(),
                argThat(props -> ttd.equals(props.get(CommandConstants.MSG_PROPERTY_DEVICE_TTD))),
                any());
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

        verify(eventSender, never()).sendEvent(
                argThat(tenantObject -> tenantObject.getTenantId().equals(tenant)),
                argThat(assertion -> assertion.getDeviceId().equals(deviceId)),
                eq(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION),
                any(),
                argThat(props -> ttd.equals(props.get(CommandConstants.MSG_PROPERTY_DEVICE_TTD))),
                any());
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

        final ArgumentCaptor<TenantObject> tenantCaptor = ArgumentCaptor.forClass(TenantObject.class);
        final ArgumentCaptor<RegistrationAssertion> assertionCaptor = ArgumentCaptor.forClass(RegistrationAssertion.class);
        final ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Map<String, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);

        verify(eventSender).sendEvent(
                tenantCaptor.capture(),
                assertionCaptor.capture(),
                contentTypeCaptor.capture(),
                any(),
                propsCaptor.capture(),
                any());

        Optional.ofNullable(tenant)
            .ifPresent(v -> {
                assertThat(tenantCaptor.getValue().getTenantId()).isEqualTo(v);
            });
        Optional.ofNullable(deviceId)
            .ifPresent(v -> {
                assertThat(assertionCaptor.getValue().getDeviceId()).isEqualTo(v);
            });
        Optional.ofNullable(contentType)
            .ifPresent(v -> {
                assertThat(contentTypeCaptor.getValue()).ignoringCase().isEqualTo(v);
            });
        Optional.ofNullable(ttl)
            .ifPresent(v -> {
                assertThat(propsCaptor.getValue()).containsAtLeastEntriesIn(Map.of(MessageHelper.SYS_HEADER_PROPERTY_TTL, v));
            });
    }

    /**
     * Asserts that no message has been sent using the telemetry sender.
     *
     * @throws AssertionError if a message has been sent.
     */
    protected void assertNoTelemetryMessageHasBeenSentDownstream() {
        verify(telemetrySender, never()).sendTelemetry(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                any(QoS.class),
                anyString(),
                any(),
                any(),
                any());
    }

    /**
     * Asserts that no message has been sent using the event sender.
     *
     * @throws AssertionError if a message has been sent.
     */
    protected void assertNoEventHasBeenSentDownstream() {
        verify(eventSender, never()).sendEvent(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                anyString(),
                any(),
                any(),
                any());
    }

    /**
     * Asserts that no command response message has been sent using the command response sender.
     *
     * @throws AssertionError if a message has been sent.
     */
    protected void assertNoCommandResponseHasBeenSentDownstream() {
        verify(commandResponseSender, never()).sendCommandResponse(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                any(CommandResponse.class),
                any());
    }
}
