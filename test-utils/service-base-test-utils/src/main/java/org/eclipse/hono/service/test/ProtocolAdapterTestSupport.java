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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

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
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.monitoring.ConnectionEventProducer;
import org.junit.jupiter.api.BeforeEach;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
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

    protected ProtocolAdapterCommandConsumerFactory commandConsumerFactory;
    protected CommandTargetMapper commandTargetMapper;
    protected ConnectionEventProducer.Context connectionEventProducerContext;
    protected CredentialsClientFactory credentialsClientFactory;
    protected DeviceConnectionClientFactory deviceConnectionClientFactory;
    protected DownstreamSenderFactory downstreamSenderFactory;
    protected RegistrationClient registrationClient;
    protected RegistrationClientFactory registrationClientFactory;
    protected TenantClientFactory tenantClientFactory;

    /**
     * Sets up the adapter instance to be tested.
     */
    @BeforeEach
    protected void setUpAdapterInstance() {

        commandConsumerFactory = mock(ProtocolAdapterCommandConsumerFactory.class);
        when(commandConsumerFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(commandConsumerFactory.isConnected()).thenReturn(Future.succeededFuture());

        commandTargetMapper = mock(CommandTargetMapper.class);

        credentialsClientFactory = mock(CredentialsClientFactory.class);
        when(credentialsClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(credentialsClientFactory.isConnected()).thenReturn(Future.succeededFuture());

        deviceConnectionClientFactory = mock(DeviceConnectionClientFactory.class);
        when(deviceConnectionClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(deviceConnectionClientFactory.isConnected()).thenReturn(Future.succeededFuture());

        downstreamSenderFactory = mock(DownstreamSenderFactory.class);
        when(downstreamSenderFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(downstreamSenderFactory.isConnected()).thenReturn(Future.succeededFuture());

        registrationClientFactory = mock(RegistrationClientFactory.class);
        when(registrationClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(registrationClientFactory.isConnected()).thenReturn(Future.succeededFuture());

        registrationClient = mock(RegistrationClient.class);
        when(registrationClientFactory.getOrCreateRegistrationClient(anyString())).thenReturn(Future.succeededFuture(registrationClient));

        tenantClientFactory = mock(TenantClientFactory.class);
        when(tenantClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(tenantClientFactory.isConnected()).thenReturn(Future.succeededFuture());

        connectionEventProducerContext = mock(ConnectionEventProducer.Context.class);
        when(connectionEventProducerContext.getMessageSenderClient()).thenReturn(downstreamSenderFactory);
        when(connectionEventProducerContext.getTenantClientFactory()).thenReturn(tenantClientFactory);

        properties = givenDefaultConfigurationProperties();
    }

    /**
     * Creates default configuration for the adapter.
     *
     * @return The configuration properties.
     */
    protected abstract C givenDefaultConfigurationProperties();

    /**
     * Sets the (mock) collaborators on an adapter.
     *
     * @param adapter The adapter.
     */
    protected void setCollaborators(final T adapter) {
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
        final DownstreamSender sender = mock(DownstreamSender.class);
        when(sender.send(any(Message.class), any(SpanContext.class)))
            .thenReturn(outcome.future());
        when(sender.sendAndWaitForOutcome(any(Message.class), any(SpanContext.class)))
            .thenReturn(outcome.future());

        when(downstreamSenderFactory.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
        return sender;
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
        final DownstreamSender sender = mock(DownstreamSender.class);
        when(sender.sendAndWaitForOutcome(any(Message.class), (SpanContext) any())).thenReturn(outcome.future());

        when(downstreamSenderFactory.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(sender));
        return sender;
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
}
