/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.client.impl;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.util.CommandConstants;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A Vertx-Proton based client for consuming async command response messages from a Hono server.
 */
public class AsyncCommandResponseConsumerImpl extends AbstractConsumer implements MessageConsumer {

    private static final String ASYNC_COMMAND_RESPONSE_ADDRESS_TEMPLATE = CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT
            + "/%s/%s";

    private AsyncCommandResponseConsumerImpl(
            final HonoConnection con,
            final ProtonReceiver receiver) {
        super(con, receiver);
    }

    /**
     * Creates a new asynchronous command response consumer for a tenant for commands with the given {@code replyId}.
     *
     * @param con The connection to the Hono server.
     * @param tenantId The tenant to consume async command responses for.
     * @param replyId The {@code replyId} of commands to consume async responses for.
     * @param asyncCommandResponseConsumer The consumer to invoke with async command response received.
     * @param closeHook The handler to invoke when the link is closed by the peer (may be {@code null}).
     * @return A future indicating the outcome.
     * @throws NullPointerException if any of the parameters except close hook are {@code null}.
     */
    public static Future<MessageConsumer> create(
            final HonoConnection con,
            final String tenantId,
            final String replyId,
            final BiConsumer<ProtonDelivery, Message> asyncCommandResponseConsumer,
            final Handler<String> closeHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(replyId);
        Objects.requireNonNull(asyncCommandResponseConsumer);

        final String sourceAddress = String.format(ASYNC_COMMAND_RESPONSE_ADDRESS_TEMPLATE, tenantId, replyId);
        return con.createReceiver(sourceAddress, ProtonQoS.AT_LEAST_ONCE, asyncCommandResponseConsumer::accept, closeHook)
                .compose(recv -> Future.succeededFuture((MessageConsumer) new AsyncCommandResponseConsumerImpl(con, recv)));
    }
}
