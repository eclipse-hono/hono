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
package org.eclipse.hono.service.amqp;

import org.eclipse.hono.service.Endpoint;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A message endpoint providing an API that clients can interact with by means of AMQP 1.0 based message exchanges.
 *
 */
public interface AmqpEndpoint extends Endpoint {

    /**
     * Handles a client's request to establish a link with Hono for sending messages to a given target address.
     * 
     * @param connection The AMQP connection that the link is part of.
     * @param receiver The link to be established.
     * @param targetAddress The (remote) target address from the client's AMQP <em>ATTACH</em> message.
     */
    void onLinkAttach(ProtonConnection connection, ProtonReceiver receiver, ResourceIdentifier targetAddress);

    /**
     * Handles a client's request to establish a link with Hono for receiving messages from a given address.
     *
     * @param connection The AMQP connection that the link is part of.
     * @param sender The link to be established.
     * @param sourceAddress The (remote) source address from the client's AMQP <em>ATTACH</em> message.
     */
    void onLinkAttach(ProtonConnection connection, ProtonSender sender, ResourceIdentifier sourceAddress);
}
