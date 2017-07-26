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

import org.apache.qpid.proton.message.Message;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;

/**
 * An adapter for handling downstream messages in an {@code Endpoint} specific way.
 *
 */
public interface DownstreamAdapter {

    /**
     * Invoked on startup of the adapter.
     * <p>
     * Subclasses may use this method to allocate any resources required for the adapter.
     * <p>
     * Clients <em>must not</em> invoke any of the other methods before the
     * start future passed in to this method has completed successfully.
     * 
     * @param startFuture The handler to inform about the outcome of the startup process.
     */
    void start(Future<Void> startFuture);

    /**
     * Invoked on shutdown of the adapter.
     * <p>
     * Subclasses should use this method to free up any resources allocated during start up.
     * <p>
     * Clients <em>must not</em> invoke any of the other methods after this
     * method has completed successfully.
     * 
     * @param stopFuture The handler to inform about the outcome of the shutdown process.
     */
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
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalStateException if this adapter is not running.
     */
    void onClientAttach(UpstreamReceiver client, Handler<AsyncResult<Void>> resultHandler);

    /**
     * Invoked when an upstream client closes a link with the Hono server.
     * <p>
     * Implementations should release any resources allocated as part of the invocation of the
     * {@link #onClientAttach(UpstreamReceiver, Handler)} method.
     * 
     * @param client The client closing the link.
     * @throws NullPointerException if client is {@code null}.
     * @throws IllegalStateException if this adapter is not running.
     */
    void onClientDetach(UpstreamReceiver client);

    /**
     * Invoked when an upstream client disconnects from Hono.
     * <p>
     * There is no distinction being made between a client <em>orderly</em>
     * closing a connection and an unexpected termination, e.g. due to
     * network failure.
     * 
     * @param connectionId The ID of the failed connection.
     * @throws NullPointerException if connection ID is {@code null}.
     * @throws IllegalStateException if this adapter is not running.
     */
    void onClientDisconnect(String connectionId);

    /**
     * Processes a message received from an upstream client.
     * <p>
     * Implementors are responsible for handling the message's disposition and settlement
     * using the <em>delivery</em> object.
     * 
     * @param client The client the message originates from.
     * @param delivery The message's disposition handler.
     * @param message The message to process.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalStateException if this adapter is not running.
     */
    void processMessage(UpstreamReceiver client, ProtonDelivery delivery, Message message);

    /**
     * Checks if this adapter is currently connected to the downstream AMQP container.
     * 
     * @return {@code true} if this adapter has a usable connection to the container.
     */
    boolean isConnected();
}
