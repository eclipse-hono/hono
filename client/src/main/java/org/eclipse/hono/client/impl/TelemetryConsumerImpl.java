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

package org.eclipse.hono.client.impl;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
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
 * A Vertx-Proton based client for consuming telemetry data from a Hono server.
 */
public class TelemetryConsumerImpl extends AbstractHonoClient implements MessageConsumer {

    private static final String     TELEMETRY_ADDRESS_TEMPLATE  = "telemetry%s%s";
    private static final Logger     LOG = LoggerFactory.getLogger(TelemetryConsumerImpl.class);
    private static final int        SENDER_CREDIT = 100;

    private TelemetryConsumerImpl(final Context context, final ProtonReceiver receiver) {
        super(context);
        this.receiver = receiver;
    }

    public static void create(
            final Context context,
            final ProtonConnection con,
            final String tenantId,
            final String pathSeparator,
            final Consumer<Message> telemetryConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(pathSeparator);
        createConsumer(context, con, tenantId, pathSeparator, telemetryConsumer).setHandler(created -> {
            if (created.succeeded()) {
                creationHandler.handle(Future.succeededFuture(
                        new TelemetryConsumerImpl(context, created.result())));
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
        final String targetAddress = String.format(TELEMETRY_ADDRESS_TEMPLATE, pathSeparator, tenantId);

        context.runOnContext(open -> {
            final ProtonReceiver receiver = con.createReceiver(targetAddress);
            final AtomicInteger credit = new AtomicInteger(SENDER_CREDIT);
            receiver.setAutoAccept(true).setPrefetch(0);
            receiver.openHandler(receiverOpen -> {
                if (receiverOpen.succeeded()) {
                    LOG.debug("telemetry receiver for [{}] open", receiverOpen.result().getRemoteSource());
                    receiver.flow(credit.get());
                    result.complete(receiverOpen.result());
                } else {
                    result.fail(receiverOpen.cause());
                }
            });
            receiver.handler((delivery, message) -> {
                if (consumer != null) {
                    if (credit.decrementAndGet() == 0) {
                        credit.set(SENDER_CREDIT);
                        LOG.debug("replenishing sender [link-credit: {}]", credit.get());
                        receiver.flow(credit.get());
                    }
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
