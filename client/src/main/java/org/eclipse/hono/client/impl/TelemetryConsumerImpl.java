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
 */

package org.eclipse.hono.client.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TelemetryConstants;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A Vertx-Proton based client for consuming telemetry data from a Hono server.
 */
public class TelemetryConsumerImpl extends AbstractConsumer implements MessageConsumer {

    private static final String TELEMETRY_ADDRESS_TEMPLATE  = TelemetryConstants.TELEMETRY_ENDPOINT + "%s%s";

    private TelemetryConsumerImpl(final Context context, final ClientConfigProperties config, final ProtonReceiver receiver) {
        super(context, config, receiver);
    }

    /**
     * Creates a new telemetry data consumer for a tenant.
     * 
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant to consumer events for.
     * @param telemetryConsumer The consumer to invoke with each telemetry message received.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @param closeHook The handler to invoke when the link is closed by the peer (may be {@code null}).
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String tenantId,
            final Consumer<Message> telemetryConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler,
            final Handler<String> closeHook ) {

        create(context, clientConfig, con, tenantId, Constants.DEFAULT_PATH_SEPARATOR, telemetryConsumer, creationHandler, closeHook);
    }

    /**
     * Creates a new telemetry data consumer for a tenant.
     * 
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant to consumer events for.
     * @param pathSeparator The address path separator character used by the server.
     * @param telemetryConsumer The consumer to invoke with each telemetry message received.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @param closeHook The handler to invoke when the link is closed by the peer (may be {@code null}).
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String tenantId,
            final String pathSeparator,
            final Consumer<Message> telemetryConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler,
            final Handler<String> closeHook) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(clientConfig);
        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(pathSeparator);
        Objects.requireNonNull(telemetryConsumer);
        Objects.requireNonNull(creationHandler);

        createReceiver(context, clientConfig, con, String.format(TELEMETRY_ADDRESS_TEMPLATE, pathSeparator, tenantId), ProtonQoS.AT_LEAST_ONCE,
                (delivery, message) -> telemetryConsumer.accept(message), closeHook).setHandler(created -> {
                    if (created.succeeded()) {
                        creationHandler.handle(Future.succeededFuture(
                                new TelemetryConsumerImpl(context, clientConfig, created.result())));
                    } else {
                        creationHandler.handle(Future.failedFuture(created.cause()));
                    }
                });
    }

}
