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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.adapter.client.command.DeviceConnectionClient;
import org.eclipse.hono.adapter.client.registry.CredentialsClient;
import org.eclipse.hono.adapter.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.mockito.ArgumentCaptor;

import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
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

    protected CommandTargetMapper commandTargetMapper;
    protected CredentialsClient credentialsClient;
    protected DeviceConnectionClient deviceConnectionClient;
    protected EventSender eventSender;
    protected ProtocolAdapterCommandConsumerFactory commandConsumerFactory;
    protected DeviceRegistrationClient registrationClient;
    protected TenantClient tenantClient;
    protected TelemetrySender telemetrySender;

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

    private DeviceConnectionClient createDeviceConnectionClientMock() {
        final DeviceConnectionClient client = mock(DeviceConnectionClient.class);
        when(client.start()).thenReturn(Future.succeededFuture());
        when(client.stop()).thenReturn(Future.succeededFuture());
        return client;
    }

    private TelemetrySender createTelemetrySenderMock() {
        final TelemetrySender sender = mock(TelemetrySender.class);
        when(sender.start()).thenReturn(Future.succeededFuture());
        when(sender.stop()).thenReturn(Future.succeededFuture());
        return sender;
    }

    private EventSender createEventSenderMock() {
        final EventSender sender = mock(EventSender.class);
        when(sender.start()).thenReturn(Future.succeededFuture());
        when(sender.stop()).thenReturn(Future.succeededFuture());
        return sender;
    }

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

        this.deviceConnectionClient = createDeviceConnectionClientMock();
        this.tenantClient = createTenantClientMock();
        this.registrationClient = createDeviceRegistrationClientMock();
        this.credentialsClient = createCredentialsClientMock();

        commandTargetMapper = mock(CommandTargetMapper.class);
        this.telemetrySender = createTelemetrySenderMock();
        this.eventSender = createEventSenderMock();
    }

    /**
     * Prepares the (mock) service clients with default behavior.
     * <p>
     * This method is separate from {@link #createClientFactories()} in order to
     * support setups where the factories/clients are created once for all test cases but
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
            when(tenantClient.get(anyString(), any(SpanContext.class))).thenAnswer(invocation -> {
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
     * Sets the (mock) service clients on an adapter.
     *
     * @param adapter The adapter.
     */
    protected void setServiceClients(final T adapter) {
        adapter.setCommandConsumerFactory(commandConsumerFactory);
        adapter.setCommandTargetMapper(commandTargetMapper);
        adapter.setCredentialsClient(credentialsClient);
        adapter.setDeviceConnectionClient(deviceConnectionClient);
        adapter.setEventSender(eventSender);
        adapter.setRegistrationClient(registrationClient);
        adapter.setTelemetrySender(telemetrySender);
        adapter.setTenantClient(tenantClient);
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
        this.telemetrySender = createTelemetrySenderMock();
        when(this.telemetrySender.sendTelemetry(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                any(org.eclipse.hono.util.QoS.class),
                anyString(),
                any(),
                any(),
                any())).thenReturn(outcome.future());
        this.adapter.setTelemetrySender(this.telemetrySender);
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
        this.eventSender = createEventSenderMock();
        when(this.eventSender.sendEvent(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                anyString(),
                any(),
                any(),
                any())).thenReturn(outcome.future());
        this.adapter.setEventSender(this.eventSender);
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
                assertThat(contentTypeCaptor.getValue()).isEqualToIgnoringCase(v);
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
                argThat(props -> ttd.equals(props.get(MessageHelper.APP_PROPERTY_DEVICE_TTD))),
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
                argThat(props -> ttd.equals(props.get(MessageHelper.APP_PROPERTY_DEVICE_TTD))),
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
                assertThat(contentTypeCaptor.getValue()).isEqualToIgnoringCase(v);
            });
        Optional.ofNullable(ttl)
            .ifPresent(v -> {
                assertThat(propsCaptor.getValue()).contains(Map.entry(MessageHelper.SYS_HEADER_PROPERTY_TTL, v));
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
}
