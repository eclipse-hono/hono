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

import io.vertx.proton.ProtonReceiver;

/**
 * A message endpoint providing an API that clients can interact with by means of AMQP 1.0 based message exchanges. 
 *
 */
public interface Endpoint {

    /**
     * Gets the name of this endpoint.
     * <p>
     * The Hono server uses this name to determine the {@code Endpoint} implementation that
     * is responsible for handling an incoming link establishement request.
     * </p>
     *  
     * @return the name.
     */
    String getName();

    /**
     * Handles an incoming link establishment request from a client.
     * 
     * @param receiver the link to be established.
     * @param targetResource the target address from the client's AMQP <em>ATTACH</em> message.
     */
    void establishLink(ProtonReceiver receiver, ResourceIdentifier targetResource);
}
