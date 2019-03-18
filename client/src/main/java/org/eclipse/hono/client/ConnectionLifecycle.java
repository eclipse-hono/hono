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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * Provides access to the life cycle events of a connection to a Hono service.
 *
 */
public interface ConnectionLifecycle {

    /**
     * Adds a listener to be notified when the connection is lost unexpectedly.
     * 
     * @param listener The listener to add.
     */
    void addDisconnectListener(DisconnectListener listener);

    /**
     * Adds a listener to be notified when the connection has been re-established after
     * it had been lost unexpectedly.
     * 
     * @param listener The listener to add.
     */
    void addReconnectListener(ReconnectListener listener);

    /**
     * Checks whether the connection is currently established.
     *
     * @return A succeeded future if this connection is established.
     *         Otherwise, the future will be failed with a
     *         {@link ServerErrorException}.
     */
    Future<Void> isConnected();

    /**
     * Disconnects from the service.
     * <p>
     * Upon terminating the connection to the server, this method does not automatically try to reconnect
     * to the server again.
     *
     */
    void disconnect();

    /**
     * Disconnects from the service.
     * <p>
     * Similar to {@code #disconnect()} but takes a handler to be notified about the result
     * of the disconnect operation. The caller can use the handler to determine if the operation succeeded or failed.
     *
     * @param completionHandler The handler to notify about the outcome of the operation.
     *                          A failure could occur if this method is called in the middle of a disconnect operation.
     * @throws NullPointerException if the completionHandler is {@code null}.
     */
    void disconnect(Handler<AsyncResult<Void>> completionHandler);
}
