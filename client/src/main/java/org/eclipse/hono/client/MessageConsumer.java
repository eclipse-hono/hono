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
     * Gets the number of messages this consumer can receive based on its current number of credits, 
     * excluding credits used by any queued incoming messages.
     * <p>
     * Note that the value returned is valid during execution of the current vert.x handler only.
     *
     * @return The number of messages.
     */
    int getRemainingCredit();

}
