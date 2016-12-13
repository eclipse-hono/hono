/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.connection;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * A factory for AMQP 1.0 connections.
 */
public interface ConnectionFactory {

    /**
     * Sets the host name to request in the AMQP <em>Open</em> frame sent to the server.
     * 
     * @param hostname The host name.
     */
    void setHostname(String hostname);

    /**
     * Connects to a server.
     * 
     * @param options The client options to use for connecting. If {@code null} default options will be used.
     * @param closeHandler The handler to invoke when an AMQP <em>Close</em> frame is received from the server
     *                     (may be {@code null}).
     * @param disconnectHandler The handler to invoke when the connection to the server is lost unexpectedly
     *                     (may be {@code null}).
     * @param connectionResultHandler The callback to invoke with the outcome of the connection attempt.
     * @throws NullPointerException if the result handler is {@code null}.
     */
    void connect(
            ProtonClientOptions options,
            Handler<AsyncResult<ProtonConnection>> closeHandler,
            Handler<ProtonConnection> disconnectHandler,
            Handler<AsyncResult<ProtonConnection>> connectionResultHandler);
}
