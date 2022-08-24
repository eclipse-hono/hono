/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.device.amqp.impl;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.GenericSenderLink;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.ConnectionLifecycleWrapper;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.command.CommandConsumer;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClient;
import org.eclipse.hono.client.util.CachingClientFactory;
import org.eclipse.hono.client.util.ClientFactory;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * A <code>vertx-proton</code> based factory for creating clients for interacting with Hono's AMQP adapter.
 * <p>
 * Sender links are being cached.
 */
public final class ProtonBasedAmqpAdapterClient extends ConnectionLifecycleWrapper<HonoConnection>
        implements AmqpAdapterClient {

    private final HonoConnection connection;
    private final CachingClientFactory<GenericSenderLink> telemetrySenderClientFactory;
    private final CachingClientFactory<GenericSenderLink> eventSenderClientFactory;
    private final CachingClientFactory<GenericSenderLink> commandResponseSenderClientFactory;
    private final ClientFactory<CommandConsumer> commandConsumerFactory;

    /**
     * Creates a new factory instance for an existing connection and a given tenant.
     *
     * @param connection The connection to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtonBasedAmqpAdapterClient(final HonoConnection connection) {
        super(connection);
        this.connection = Objects.requireNonNull(connection);
        this.connection.addDisconnectListener(con -> onDisconnect());

        telemetrySenderClientFactory = new CachingClientFactory<>(connection.getVertx(), GenericSenderLink::isOpen);
        eventSenderClientFactory = new CachingClientFactory<>(connection.getVertx(), GenericSenderLink::isOpen);
        commandResponseSenderClientFactory = new CachingClientFactory<>(connection.getVertx(), GenericSenderLink::isOpen);
        commandConsumerFactory = new ClientFactory<>();
    }

    private void onDisconnect() {
        telemetrySenderClientFactory.onDisconnect();
        eventSenderClientFactory.onDisconnect();
        commandResponseSenderClientFactory.onDisconnect();
    }

    /**
     * Gets the default timeout used when checking whether this client factory is connected to the service.
     * <p>
     * The value returned here is the {@code org.eclipse.hono.amqp.config.ClientConfigProperties#getLinkEstablishmentTimeout()}.
     *
     * @return The timeout value in milliseconds.
     */
    private long getDefaultConnectionCheckTimeout() {
        return connection.getConfig().getLinkEstablishmentTimeout();
    }

    private Future<GenericSenderLink> getOrCreateGenericTelemetrySender() {

        final String cacheKey = TelemetryConstants.TELEMETRY_ENDPOINT;

        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    telemetrySenderClientFactory.getOrCreateClient(
                            cacheKey,
                            () -> GenericSenderLink.create(
                                    connection,
                                    onSenderClosed -> telemetrySenderClientFactory.removeClient(cacheKey)),
                            result);
                }));
    }

    private Future<GenericSenderLink> getOrCreateGenericEventSender() {

        final String cacheKey = EventConstants.EVENT_ENDPOINT;

        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    eventSenderClientFactory.getOrCreateClient(
                            cacheKey,
                            () -> GenericSenderLink.create(
                                    connection,
                                    onSenderClosed -> telemetrySenderClientFactory.removeClient(cacheKey)),
                            result);
                }));
    }

    private Future<GenericSenderLink> getOrCreateCommandResponseSender() {

        final String cacheKey = CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT;

        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    commandResponseSenderClientFactory.getOrCreateClient(
                            cacheKey,
                            () -> GenericSenderLink.create(
                                    connection,
                                    onSenderClosed -> commandResponseSenderClientFactory.removeClient(cacheKey)),
                            result);
                }));
    }

    private Span createSpan(
            final String operationName,
            final String tenantId,
            final String deviceId,
            final SpanContext context) {

        Objects.requireNonNull(operationName);

        final Span span = TracingHelper
                .buildChildSpan(connection.getTracer(), context, operationName, getClass().getSimpleName())
                .ignoreActiveSpan()
                .withTag(Tags.PEER_HOSTNAME.getKey(), connection.getConfig().getHost())
                .withTag(Tags.PEER_PORT.getKey(), connection.getConfig().getPort())
                .withTag(TracingHelper.TAG_PEER_CONTAINER.getKey(), connection.getRemoteContainerId())
                .start();
        TracingHelper.setDeviceTags(span, tenantId, deviceId);
        return span;
    }

    /**
     * Creates the message to be sent to the AMQP adapter.
     *
     * @param payload The data to send.
     *            <p>
     *            The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload (may be {@code null}).
     *            <p>
     *            This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param targetAddress The address to send the message to.
     * @return The message.
     */
    private Message createAmqpMessage(
            final Buffer payload,
            final String contentType,
            final String targetAddress) {

        final Message msg = ProtonHelper.message();
        msg.setAddress(targetAddress);
        AmqpUtils.setCreationTime(msg);
        Optional.ofNullable(contentType).ifPresent(msg::setContentType);
        Optional.ofNullable(payload)
            .map(Buffer::getBytes)
            .map(Binary::new)
            .map(Data::new)
            .ifPresent(msg::setBody);
        return msg;
    }

    private void checkDeviceSpec(final String tenantId, final String deviceId) {
        if (tenantId != null && deviceId == null) {
            throw new IllegalArgumentException("device ID is required if tenant ID is not null");
        }
    }

    @Override
    public Future<ProtonDelivery> sendTelemetry(
            final QoS qos,
            final Buffer payload,
            final String contentType,
            final String tenantId,
            final String deviceId,
            final SpanContext context) {

        Objects.requireNonNull(qos);

        checkDeviceSpec(tenantId, deviceId);
        return getOrCreateGenericTelemetrySender()
                .compose(sender -> {
                    final var currentSpan = createSpan("send telemetry", tenantId, deviceId, context);
                    final var message = createAmqpMessage(
                            payload,
                            contentType,
                            ResourceIdentifier.fromPath(
                                    TelemetryConstants.TELEMETRY_ENDPOINT,
                                    tenantId,
                                    deviceId)
                                .toString());
                    if (qos == QoS.AT_MOST_ONCE) {
                        return sender.send(message, currentSpan);
                    } else {
                        return sender.sendAndWaitForOutcome(message, currentSpan);
                    }
                });
    }

    @Override
    public Future<ProtonDelivery> sendEvent(
            final Buffer payload,
            final String contentType,
            final String tenantId,
            final String deviceId,
            final SpanContext context) {

        checkDeviceSpec(tenantId, deviceId);
        return getOrCreateGenericEventSender()
                .compose(sender -> {
                    final var currentSpan = createSpan("send event", tenantId, deviceId, context);
                    final var message = createAmqpMessage(
                            payload,
                            contentType,
                            ResourceIdentifier.fromPath(
                                    EventConstants.EVENT_ENDPOINT,
                                    tenantId,
                                    deviceId)
                                .toString());
                     return sender.sendAndWaitForOutcome(message, currentSpan);
                });
    }

    @Override
    public Future<CommandConsumer> createDeviceSpecificCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Consumer<Message> messageHandler) {

        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(messageHandler);

        return connection.executeOnContext(result -> commandConsumerFactory.createClient(
                () -> AmqpAdapterClientCommandConsumer.create(
                        connection,
                        tenantId,
                        deviceId,
                        (delivery, message) -> messageHandler.accept(message)),
                result));
    }

    @Override
    public Future<CommandConsumer> createCommandConsumer(final Consumer<Message> messageHandler) {

        Objects.requireNonNull(messageHandler);

        return connection.executeOnContext(result -> commandConsumerFactory.createClient(
                () -> AmqpAdapterClientCommandConsumer.create(
                        connection,
                        (delivery, message) -> messageHandler.accept(message)),
                result));
    }

    @Override
    public Future<ProtonDelivery> sendCommandResponse(
            final String targetAddress,
            final String correlationId,
            final int status,
            final Buffer payload,
            final String contentType,
            final SpanContext context) {

        Objects.requireNonNull(targetAddress);
        Objects.requireNonNull(correlationId);

        return getOrCreateCommandResponseSender()
                .compose(sender -> {
                    final var currentSpan = createSpan("send command response", null, null, context);
                    final var message = createAmqpMessage(
                            payload,
                            contentType,
                            targetAddress);
                    message.setCorrelationId(correlationId);
                    AmqpUtils.addStatus(message, status);
                    return sender.sendAndWaitForOutcome(message, currentSpan);
                });
    }
}
