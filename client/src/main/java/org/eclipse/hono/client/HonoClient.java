/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.client;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;

/**
 * A factory for creating clients for Hono's arbitrary APIs.
 * <p>
 * A factory maintains a single AMQP 1.0 connection and a single
 * session to the peer. This session is shared by all AMQP 1.0 links
 * established for <em>senders</em>, <em>consumers</em> and <em>clients</em>
 * created using the factory methods.
 * <p>
 * The <em>getOrCreate</em> factory methods return an existing client for the
 * given address if available. Note that factory methods for creating consumers
 * <em>always</em> return a new instance so that all messages received are processed
 * by the given handler passed in to the factory method.
 */
public interface HonoClient {

    /**
     * Checks whether this client is connected to the Hono server.
     * 
     * @return {@code true} if this client is connected.
     */
    boolean isConnected();

    /**
     * Connects to the Hono server using given options.
     * 
     * @param options The options to use (may be {@code null}).
     * @param connectionHandler The handler to notify about the outcome of the connection attempt. Always fails if one
     *            of the shutdown methods was called before.
     * @throws NullPointerException if the connection handler is {@code null}.
     */
    void connect(ProtonClientOptions options, Handler<AsyncResult<HonoClient>> connectionHandler);

    /**
     * Connects to the Hono server using given options.
     * 
     * @param options The options to use (may be {@code null}).
     * @param connectionHandler The handler to notify about the outcome of the connection attempt. Always fails if one
     *            of the shutdown methods was called before.
     * @param disconnectHandler A handler to notify about connection loss (may be {@code null}).
     * @throws NullPointerException if the connection handler is {@code null}.
     */
    void connect(
            ProtonClientOptions options,
            Handler<AsyncResult<HonoClient>> connectionHandler,
            Handler<ProtonConnection> disconnectHandler);

    /**
     * Gets a client for sending telemetry messages to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send messages for.
     * @param resultHandler The handler to notify about the client.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void getOrCreateTelemetrySender(String tenantId, Handler<AsyncResult<MessageSender>> resultHandler);

    /**
     * Gets a client for sending telemetry messages to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send messages for.
     * @param deviceId The ID of the device to send events for (may be {@code null}).
     * @param resultHandler The handler to notify about the client.
     * @throws NullPointerException if any of the tenantId or resultHandler is {@code null}.
     */
    void getOrCreateTelemetrySender(String tenantId, String deviceId,
            Handler<AsyncResult<MessageSender>> resultHandler);

    /**
     * Gets a client for sending events to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send events for.
     * @param resultHandler The handler to notify about the client.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void getOrCreateEventSender(String tenantId, Handler<AsyncResult<MessageSender>> resultHandler);

    /**
     * Gets a client for sending events to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send events for.
     * @param deviceId The ID of the device to send events for (may be {@code null}).
     * @param resultHandler The handler to notify about the client.
     * @throws NullPointerException if any of the tenantId or resultHandler is {@code null}.
     */
    void getOrCreateEventSender(
            String tenantId,
            String deviceId,
            Handler<AsyncResult<MessageSender>> resultHandler);

    /**
     * Creates a new consumer of telemetry data for a tenant.
     * 
     * @param tenantId The tenant to consume data for.
     * @param telemetryConsumer The handler to invoke with every message received.
     * @param creationHandler The handler to invoke with the outcome of the operation.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void createTelemetryConsumer(
            String tenantId,
            Consumer<Message> telemetryConsumer,
            Handler<AsyncResult<MessageConsumer>> creationHandler);

    /**
     * Creates a new consumer of events for a tenant.
     * <p>
     * The events passed in to the registered eventConsumer will be settled
     * automatically if the consumer does not throw an exception and does not
     * manually handle the message disposition using the passed in delivery.
     *
     * @param tenantId The tenant to consume events for.
     * @param eventConsumer The handler to invoke with every event received.
     * @param creationHandler The handler to invoke with the outcome of the operation.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void createEventConsumer(
            String tenantId,
            Consumer<Message> eventConsumer,
            Handler<AsyncResult<MessageConsumer>> creationHandler);

    /**
     * Creates a new consumer of events for a tenant.
     * <p>
     * The events passed in to the registered eventConsumer will be settled
     * automatically if the consumer does not throw an exception and does not
     * manually handle the message disposition using the passed in delivery.
     *
     * @param tenantId The tenant to consume events for.
     * @param eventConsumer The handler to invoke with every event received.
     * @param creationHandler The handler to invoke with the outcome of the operation.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void createEventConsumer(
            String tenantId,
            BiConsumer<ProtonDelivery, Message> eventConsumer,
            Handler<AsyncResult<MessageConsumer>> creationHandler);

    /**
     * Gets a client for interacting with Hono's <em>Registration</em> API.
     * 
     * @param tenantId The tenant to manage device registration data for.
     * @param resultHandler The handler to invoke with the result of the operation.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void getOrCreateRegistrationClient(
            String tenantId,
            Handler<AsyncResult<RegistrationClient>> resultHandler);

    /**
     * Gets a client for interacting with Hono's <em>Credentials</em> API.
     *
     * @param tenantId The tenant to manage device credentials data for.
     * @param resultHandler The handler to invoke with the result of the operation.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void getOrCreateCredentialsClient(
            String tenantId,
            Handler<AsyncResult<CredentialsClient>> resultHandler);

    /**
     * Closes this client's connection to the Hono server.
     * <p>
     * This method waits for at most 5 seconds for the connection to be closed properly. Any subsequent attempts to 
     * connect this client again will fail.
     */
    void shutdown();

    /**
     * Closes this client's connection to the Hono server.
     * <p>
     * Any senders or consumers opened by this client will be implicitly closed as well. Any subsequent attempts to 
     * connect this client again will fail.
     * 
     * @param completionHandler The handler to invoke with the result of the operation.
     * @throws NullPointerException if the handler is {@code null}.
     */
    void shutdown(Handler<AsyncResult<Void>> completionHandler);

}
