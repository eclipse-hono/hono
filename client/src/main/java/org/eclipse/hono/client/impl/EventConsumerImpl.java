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
 *
 */

package org.eclipse.hono.client.impl;

import java.util.Objects;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
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

    public static void create(
            final Context context,
            final ProtonConnection con,
            final String tenantId,
            final String pathSeparator,
            final Consumer<Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(pathSeparator);
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
            final Consumer<Message> consumer) {

        Future<ProtonReceiver> result = Future.future();
        final String targetAddress = String.format(EVENT_ADDRESS_TEMPLATE, pathSeparator, tenantId);

        context.runOnContext(open -> {
            final ProtonReceiver receiver = con.createReceiver(targetAddress);
            receiver.setAutoAccept(true).setPrefetch(DEFAULT_RECEIVER_CREDITS);
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
                    consumer.accept(message);
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
