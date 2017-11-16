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
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.util.Constants;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A Vertx-Proton based client for consuming telemetry data from a Hono server.
 */
public class TelemetryConsumerImpl extends AbstractConsumer implements MessageConsumer {

    private static final String     TELEMETRY_ADDRESS_TEMPLATE  = "telemetry%s%s";

    private TelemetryConsumerImpl(final Context context, final ProtonReceiver receiver) {
        super(context, receiver);
    }

    /**
     * Creates a new telemetry data consumer for a tenant.
     * 
     * @param context The vert.x context to run all interactions with the server on.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant to consumer events for.
     * @param prefetch the number of message credits the consumer grants and replenishes automatically as messages are
     *                 delivered. To manage credit manually, you can instead set prefetch to 0.
     * @param telemetryConsumer The consumer to invoke with each telemetry message received.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void create(
            final Context context,
            final ProtonConnection con,
            final String tenantId,
            final int prefetch,
            final Consumer<Message> telemetryConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        create(context, con, tenantId, Constants.DEFAULT_PATH_SEPARATOR, prefetch, telemetryConsumer, creationHandler);
    }

    /**
     * Creates a new telemetry data consumer for a tenant.
     * 
     * @param context The vert.x context to run all interactions with the server on.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant to consumer events for.
     * @param pathSeparator The address path separator character used by the server.
     * @param prefetch the number of message credits the consumer grants and replenishes automatically as messages are
     *                 delivered. To manage credit manually, you can instead set prefetch to 0.
     * @param telemetryConsumer The consumer to invoke with each telemetry message received.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void create(
            final Context context,
            final ProtonConnection con,
            final String tenantId,
            final String pathSeparator,
            final int prefetch,
            final Consumer<Message> telemetryConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(pathSeparator);
        Objects.requireNonNull(telemetryConsumer);
        Objects.requireNonNull(creationHandler);

        createConsumer(context, con, tenantId, pathSeparator, TELEMETRY_ADDRESS_TEMPLATE, ProtonQoS.AT_LEAST_ONCE, prefetch,
                (protonDelivery, message) -> telemetryConsumer.accept(message)).setHandler(created -> {
                    if (created.succeeded()) {
                        creationHandler.handle(Future.succeededFuture(
                                new TelemetryConsumerImpl(context, created.result())));
                    } else {
                        creationHandler.handle(Future.failedFuture(created.cause()));
                    }
                });
    }

}
