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

import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Future;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A message endpoint providing an API that clients can interact with by means of AMQP 1.0 based message exchanges. 
 *
 */
public interface Endpoint {

    /**
     * Gets the name of this endpoint.
     * <p>
     * The Hono server uses this name to determine the {@code Endpoint} implementation that
     * is responsible for handling requests to establish a link with a target address starting with this name.
     * </p>
     *  
     * @return the name.
     */
    String getName();

    /**
     * Handles a client's request to establish a link with Hono for sending messages to a given target address.
     * 
     * @param receiver the link to be established.
     * @param targetAddress the target address from the client's AMQP <em>ATTACH</em> message.
     */
    void onLinkAttach(ProtonReceiver receiver, ResourceIdentifier targetAddress);

    /**
     * Handles a client's request to establish a link with Hono for receiving messages from a given address.
     *
     * @param sender the link to be established.
     * @param sourceAddress the source address from the client's AMQP <em>ATTACH</em> message.
     */
    void onLinkAttach(ProtonSender sender, ResourceIdentifier sourceAddress);

    /**
     * Starts this endpoint.
     * <p>
     * This method should be used to allocate any required resources.
     * However, no long running tasks should be executed.
     * 
     * @param startFuture Completes if this endpoint has started successfully.
     */
    void start(Future<Void> startFuture);

    /**
     * Stops this endpoint.
     * <p>
     * This method should be used to release any allocated resources.
     * However, no long running tasks should be executed.
     * 
     * @param stopFuture Completes if this endpoint has stopped successfully.
     */
    void stop(Future<Void> stopFuture);
}
