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

package org.eclipse.hono.server;

import org.apache.qpid.proton.message.Message;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;

/**
 * An adapter for handling downstream messages in an {@code Endpoint} specific way.
 *
 */
public interface DownstreamAdapter {

    void start(Future<Void> startFuture);

    void stop(Future<Void> stopFuture);

    /**
     * Invoked when an upstream client wants to establish a link with the Hono server for sending
     * messages for a given target address downstream.
     * <p>
     * Implementations should use this method for allocating any resources required
     * for processing messages sent by the client, e.g. connect to a downstream container.
     * <p>
     * In order to signal the client to start sending messages the client will usually need
     * to be replenished with some credits by invoking {@link UpstreamReceiver#replenish(int)}.
     * 
     * @param client The client connecting to the Hono server.
     * @param resultHandler The handler to notify about the outcome of allocating the required resources.
     */
    void onClientAttach(UpstreamReceiver client, Handler<AsyncResult<Void>> resultHandler);

    /**
     * Invoked when an upstream client closes a link with the Hono server.
     * <p>
     * Implementations should release any resources allocated as part of the invocation of the
     * {@link #onClientAttach(UpstreamReceiver, Handler)} method.
     * 
     * @param client The client closing the link.
     */
    void onClientDetach(UpstreamReceiver client);

    /**
     * Invoked when a connection with an upstream client got disconnected unexpectedly.
     * 
     * @param con The failed connection.
     */
    void onClientDisconnect(ProtonConnection con);

    /**
     * Processes a message received from an upstream client.
     * <p>
     * Implementors are responsible for handling the message's disposition and settlement
     * using the <em>delivery</em> object.
     * 
     * @param client The client the message originates from.
     * @param delivery The message's disposition handler.
     * @param message The message to process.
     */
    void processMessage(UpstreamReceiver client, ProtonDelivery delivery, Message message);
}
