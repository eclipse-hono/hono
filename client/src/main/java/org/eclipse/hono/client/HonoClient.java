/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
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

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.message.Message;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
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
 * created using the corresponding factory methods.
 * <p>
 * The <em>getOrCreate</em> factory methods return an existing client for the
 * given address if available. Note that factory methods for creating consumers
 * <em>always</em> return a new instance so that all messages received are only
 * processed by the handler passed in to the factory method.
 * <p>
 * Before any of the factory methods can be invoked successfully, the client needs
 * to connect to Hono. This is done by invoking any of the client's <em>connect</em> methods.
 */
public interface HonoClient {

    /**
     * Checks whether this client is connected to the service.
     * 
     * @return A succeeded future if this client is connected.
     *         Otherwise, the future will fail with a {@link ServerErrorException}.
     */
    Future<Void> isConnected();

    /**
     * Connects to the Hono server using default options.
     * <p>
     * Using the default options, the client will try to (re-)connect to the peer
     * an unlimited number of times.
     * 
     * @return A future that will succeed with the connected client once the connection has been established.
     *         The future will fail with a {@link ServiceInvocationException} if the connection cannot be established,
     *         e.g. because
     *         <ul>
     *         <li>authentication of the client failed, or</li>
     *         <li>one of the client's <em>shutdown</em> methods has been invoked before the
     *         connection could be established.</li>
     *         </ul>
     */
    Future<HonoClient> connect();

    /**
     * Connects to the Hono server using given options.
     * <p>
     * The number of times that the client tries to (re-)connect to the peer is determined
     * by the <em>reconnectAttempts</em> property of the given options. If set to -1 then
     * the client will try to (re-)connect an unlimited number of times.
     * 
     * @param options The options to use. If {@code null} a set of default properties will be used.
     * @return A future that will succeed with the connected client once the connection has been established.
     *         The future will fail with a {@link ServiceInvocationException} if the connection cannot be established,
     *         e.g. because
     *         <ul>
     *         <li>authentication of the client failed, or</li>
     *         <li>one of the client's <em>shutdown</em> methods has been invoked before the
     *         connection could be established, or</li>
     *         <li>the maximum number of (unsuccessful) (re-)connection attempts have been made.</li>
     *         </ul>
     * @throws NullPointerException if the options are {@code null}.
     */
    Future<HonoClient> connect(ProtonClientOptions options);

    /**
     * Connects to the Hono server using default options.
     * <p>
     * Using the default options, the client will try to <em>initially</em> connect to the peer
     * an unlimited number of times. When an established connection to the server fails, the
     * disconnect handler will be invoked. The client will <em>not</em> automatically try to
     * re-connect to the server in this case.
     * 
     * @param disconnectHandler A handler to notify about connection loss.
     * @return A future that will succeed with the connected client once the connection has been established.
     *         The future will fail with a {@link ServiceInvocationException} if the connection cannot be established,
     *         e.g. because
     *         <ul>
     *         <li>authentication of the client failed, or</li>
     *         <li>one of the client's <em>shutdown</em> methods has been invoked before the
     *         connection could be established.</li>
     *         </ul>
     * @throws NullPointerException if the disconnect handler is {@code null}.
     */
    Future<HonoClient> connect(Handler<ProtonConnection> disconnectHandler);

    /**
     * Connects to the Hono server using given options.
     * <p>
     * The number of times that the client tries to <em>initially</em> connect to the peer
     * is determined by the <em>reconnectAttempts</em> property of the given options.
     * If set to -1 then the client will try to connect an unlimited number of times.
     * <p>
     * When an established connection to the server fails, the disconnect handler will be
     * invoked (if not {@code null}) and the client will <em>not</em> automatically try to
     * re-connect to the server in this case. If the disconnect handler is {@code null},
     * the client will try to re-connect to the server using the same number of attempts
     * as for the initial connection.
     * 
     * @param options The options to use. If {@code null} a set of default properties will be used.
     * @param disconnectHandler A handler to notify about connection loss (may be {@code null}).
     * @return A future that will succeed with the connected client once the connection has been established.
     *         The future will fail with a {@link ServiceInvocationException} if the connection cannot be established,
     *         e.g. because
     *         <ul>
     *         <li>authentication of the client failed, or</li>
     *         <li>one of the client's <em>shutdown</em> methods has been invoked before the
     *         connection could be established, or</li>
     *         <li>the maximum number of (unsuccessful) (re-)connection attempts have been made.</li>
     *         </ul>
     */
    Future<HonoClient> connect(
            ProtonClientOptions options,
            Handler<ProtonConnection> disconnectHandler);

