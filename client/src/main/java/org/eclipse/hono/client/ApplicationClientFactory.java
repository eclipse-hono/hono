/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;

/**
 * A factory for creating clients for Hono's north bound APIs.
 *
 */
public interface ApplicationClientFactory extends ConnectionLifecycle {

    /**
     * Creates a client for consuming data from Hono's north bound <em>Telemetry API</em>.
     *
     * @param tenantId The tenant to consume data for.
     * @param telemetryConsumer The handler to invoke with every message received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this client is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<MessageConsumer> createTelemetryConsumer(
            String tenantId,
            Consumer<Message> telemetryConsumer,
            Handler<Void> closeHandler);

    /**
     * Creates a client for consuming events from Hono's north bound <em>Event API</em>.
     * <p>
     * The events passed in to the event consumer will be settled automatically if the consumer does not throw an
     * exception and does not manually handle the message disposition using the passed in delivery.
     *
     * @param tenantId The tenant to consume events for.
     * @param eventConsumer The handler to invoke with every event received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this client is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<MessageConsumer> createEventConsumer(
            String tenantId,
            BiConsumer<ProtonDelivery, Message> eventConsumer,
            Handler<Void> closeHandler);

    /**
     * Creates a client for consuming events from Hono's north bound <em>Event API</em>.
     * <p>
     * The events passed in to the event consumer will be settled automatically if the consumer does not throw an
     * exception.
     *
     * @param tenantId The tenant to consume events for.
     * @param eventConsumer The handler to invoke with every event received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this client is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<MessageConsumer> createEventConsumer(
            String tenantId,
            Consumer<Message> eventConsumer,
            Handler<Void> closeHandler);

    /**
     * Gets a client for sending commands to a device.
     * <p>
     * The client returned may be either newly created or it may be an existing
     * client for the given device.
     * <p>
     * This method will use an implementation specific mechanism (e.g. a UUID) to create
     * a unique reply-to address to be included in commands sent to the device. The protocol
     * adapters need to convey an encoding of the reply-to address to the device when delivering
     * the command. Consequently, the number of bytes transferred to the device depends on the
     * length of the reply-to address being used. In situations where this is a major concern it
     * might be advisable to use {@link #getOrCreateCommandClient(String, String, String)} for
     * creating a command client and provide custom (and shorter) <em>reply-to identifier</em>
     * to be used in the reply-to address.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the commands to.
     * @return A future that will complete with the command and control client (if successful) or
     *         fail if the client cannot be created, e.g. because the underlying connection
     *         is not established or if a concurrent request to create a client for the same
     *         tenant and device is already being executed.
     * @throws NullPointerException if the tenantId is {@code null}.
     */
    Future<CommandClient> getOrCreateCommandClient(String tenantId, String deviceId);

    /**
     * Gets a client for sending commands to a device.
     * <p>
     * The client returned may be either newly created or it may be an existing
     * client for the given device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the commands to.
     * @param replyId An arbitrary string which (in conjunction with the tenant and device ID) uniquely
     *                identifies this command client.
     *                This identifier will only be used for creating a <em>new</em> client for the device.
     *                If this method returns an existing client for the device then the client will use
     *                the reply-to address determined during its initial creation. In particular, this
     *                means that if the (existing) client has originally been created using the
     *                {@link #getOrCreateCommandClient(String, String)} method, then the reply-to address
     *                used by the client will most likely not contain the given identifier.
     * @return A future that will complete with the command and control client (if successful) or
     *         fail if the client cannot be created, e.g. because the underlying connection
     *         is not established or if a concurrent request to create a client for the same
     *         tenant and device is already being executed.
     * @throws NullPointerException if the tenantId is {@code null}.
     */
    Future<CommandClient> getOrCreateCommandClient(String tenantId, String deviceId, String replyId);
}
