/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.amqp.connection;

import java.util.UUID;

import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.impl.ConnectionFactoryImpl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * A factory for AMQP 1.0 connections.
 */
public interface ConnectionFactory {

    /**
     * Gets the host name of the server that this factory creates connections to.
     *
     * @return The host name or literal IP address.
     */
    String getHost();

    /**
     * Gets the port of the server that this factory creates connections to.
     *
     * @return The port number.
     */
    int getPort();

    /**
     * Gets the character sequence that the server uses for separating path components of target addresses.
     *
     * @return The path separator.
     */
    String getPathSeparator();

    /**
     * Gets the name of the role that the server plays from the client's perspective.
     *
     * @return The name or {@code null} if not set.
     */
    default String getServerRole() {
        return null;
    }

    /**
     * Connects to a server.
     *
     * @param options The client options to use for connecting. If {@code null} default options will be used.
     * @param closeHandler The handler to invoke when an AMQP <em>Close</em> frame is received from the server (may be
     *            {@code null}).
     * @param disconnectHandler The handler to invoke when the connection to the server is lost unexpectedly (may be
     *            {@code null}).
     * @return A future indicating the outcome of the connection attempt.
     * @throws NullPointerException if the result handler is {@code null}.
     */
    Future<ProtonConnection> connect(
            ProtonClientOptions options,
            Handler<AsyncResult<ProtonConnection>> closeHandler,
            Handler<ProtonConnection> disconnectHandler);

    /**
     * Connects to a server.
     *
     * @param options The client options to use for connecting. If {@code null} default options will be used.
     * @param username The username to use for authenticating to the server using SASL PLAIN. If {@code null}, the
     *                 default username defined for this factory will be used.
     * @param password The password to use for authenticating to the server using SASL PLAIN. If {@code null}, the
     *                 default password defined for this factory will be used.
     * @param closeHandler The handler to invoke when an AMQP <em>Close</em> frame is received from the server (may be
     *            {@code null}).
     * @param disconnectHandler The handler to invoke when the connection to the server is lost unexpectedly (may be
     *            {@code null}).
     * @return A future indicating the outcome of the connection attempt.
     * @throws NullPointerException if the result handler is {@code null}.
     */
    Future<ProtonConnection> connect(
            ProtonClientOptions options,
            String username,
            String password,
            Handler<AsyncResult<ProtonConnection>> closeHandler,
            Handler<ProtonConnection> disconnectHandler);

    /**
     * Connects to a server.
     *
     * @param options The client options to use for connecting. If {@code null} default options will be used.
     * @param username The username to use for authenticating to the server using SASL PLAIN. If {@code null}, the
     *                 default username defined for this factory will be used.
     * @param password The password to use for authenticating to the server using SASL PLAIN. If {@code null}, the
     *                 default password defined for this factory will be used.
     * @param containerId The container id to be advertised to the remove peer. If {@code null}, a generated
     *                    container id will be used.
     * @param closeHandler The handler to invoke when an AMQP <em>Close</em> frame is received from the server (may be
     *            {@code null}).
     * @param disconnectHandler The handler to invoke when the connection to the server is lost unexpectedly (may be
     *            {@code null}).
     * @return A future indicating the outcome of the connection attempt.
     * @throws NullPointerException if the result handler is {@code null}.
     */
    Future<ProtonConnection> connect(
            ProtonClientOptions options,
            String username,
            String password,
            String containerId,
            Handler<AsyncResult<ProtonConnection>> closeHandler,
            Handler<ProtonConnection> disconnectHandler);

    /**
     * Create a new {@link ConnectionFactory} using the default implementation.
     * <p>
     * <strong>Note:</strong> Instances of {@link ClientConfigProperties} are not thread safe and not immutable. They
     * must not be modified after calling this method.
     *
     * @param vertx The vertx instance to use. Must not be {@code null}.
     * @param clientConfigProperties The client properties to use. Must not be {@code null}.
     * @return A new instance of a connection factory.
     */
    static ConnectionFactory newConnectionFactory(final Vertx vertx,
            final ClientConfigProperties clientConfigProperties) {
        return new ConnectionFactoryImpl(vertx, clientConfigProperties);
    }

    /**
     * Creates a container id to be advertised to the remote peer.
     *
     * @param name The name part. If {@code null}, the string 'null' will be used.
     * @param serverRole The server role part. If {@code null}, that part will be omitted.
     * @param uuid The unique identifier to use. If {@code null}, a random UUID will be used.
     * @return The container id.
     */
    static String createContainerId(final String name, final String serverRole, final UUID uuid) {
        final UUID effectiveUuid = uuid == null ? UUID.randomUUID() : uuid;
        if (serverRole == null) {
            return String.format("%s-%s", name, effectiveUuid);
        }
        return String.format("%s-%s-%s", name, serverRole, effectiveUuid);
    }
}
