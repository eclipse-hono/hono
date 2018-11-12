/*******************************************************************************
 * Copyright (c) 2016, 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.client;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * Interface for common methods that all clients that follow the request response pattern need to implement.
 */
public interface RequestResponseClient extends CreditBasedSender {
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

    /**
     * Sets the period of time after which any requests are considered to have timed out.
     * <p>
     * The client will fail the result handler passed in to any of the operations if no response
     * has been received from the peer after the given amount of time.
     * <p>
     * When setting this property to 0, requests do not time out at all. Note that this will
     * allow for unanswered requests piling up in the client, which eventually may cause the
     * client to run out of memory.
     * 
     * @param timoutMillis The number of milliseconds after which a request is considered to have timed out.
     * @throws IllegalArgumentException if the value is &lt; 0
     */
    void setRequestTimeout(long timoutMillis);
}
