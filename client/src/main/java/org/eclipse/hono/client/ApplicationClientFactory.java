/**
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.impl.ApplicationClientFactoryImpl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;

/**
 * A factory for creating clients for Hono's north bound APIs.
 */
public interface ApplicationClientFactory extends ConnectionLifecycle<HonoConnection> {

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to use.
     * @return The factory.
     * @throws NullPointerException if connection is {@code null}
     */
    static ApplicationClientFactory create(final HonoConnection connection) {
        return create(connection, SendMessageSampler.Factory.noop());
    }

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to use.
     * @param samplerFactory The sampler to use.
     * @return The factory.
     * @throws NullPointerException if connection is {@code null}
     */
    static ApplicationClientFactory create(final HonoConnection connection, final SendMessageSampler.Factory samplerFactory) {
        return new ApplicationClientFactoryImpl(connection, samplerFactory);
    }

    /**
     * Creates a client for consuming data from Hono's north bound <em>Telemetry API</em>.
     *
     * @param tenantId The tenant to consume data for.
     * @param telemetryConsumer The handler to invoke with every message received.
     * @param autoAccept {@code true} if received deliveries should be automatically accepted (and settled)
     *                   after the message handler runs for them, if no other disposition has been applied
     *                   during handling. NOTE: When using {@code false} here, make sure that deliveries are
     *                   quickly updated and settled, so that the messages don't remain <em>in flight</em>
     *                   for long.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established.
     *         The future will fail if the link cannot be established, e.g. because this factory
     *         is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @deprecated Use {@code org.eclipse.hono.application.client.amqp.AmqpApplicationClientFactory} instead.
     */
    @Deprecated
    Future<MessageConsumer> createTelemetryConsumer(
            String tenantId,
            BiConsumer<ProtonDelivery, Message> telemetryConsumer,
            boolean autoAccept,
            Handler<Void> closeHandler);

    /**
     * Creates a client for consuming data from Hono's north bound <em>Telemetry API</em>.
     * <p>
     * The messages passed in to the consumer will be accepted and settled automatically if the consumer
     * does not throw an exception.
     *
     * @param tenantId The tenant to consume data for.
     * @param telemetryConsumer The handler to invoke with every message received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established.
     *         The future will fail if the link cannot be established, e.g. because this factory
     *         is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @deprecated Use {@code org.eclipse.hono.application.client.amqp.AmqpApplicationClientFactory} instead.
     */
    @Deprecated
    Future<MessageConsumer> createTelemetryConsumer(
            String tenantId,
            Consumer<Message> telemetryConsumer,
            Handler<Void> closeHandler);

    /**
     * Creates a client for consuming events from Hono's north bound <em>Event API</em>.
     *
     * @param tenantId The tenant to consume events for.
     * @param eventConsumer The handler to invoke with every event received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @param autoAccept {@code true} if received deliveries should be automatically accepted (and settled)
     *                   after the message handler runs for them, if no other disposition has been applied
     *                   during handling. NOTE: When using {@code false} here, make sure that deliveries are
     *                   quickly updated and settled, so that the messages don't remain <em>in flight</em>
     *                   for long.
     * @return A future that will complete with the consumer once the link has been established.
     *         The future will fail if the link cannot be established, e.g. because this factory
     *         is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @deprecated Use {@code org.eclipse.hono.application.client.amqp.AmqpApplicationClientFactory} instead.
     */
    @Deprecated
    Future<MessageConsumer> createEventConsumer(
            String tenantId,
            BiConsumer<ProtonDelivery, Message> eventConsumer,
            boolean autoAccept,
            Handler<Void> closeHandler);

    /**
     * Creates a client for consuming events from Hono's north bound <em>Event API</em>.
     * <p>
     * The events passed in to the event consumer will be accepted and settled automatically if
     * the consumer does not throw an exception and does not manually handle the message disposition
     * using the passed in delivery.
     *
     * @param tenantId The tenant to consume events for.
     * @param eventConsumer The handler to invoke with every event received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established.
     *         The future will fail if the link cannot be established, e.g. because this factory
     *         is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @deprecated Use {@code org.eclipse.hono.application.client.amqp.AmqpApplicationClientFactory} instead.
     */
    @Deprecated
    Future<MessageConsumer> createEventConsumer(
            String tenantId,
            BiConsumer<ProtonDelivery, Message> eventConsumer,
            Handler<Void> closeHandler);

