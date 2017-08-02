/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.messaging;

import org.apache.qpid.proton.amqp.transport.ErrorCondition;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonReceiver;

/**
 * A decorator for a {@code ProtonReceiver} representing a client uploading data to a Hono endpoint.
 * <p>
 * Subclasses are strongly encouraged to implement {@link Object#hashCode()} and {@link Object#equals(Object)}
 * based on the <em>linkId</em> because instances of this interface are used as keys in maps in other classes of Hono.
 *
 */
public interface UpstreamReceiver {

    /**
     * Creates a new instance for an identifier and a receiver link.
     * <p>
     * The receiver is configured for manual flow control and disposition handling.
     * 
     * @param linkId The identifier for the link.
     * @param receiver The link for receiving data from the client.
     * @return The created instance.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    static UpstreamReceiver newUpstreamReceiver(final String linkId, final ProtonReceiver receiver) {
        return new UpstreamReceiverImpl(linkId, receiver);
    }

    /**
     * Sends an AMQP 1.0 <em>flow</em> frame to the client with a certain amount of <em>credit</em>.
     * 
     * @param replenishedCredits The number of credits to replenish the client with.
     */
    void replenish(int replenishedCredits);

    /**
     * Sends an AMQP 1.0 <em>flow</em> frame to the client with the <em>drain</em> flag set.
     * 
     * @param timeoutMillis The maximum time to wait for a response from the client in milliseconds.
     * @param drainCompletionHandler The handler to notify about the result of the drain request.
     */
    void drain(long timeoutMillis, Handler<AsyncResult<Void>> drainCompletionHandler);

    /**
     * Closes the decorated link with an error condition.
     * 
     * @param error The error condition to set on the link.
     */
    void close(ErrorCondition error);

    /**
     * Gets the link's unique identifier.
     * 
     * @return The identifier.
     */
    String getLinkId();

    /**
     * Gets the ID of the client's connection to Hono that the decorated link is part of.
     * <p>
     * {@code HonoServer} assigns this (surrogate) identifier when a client establishes a connection with Hono.
     * 
     * @return The ID.
     */
    String getConnectionId();

    /**
     * Gets the decorated link's target address.
     * 
     * @return The address.
     */
    String getTargetAddress();
}