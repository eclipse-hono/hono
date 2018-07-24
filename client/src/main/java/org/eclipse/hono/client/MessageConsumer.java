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

package org.eclipse.hono.client;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * A client for consuming messages from a Hono server.
 *
 */
public interface MessageConsumer {

    /**
     * Closes the AMQP link with the Hono server this client is configured to use.
     * <p>
     * The underlying AMQP connection to the server is not affected by this operation.
     * </p>
     * 
     * @param closeHandler A handler that is called back with the result of the attempt to close the links.
     */
    void close(Handler<AsyncResult<Void>> closeHandler);

    /**
     * Grants the given number of message credits to the sender.
     *
     * For use when created with 0 prefetch in consumer creation
     *
     * @param credits the credits to flow
     * @throws IllegalStateException if prefetch is non-zero, or an existing drain operation is not yet complete
     */
    void flow(int credits) throws IllegalStateException;

    /**
     * Gets the number of additional messages this consumer can receive.
     * <p>
     * Note that the value returned is valid during execution of the current vert.x handler only.
     *
     * @return The number of messages.
     */
    int getRemainingCredit();

}
