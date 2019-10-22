/**
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.tests;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.impl.AbstractHonoClient;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;


/**
 * A generic sender for arbitrary target addresses.
 *
 */
public class GenericMessageSenderImpl extends AbstractHonoClient implements MessageSender {

    /**
     * Creates a sender.
     * 
     * @param con The connection to the Hono server.
     * @param sender The sender link to send messages over.
     */
    public GenericMessageSenderImpl(
            final HonoConnection con,
            final ProtonSender sender) {

        super(con);
        this.sender = sender;
    }

    /**
     * Creates a new sender for sending messages.
     * 
     * @param con The connection to the peer.
     * @param targetAddress The target address of the sender.
     * @param closeHook The handler to invoke when the Hono server closes the sender. The sender's
     *                  target address is provided as an argument to the handler.
     * @return The sender.
     * @throws NullPointerException if any of context, connection, tenant or handler is {@code null}.
     */
    public static Future<MessageSender> create(
            final HonoConnection con,
            final String targetAddress,
            final Handler<String> closeHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(targetAddress);

        return con.createSender(targetAddress, ProtonQoS.AT_LEAST_ONCE, closeHook).map(sender -> {
            return new GenericMessageSenderImpl(con, sender);
        });
    }

    /**
     * Closes this sender.
     */
    public void close() {
        closeLinks(closeAttempt -> {});
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCredit() {
        return sender.getCredit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendQueueDrainHandler(final Handler<Void> handler) {
        sender.sendQueueDrainHandler(replenishedSender -> handler.handle(null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getEndpoint() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {
        sender.closeHandler(closeAttempt -> {
            if (closeAttempt.succeeded()) {
                closeHandler.handle(Future.succeededFuture());
            } else {
                closeHandler.handle(Future.failedFuture(closeAttempt.cause()));
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOpen() {
        return sender != null && sender.isOpen();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<ProtonDelivery> send(final Message message) {
        return connection.executeOnContext(result -> {
            if (sender.isOpen() && sender.getCredit() > 0) {
                result.complete(sender.send(message));
            } else {
                result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
            }
        });
    }

    /**
     * Sends an AMQP 1.0 message to the peer this client is configured for
     * and waits for the outcome of the transfer.
     * 
     * @param message The message to send.
     * @return A future indicating the outcome of transferring the message.
     *         <p>
     *         The future will succeed with the updated delivery from the peer if
     *         the message has been settled with the <em>accepted</em> outcome.
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the
     *         message could not be sent, e.g. due to a lack of credit. It will be
     *         failed with a {@link ClientErrorException} if the message has not
     *         been accepted by the peer.
     * @throws NullPointerException if the message is {@code null}.
     */
    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message) {
        return connection.executeOnContext(result -> {
            if (sender.isOpen() && sender.getCredit() > 0) {
                sender.send(message, updatedDelivery -> {
                    if (updatedDelivery.getRemoteState() instanceof Accepted) {
                        result.complete(updatedDelivery);
                    } else if (updatedDelivery.getRemoteState() instanceof Released) {
                        result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
                    } else {
                        result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
                    }
                });
            } else {
                result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
            }
        });
    }
}
