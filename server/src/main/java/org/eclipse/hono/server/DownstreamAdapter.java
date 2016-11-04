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
import io.vertx.proton.ProtonSender;

/**
 * An adapter for handling downstream messages in an {@code Endpoint} specific way.
 *
 */
public interface DownstreamAdapter {

    void start(Future<Void> startFuture);

    void stop(Future<Void> stopFuture);

    /**
     * Invoked when a client wants to establish a link with the Hono server for sending
     * messages for a given target address downstream.
     * <p>
     * Subclasses should use this method for allocating any resources required
     * for processing messages sent by the client, e.g. connect to a downstream container.
     * <p>
     * In order to signal the client to start sending messages the
     * {@link #sendFlowControlMessage(String, int, Handler)} method must be invoked with
     * some <em>credit</em>.
     * 
     * @param connectionId The unique ID of the AMQP 1.0 connection with the client.
     * @param linkId The unique ID of the link used by the client for uploading data.
     * @param targetAddress The target address to upload data to.
     */
    void getDownstreamSender(final UpstreamReceiver client, final Handler<AsyncResult<ProtonSender>> resultHandler);

    /**
     * Invoked when a client closes a link with the Hono server.
     * <p>
     * Subclasses should release any resources allocated as part of the invocation of the
     * {@link #onLinkAttached(String, String, String)} method.
     * 
     * @param linkId the unique ID of the link being closed.
     */
    void onClientDetach(final UpstreamReceiver client);

    /**
     * Invoked when a connection with a client got disconnected unexpectedly.
     * 
     * @param con The failed connection.
     */
    void onClientDisconnect(final ProtonConnection con);

    /**
     * Processes a message received from an upstream client.
     * 
     * @param client The client the message originates from.
     * @param delivery The message's disposition handler.
     * @param message The message.
     */
    void processMessage(final UpstreamReceiver client, ProtonDelivery delivery, Message message);
}
