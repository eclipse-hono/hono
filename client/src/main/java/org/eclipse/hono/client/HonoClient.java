/**
 * Copyright (c) 2016,2017 Bosch Software Innovations GmbH.
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

import java.util.Map;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * A factory for creating clients for Hono's arbitrary APIs.
 *
 */
public interface HonoClient {

    /**
     * Checks whether this client is connected to the Hono server.
     * 
     * @return {@code true} if this client is connected.
     */
    boolean isConnected();

    /**
     * Gets properties describing the status of the connection to the Hono server.
     * <p>
     * The returned map contains the following properties:
     * <ul>
     * <li><em>name</em> - The name being indicated as the <em>container-id</em> in the
     * client's AMQP <em>Open</em> frame.</li>
     * <li><em>connected</em> - A boolean indicating whether this client is currently connected
     * to the Hono server.</li>
     * <li><em>Hono server</em> - The host (either name or literal IP address) and port of the
     * server this client is configured to connect to.</li>
     * </ul>
     * 
     * @return The connection status properties.
     */
    Map<String, Object> getConnectionStatus();

    /**
     * Gets a list of all senders and their current status.
     * <p>
     * For each sender the following properties are contained:
     * <ol>
     * <li>address - the link target address</li>
     * <li>open - indicates whether the link is (still) open</li>
     * <li>credit - the link-credit available</li>
     * </ol>
     * 
     * @return The status information.
     */
    JsonArray getSenderStatus();

    /**
     * Connects to the Hono server using given options.
     * 
     * @param options The options to use (may be {@code null}).
     * @param connectionHandler The handler to notify about the outcome of the connection attempt.
     * @return This client for command chaining.
     * @throws NullPointerException if the connection handler is {@code null}.
     */
    HonoClient connect(ProtonClientOptions options, Handler<AsyncResult<HonoClient>> connectionHandler);

    /**
     * Connects to the Hono server using given options.
     * 
     * @param options The options to use (may be {@code null}).
     * @param connectionHandler The handler to notify about the outcome of the connection attempt.
     * @param disconnectHandler A  handler to notify about connection loss (may be {@code null}).
     * @return This client for command chaining.
     * @throws NullPointerException if the connection handler is {@code null}.
     */
    HonoClient connect(
            ProtonClientOptions options,
            Handler<AsyncResult<HonoClient>> connectionHandler,
            Handler<ProtonConnection> disconnectHandler);

    /**
     * Gets a client for sending telemetry messages to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send messages for.
     * @param resultHandler The handler to notify about the client.
     * @return This for command chaining.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    HonoClient getOrCreateTelemetrySender(String tenantId, Handler<AsyncResult<MessageSender>> resultHandler);

    /**
     * Gets a client for sending telemetry messages to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send messages for.
     * @param deviceId The ID of the device to send events for (may be {@code null}).
     * @param resultHandler The handler to notify about the client.
     * @return This for command chaining.
     * @throws NullPointerException if any of the tenantId or resultHandler is {@code null}.
     */
    HonoClient getOrCreateTelemetrySender(String tenantId, String deviceId,
            Handler<AsyncResult<MessageSender>> resultHandler);

    /**
     * Gets a client for sending events to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send events for.
     * @param resultHandler The handler to notify about the client.
     * @return This for command chaining.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    HonoClient getOrCreateEventSender(String tenantId, Handler<AsyncResult<MessageSender>> resultHandler);

    /**
     * Gets a client for sending events to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send events for.
     * @param deviceId The ID of the device to send events for (may be {@code null}).
     * @param resultHandler The handler to notify about the client.
     * @return This for command chaining.
     * @throws NullPointerException if any of the tenantId or resultHandler is {@code null}.
     */
    HonoClient getOrCreateEventSender(
            String tenantId,
            String deviceId,
            Handler<AsyncResult<MessageSender>> resultHandler);

    /**
     * Creates a new consumer of telemetry data for a tenant.
     * 
     * @param tenantId The tenant to consume data for.
     * @param telemetryConsumer The handler to invoke with every message received.
     * @param creationHandler The handler to invoke with the outcome of the operation.
     * @return This client for command chaining.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    HonoClient createTelemetryConsumer(
            String tenantId,
            Consumer<Message> telemetryConsumer,
            Handler<AsyncResult<MessageConsumer>> creationHandler);

    /**
     * Creates a new consumer of events for a tenant.
     * 
     * @param tenantId The tenant to consume events for.
     * @param eventConsumer The handler to invoke with every event received.
     * @param creationHandler The handler to invoke with the outcome of the operation.
     * @return This client for command chaining.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    HonoClient createEventConsumer(
            String tenantId,
            Consumer<Message> eventConsumer,
            Handler<AsyncResult<MessageConsumer>> creationHandler);

    /**
     * Gets a client for interacting with Hono's <em>Registration</em> API.
     * 
     * @param tenantId The tenant to manage device registration data for.
     * @param resultHandler The handler to invoke with the result of the operation.
     * @return This client for command chaining.
     */
    HonoClient getOrCreateRegistrationClient(
            String tenantId,
            Handler<AsyncResult<RegistrationClient>> resultHandler);

    /**
     * Creates a new client for interacting with Hono's <em>Registration</em> API.
     * 
     * @param tenantId The tenant to manage device registration data for.
     * @param creationHandler The handler to invoke with the result of the operation.
     * @return This client for command chaining.
     */
    HonoClient createRegistrationClient(
            String tenantId,
            Handler<AsyncResult<RegistrationClient>> creationHandler);

    /**
     * Gets a client for interacting with Hono's <em>Credentials</em> API.
     *
     * @param tenantId The tenant to manage device credentials data for.
     * @param resultHandler The handler to invoke with the result of the operation.
     * @return This client for command chaining.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    HonoClient getOrCreateCredentialsClient(
            String tenantId,
            Handler<AsyncResult<CredentialsClient>> resultHandler);

    /**
     * Creates a new client for interacting with Hono's <em>Credentials</em> API.
     *
     * @param tenantId The tenant to manage device credentials data for.
     * @param creationHandler The handler to invoke with the result of the operation.
     * @return This client for command chaining.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    HonoClient createCredentialsClient(
            String tenantId,
            Handler<AsyncResult<CredentialsClient>> creationHandler);

    /**
     * Closes this client's connection to the Hono server.
     * <p>
     * This method waits for at most 5 seconds for the connection to be closed properly.
     */
    void shutdown();

    /**
     * Closes this client's connection to the Hono server.
     * <p>
     * Any senders or consumers opened by this client will be implicitly closed as well.
     * 
     * @param completionHandler The handler to invoke with the result of the operation.
     */
    void shutdown(Handler<AsyncResult<Void>> completionHandler);

}
