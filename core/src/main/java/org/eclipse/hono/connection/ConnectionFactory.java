/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.connection;

import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.impl.ConnectionFactoryImpl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * A factory for AMQP 1.0 connections.
 */
public interface ConnectionFactory {

    /**
     * Gets the name being indicated as the <em>container-id</em> in the client's AMQP <em>Open</em> frame.
     *
     * @return The name or {@code null} if no name has been set.
     */
    String getName();

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
     * Connects to a server.
     *
     * @param options The client options to use for connecting. If {@code null} default options will be used.
     * @param closeHandler The handler to invoke when an AMQP <em>Close</em> frame is received from the server (may be
     *            {@code null}).
     * @param disconnectHandler The handler to invoke when the connection to the server is lost unexpectedly (may be
     *            {@code null}).
     * @param connectionResultHandler The callback to invoke with the outcome of the connection attempt.
     * @throws NullPointerException if the result handler is {@code null}.
     */
    void connect(
            ProtonClientOptions options,
            Handler<AsyncResult<ProtonConnection>> closeHandler,
            Handler<ProtonConnection> disconnectHandler,
            Handler<AsyncResult<ProtonConnection>> connectionResultHandler);

    /**
     * Connects to a server.
     *
     * @param options The client options to use for connecting. If {@code null} default options will be used.
     * @param username The username to use for authenticating to the server using SASL PLAIN.
     * @param password The password to use for authenticating to the server using SASL PLAIN.
     * @param closeHandler The handler to invoke when an AMQP <em>Close</em> frame is received from the server (may be
     *            {@code null}).
     * @param disconnectHandler The handler to invoke when the connection to the server is lost unexpectedly (may be
     *            {@code null}).
     * @param connectionResultHandler The callback to invoke with the outcome of the connection attempt.
     * @throws NullPointerException if the result handler is {@code null}.
     */
    void connect(
            ProtonClientOptions options,
            String username,
            String password,
            Handler<AsyncResult<ProtonConnection>> closeHandler,
            Handler<ProtonConnection> disconnectHandler,
            Handler<AsyncResult<ProtonConnection>> connectionResultHandler);

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
}