    /**
     * Gets a client for sending telemetry messages to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send messages for.
     * @return A future that will complete with the sender once the link has been established.
     *         The future will fail if the link cannot be established, e.g. because this
     *         client is not connected or if a concurrent request to create a sender for the same
     *         tenant is already being executed.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    Future<MessageSender> getOrCreateTelemetrySender(String tenantId);

    /**
     * Gets a client for sending telemetry messages to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send messages for.
     * @param deviceId The ID of the device to send events for (may be {@code null}).
     * @return A future that will complete with the sender once the link has been established.
     *         The future will fail if the link cannot be established, e.g. because this
     *         client is not connected or if a concurrent request to create a sender for the same
     *         tenant and device is already being executed.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    Future<MessageSender> getOrCreateTelemetrySender(String tenantId, String deviceId);

    /**
     * Gets a client for sending events to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send events for.
     * @return A future that will complete with the sender once the link has been established.
     *         The future will fail if the link cannot be established, e.g. because this
     *         client is not connected or if a concurrent request to create a sender for the same
     *         tenant is already being executed.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    Future<MessageSender> getOrCreateEventSender(String tenantId);

    /**
     * Gets a client for sending events to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send events for.
     * @param deviceId The ID of the device to send events for (may be {@code null}).
     * @return A future that will complete with the sender once the link has been established.
     *         The future will fail if the link cannot be established, e.g. because this
     *         client is not connected or if a concurrent request to create a sender for the same
     *         tenant and device is already being executed.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    Future<MessageSender> getOrCreateEventSender(String tenantId, String deviceId);

    /**
     * Creates a new consumer of telemetry data for a tenant.
     * 
     * @param tenantId The tenant to consume data for.
     * @param telemetryConsumer The handler to invoke with every message received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established.
     *         The future will fail if the link cannot be established, e.g. because this
     *         client is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<MessageConsumer> createTelemetryConsumer(String tenantId, Consumer<Message> telemetryConsumer,
            Handler<Void> closeHandler);

    /**
     * Creates a new consumer of events for a tenant.
     * <p>
     * The events passed in to the event consumer will be settled
     * automatically if the consumer does not throw an exception.
     *
     * @param tenantId The tenant to consume events for.
     * @param eventConsumer The handler to invoke with every event received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established.
     *         The future will fail if the link cannot be established, e.g. because this
     *         client is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<MessageConsumer> createEventConsumer(String tenantId, Consumer<Message> eventConsumer,
            Handler<Void> closeHandler);

    /**
     * Creates a new consumer of events for a tenant.
     * <p>
     * The events passed in to the event consumer will be settled
     * automatically if the consumer does not throw an exception and does not
     * manually handle the message disposition using the passed in delivery.
     *
     * @param tenantId The tenant to consume events for.
     * @param eventConsumer The handler to invoke with every event received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established.
     *         The future will fail if the link cannot be established, e.g. because this
     *         client is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<MessageConsumer> createEventConsumer(String tenantId, BiConsumer<ProtonDelivery, Message> eventConsumer,
            Handler<Void> closeHandler);

    /**
     * Gets a client for invoking operations on a service implementing
     * Hono's <em>Device Registration</em> API.
     * 
     * @param tenantId The tenant to manage device registration data for.
     * @return A future that will complete with the registration client (if successful) or
     *         fail if the client cannot be created, e.g. because the underlying connection
     *         is not established or if a concurrent request to create a client for the same
     *         tenant is already being executed.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    Future<RegistrationClient> getOrCreateRegistrationClient(String tenantId);

    /**
     * Gets a client for interacting with Hono's <em>Credentials</em> API.
     *
     * @param tenantId The tenant to manage device credentials data for.
     * @return A future that will complete with the credentials client (if successful) or
     *         fail if the client cannot be created, e.g. because the underlying connection
     *         is not established or if a concurrent request to create a client for the same
     *         tenant is already being executed.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    Future<CredentialsClient> getOrCreateCredentialsClient(String tenantId);

    /**
     * Gets a client for interacting with Hono's <em>Tenant</em> API.
     *
     * @return A future that will complete with the tenant client (if successful) or
     *         fail if the client cannot be created, e.g. because the underlying connection
     *         is not established or if a concurrent request to create a client for the same
     *         tenant is already being executed.
     */
    Future<TenantClient> getOrCreateTenantClient();

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

    /**
     * Checks if this client supports a certain capability.
     * <p>
     * The result of this method should only be considered reliable
     * if this client is connected to the server.
     * 
     * @param capability The capability to check support for.
     * @return {@code true} if the capability is included in the list of
     *         capabilities that the server has offered in its AMQP <em>open</em>
     *         frame, {@code false} otherwise.
     */
    boolean supportsCapability(Symbol capability);
}
