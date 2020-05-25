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

    /**
     * Handles a closed connection.
     * <p>
     * This method is called whenever a connection got closed. Either actively but a call to disconnect, or by a
     * broken/lost connection.
     * </p>
     *
     * @param connection The connection which got closed.
     */
    void onConnectionClosed(ProtonConnection connection);
}
