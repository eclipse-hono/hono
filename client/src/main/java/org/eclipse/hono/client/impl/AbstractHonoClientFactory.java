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


package org.eclipse.hono.client.impl;

import java.util.Objects;

import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ReconnectListener;
import org.eclipse.hono.client.ServerErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * A base class for implementing client factories.
 */
abstract class AbstractHonoClientFactory implements ConnectionLifecycle<HonoConnection> {

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());
    /**
     * The connection to use for interacting with Hono.
     */
    protected final HonoConnection connection;

    /**
     * @param connection The connection to use.
     * @throws NullPointerException if connection is {@code null}.
     */
    AbstractHonoClientFactory(final HonoConnection connection) {
        this.connection = Objects.requireNonNull(connection);
        this.connection.addDisconnectListener(con -> onDisconnect());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Simply delegates to {@link HonoConnection#addDisconnectListener(DisconnectListener)}.
     */
    @Override
    public void addDisconnectListener(final DisconnectListener<HonoConnection> listener) {
        connection.addDisconnectListener(listener);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Simply delegates to {@link HonoConnection#addReconnectListener(ReconnectListener)}.
     */
    @Override
    public void addReconnectListener(final ReconnectListener<HonoConnection> listener) {
        connection.addReconnectListener(listener);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Simply delegates to {@link HonoConnection#connect()}.
     */
    @Override
    public Future<HonoConnection> connect() {
        return connection.connect();
    }

    /**
     * Checks whether this client is connected to the service.
     * <p>
     * Simply delegates to {@link HonoConnection#isConnected()}.
     * 
     * @return A succeeded future if this factory is connected.
     *         Otherwise, the future will be failed with a
     *         {@link ServerErrorException}.
     */
    @Override
    public final Future<Void> isConnected() {
        return connection.isConnected();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This default implementation simply delegates to {@link HonoConnection#disconnect()}.
     */
    @Override
    public void disconnect() {
        connection.disconnect();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This default implementation simply delegates to {@link HonoConnection#disconnect(Handler)}.
     */
    @Override
    public void disconnect(final Handler<AsyncResult<Void>> completionHandler) {
        connection.disconnect(completionHandler);
    }

    /**
     * Invoked when the underlying connection to the Hono server
     * is lost unexpectedly.
     * <p>
     * This default implementation does nothing.
     * Subclasses should override this method in order to clean
     * up any state that may have become stale with the loss
     * of the connection.
     */
    protected void onDisconnect() {
        // do nothing
    }
}