    /**
     * Creates a client for consuming events from Hono's north bound <em>Event API</em>.
     * <p>
     * The events passed in to the event consumer will be accepted and settled automatically if
     * the consumer does not throw an exception.
     *
     * @param tenantId The tenant to consume events for.
     * @param eventConsumer The handler to invoke with every event received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established.
     *         The future will fail if the link cannot be established, e.g. because this factory
     *         is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @deprecated Use {@code org.eclipse.hono.application.client.amqp.AmqpApplicationClientFactory} instead.
     */
    @Deprecated
    Future<MessageConsumer> createEventConsumer(
            String tenantId,
            Consumer<Message> eventConsumer,
            Handler<Void> closeHandler);

    /**
     * Creates a client for consuming responses to commands that have been sent asynchronously
     * using Hono's north bound <em>Command &amp; Control API</em>.
     * <p>
     * The command responses passed in to the consumer will be accepted and settled automatically
     * if the consumer does not throw an exception.
     *
     * @param tenantId The tenant to consume command responses for.
     * @param replyId The replyId of commands to consume command responses for.
     * @param consumer The handler to invoke with every command response received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established.
     *         The future will fail if the link cannot be established, e.g. because this factory
     *         is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see org.eclipse.hono.client.AsyncCommandClient
     */
    Future<MessageConsumer> createAsyncCommandResponseConsumer(String tenantId, String replyId,
            Consumer<Message> consumer, Handler<Void> closeHandler);

    /**
     * Creates a client for consuming async command responses from Hono's north bound <em>Command API</em>.
     * <p>
     * The command responses passed in to the responses consumer will be accepted and settled automatically
     * if the consumer does not throw an exception and does not manually handle the message disposition using
     * the passed in delivery.
     *
     * @param tenantId The tenant to consume command responses for.
     * @param replyId The replyId of commands to consume command responses for.
     * @param consumer The handler to invoke with every command response received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established.
     *         The future will fail if the link cannot be established, e.g. because this client
     *         is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see org.eclipse.hono.client.AsyncCommandClient
     */
    Future<MessageConsumer> createAsyncCommandResponseConsumer(String tenantId, String replyId,
            BiConsumer<ProtonDelivery, Message> consumer, Handler<Void> closeHandler);

    /**
     * Gets a client for sending commands to devices of the given tenant.
     * <p>
     * The client returned may be either newly created or it may be an existing
     * client for the given tenant.
     * <p>
     * This method will use an implementation specific mechanism (e.g. a UUID) to create
     * a unique reply-to address to be included in commands sent to devices of the tenant.
     * The protocol adapters need to convey an encoding of the reply-to address to the device
     * when delivering the command. Consequently, the number of bytes transferred to the device
     * depends on the length of the reply-to address being used. In situations where this is a
     * major concern it might be advisable to use {@link #getOrCreateCommandClient(String, String)}
     * for creating a command client and provide custom (and shorter) <em>reply-to identifier</em>
     * to be used in the reply-to address.
     *
     * @param tenantId The tenant of the devices to which commands shall be sent.
     * @return A future that will complete with the command and control client (if successful) or
     *         fail if the client cannot be created, e.g. because the underlying connection
     *         is not established or if a concurrent request to create a client for the same
     *         tenant is already being executed.
     * @throws NullPointerException if the tenantId is {@code null}.
     */
    Future<CommandClient> getOrCreateCommandClient(String tenantId);

    /**
     * Gets a client for sending commands to devices of the given tenant.
     * <p>
     * The client returned may be either newly created or it may be an existing
     * client for the given tenant and replyId.
     *
     * @param tenantId The tenant of the devices to which commands shall be sent.
     * @param replyId An arbitrary string which will be used to create the reply-to address to be included in
     *                commands sent to devices of the tenant. The combination of tenant and replyId has to be
     *                unique among all CommandClient instances to make sure command response messages can be received.
     * @return A future that will complete with the command and control client (if successful) or
     *         fail if the client cannot be created, e.g. because the underlying connection
     *         is not established or if a concurrent request to create a client for the same
     *         tenant and replyId is already being executed.
     * @throws NullPointerException if tenantId or replyId is {@code null}.
     */
    Future<CommandClient> getOrCreateCommandClient(String tenantId, String replyId);

    /**
     * Gets a client for sending commands to devices of the given tenant asynchronously, i.e. command responses
     * get received by a separate receiver.
     * <p>
     * The client returned may be either newly created or it may be an existing client for the given tenant.
     *
     * @param tenantId The tenant of the devices to which commands shall be sent.
     * @return A future that will complete with the command client (if successful) or
     *         fail if the client cannot be created, e.g. because the underlying connection
     *         is not established or if a concurrent request to create a client for the same
     *         tenant is already being executed.
     * @throws NullPointerException if tenantId is {@code null}.
     */
    Future<AsyncCommandClient> getOrCreateAsyncCommandClient(String tenantId);
}
