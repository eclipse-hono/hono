/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.impl;

import java.util.Objects;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.util.TelemetryConstants;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A Vertx-Proton based client for consuming telemetry data from a Hono server.
 */
public class TelemetryConsumerImpl extends AbstractConsumer implements MessageConsumer {

    private TelemetryConsumerImpl(final HonoConnection connection, final ProtonReceiver receiver) {
        super(connection, receiver);
    }

    /**
     * Creates a new telemetry data consumer for a tenant.
     * 
     * @param con The connection to the server.
     * @param tenantId The tenant to consumer events for.
     * @param telemetryConsumer The consumer to invoke with each telemetry message received.
     * @param closeHook The handler to invoke when the link is closed by the peer (may be {@code null}).
     * @return A future indicating the outcome.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static Future<MessageConsumer> create(
            final HonoConnection con,
            final String tenantId,
            final Consumer<Message> telemetryConsumer,
            final Handler<String> closeHook ) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(telemetryConsumer);

        final String sourceAddress = String.format("%s/%s", TelemetryConstants.TELEMETRY_ENDPOINT, tenantId);
        return con.createReceiver(
                sourceAddress,
                ProtonQoS.AT_LEAST_ONCE,
                (delivery, message) -> telemetryConsumer.accept(message),
                closeHook)
                .compose(receiver -> Future.succeededFuture(new TelemetryConsumerImpl(con, receiver)));
    }
}
