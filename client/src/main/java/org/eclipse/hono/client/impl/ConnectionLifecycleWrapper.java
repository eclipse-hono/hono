/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.client.impl;

import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ReconnectListener;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * ConnectionLifecycle base class that delegates all method invocations to a given
 * ConnectionLifecycle instance.
 */
public abstract class ConnectionLifecycleWrapper implements ConnectionLifecycle {

    private final ConnectionLifecycle delegate;

    /**
     * Creates a new ConnectionLifecycleWrapper instance.
     *
     * @param delegate The object to invoke the ConnectionLifecycle methods on.
     */
    public ConnectionLifecycleWrapper(final ConnectionLifecycle delegate) {
        this.delegate = delegate;
    }

    @Override
    public Future<HonoConnection> connect() {
        return delegate.connect();
    }

    @Override
    public void addDisconnectListener(final DisconnectListener listener) {
        delegate.addDisconnectListener(listener);
    }

    @Override
    public void addReconnectListener(final ReconnectListener listener) {
        delegate.addReconnectListener(listener);
    }

    @Override
    public Future<Void> isConnected() {
        return delegate.isConnected();
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
