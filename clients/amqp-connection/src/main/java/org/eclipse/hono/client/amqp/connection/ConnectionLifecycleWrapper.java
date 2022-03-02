/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

import java.util.Objects;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * ConnectionLifecycle base class that delegates all method invocations to a given
 * ConnectionLifecycle instance.
 *
 * @param <T> The type of connection.
 */
public abstract class ConnectionLifecycleWrapper<T> implements ConnectionLifecycle<T> {

    private final ConnectionLifecycle<T> delegate;

    /**
     * Creates a new ConnectionLifecycleWrapper instance.
     *
     * @param delegate The object to invoke the ConnectionLifecycle methods on.
     * @throws NullPointerException if delegate is {@code null}.
     */
    protected ConnectionLifecycleWrapper(final ConnectionLifecycle<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public Future<T> connect() {
        return delegate.connect();
    }

    @Override
    public void addDisconnectListener(final DisconnectListener<T> listener) {
        delegate.addDisconnectListener(listener);
    }

    @Override
    public void addReconnectListener(final ReconnectListener<T> listener) {
        delegate.addReconnectListener(listener);
    }

    @Override
    public Future<Void> isConnected() {
        return delegate.isConnected();
    }

    @Override
    public Future<Void> isConnected(final long waitForCurrentConnectAttemptTimeout) {
        return delegate.isConnected(waitForCurrentConnectAttemptTimeout);
    }

    @Override
    public void disconnect() {
        delegate.disconnect();
    }

    @Override
    public void disconnect(final Handler<AsyncResult<Void>> completionHandler) {
        delegate.disconnect(completionHandler);
    }
}
