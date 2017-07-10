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
 *
 */

package org.eclipse.hono.client.impl;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonReceiver;

/**
 * A Vertx-Proton based client for consuming event messages from a Hono server.
 */
public class EventConsumerImpl extends AbstractHonoClient implements MessageConsumer {

    private static final String EVENT_ADDRESS_TEMPLATE = "event%s%s";
    private static final Logger LOG = LoggerFactory.getLogger(EventConsumerImpl.class);

    private EventConsumerImpl(final Context context, final ProtonReceiver receiver) {
        super(context);
        this.receiver = receiver;
    }

    /**
     * Creates a new event consumer for a tenant.
     * 
     * @param context The vert.x context to run all interactions with the server on.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant to consumer events for.
     * @param eventConsumer The consumer to invoke with each event received.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void create(
            final Context context,
            final ProtonConnection con,
            final String tenantId,
            final BiConsumer<ProtonDelivery, Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        create(context, con, tenantId, Constants.DEFAULT_PATH_SEPARATOR, eventConsumer, creationHandler);
    }

    /**
     * Creates a new event consumer for a tenant.
     * 
     * @param context The vert.x context to run all interactions with the server on.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant to consumer events for.
     * @param pathSeparator The address path separator character used by the server.
     * @param eventConsumer The consumer to invoke with each event received.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void create(
            final Context context,
            final ProtonConnection con,
            final String tenantId,
            final String pathSeparator,
            final BiConsumer<ProtonDelivery, Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(pathSeparator);
        Objects.requireNonNull(eventConsumer);
        Objects.requireNonNull(creationHandler);
        createConsumer(context, con, tenantId, pathSeparator, eventConsumer).setHandler(created -> {
            if (created.succeeded()) {
                creationHandler.handle(Future.succeededFuture(
                        new EventConsumerImpl(context, created.result())));
            } else {
                creationHandler.handle(Future.failedFuture(created.cause()));
            }
        });
    }

    private static Future<ProtonReceiver> createConsumer(
            final Context context,
            final ProtonConnection con,
            final String tenantId,
            final String pathSeparator,
            final BiConsumer<ProtonDelivery, Message> consumer) {

        Future<ProtonReceiver> result = Future.future();
        final String targetAddress = String.format(EVENT_ADDRESS_TEMPLATE, pathSeparator, tenantId);

        context.runOnContext(open -> {
            final ProtonReceiver receiver = con.createReceiver(targetAddress);
            receiver.setAutoAccept(true);
            receiver.setPrefetch(DEFAULT_SENDER_CREDITS);
            receiver.openHandler(receiverOpen -> {
                if (receiverOpen.succeeded()) {
                    LOG.debug("event receiver for [{}] open", receiverOpen.result().getRemoteSource());
                    result.complete(receiverOpen.result());
                } else {
                    result.fail(receiverOpen.cause());
                }
            });
            receiver.handler((delivery, message) -> {
                if (consumer != null) {
                    consumer.accept(delivery, message);
                }
            });
            receiver.open();
        });
        return result;
    }

    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {
        closeLinks(closeHandler);
    }
}
