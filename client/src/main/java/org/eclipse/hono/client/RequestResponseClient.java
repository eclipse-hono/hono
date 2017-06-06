/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * Interface for common methods that all clients that follow the request response pattern need to implement.
 */
public interface RequestResponseClient {
    /**
     * Closes the AMQP link(s) with the Hono server this client is configured to use.
     * <p>
     * The underlying AMQP connection to the server is not affected by this operation.
     * </p>
     *
     * @param closeHandler A handler that is called back with the result of the attempt to close the links.
     */
    void close(Handler<AsyncResult<Void>> closeHandler);

    /**
     * Checks if this client's sender and receiver are (locally) open.
     *
     * @return {@code true} if this client can be used to exchange messages with the peer.
     */
    boolean isOpen();
}
