/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.amqp;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A wrapper around a vertx-proton based AMQP receiver link.
 */
public class GenericReceiverLink extends AbstractHonoClient {

    private static final Logger LOG = LoggerFactory.getLogger(GenericReceiverLink.class);

    private GenericReceiverLink(final HonoConnection connection, final ProtonReceiver receiver) {
        super(connection);
        this.receiver = Objects.requireNonNull(receiver);
    }

    /**
     * Creates a new receiver link for a source address.
     *
     * @param con The connection to the server.
     * @param sourceAddress The address to receive messages from.
     * @param messageConsumer The consumer to invoke with each message received.
     * @param autoAccept {@code true} if received deliveries should be automatically accepted (and settled)
     *                   after the message handler runs for them, if no other disposition has been applied
     *                   during handling. NOTE: When using {@code false} here, make sure that deliveries are
     *                   quickly updated and settled, so that the messages don't remain <em>in flight</em>
     *                   for long.
     * @param closeHook An (optional) handler to invoke when the link is closed by the peer.
     * @return A future indicating the outcome.
     * @throws NullPointerException if any of the parameters except close hook are {@code null}.
     */
    public static Future<GenericReceiverLink> create(
            final HonoConnection con,
            final String sourceAddress,
            final BiConsumer<ProtonDelivery, Message> messageConsumer,
            final boolean autoAccept,
            final Handler<String> closeHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(sourceAddress);
        Objects.requireNonNull(messageConsumer);

        final int preFetchSize = con.getConfig().getInitialCredits();
        return con.createReceiver(
                sourceAddress,
                ProtonQoS.AT_LEAST_ONCE,
                messageConsumer::accept,
                preFetchSize,
                autoAccept,
                closeHook)
                .map(receiver -> new GenericReceiverLink(con, receiver));
    }

    /**
     * Closes the link.
     *
     * @return A succeeded future indicating that the link has been closed.
     */
    public final Future<Void> close() {
        LOG.debug("closing receiver ...");
        return closeLinks();
    }
}
