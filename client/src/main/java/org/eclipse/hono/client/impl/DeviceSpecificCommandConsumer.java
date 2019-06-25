/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.client.impl;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A wrapper around an AMQP receiver link for consuming commands.
 */
public class DeviceSpecificCommandConsumer extends CommandConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceSpecificCommandConsumer.class);

    private DeviceSpecificCommandConsumer(final HonoConnection connection, final ProtonReceiver receiver) {
        super(connection, receiver);
    }

    /**
     * Creates a new command consumer.
     * <p>
     * The underlying receiver link will be created with the following properties:
     * <ul>
     * <li><em>auto accept</em> will be set to {@code true}</li>
     * <li><em>pre-fetch size</em> will be set to {@code 0} to enforce manual flow control.
     * However, the sender will be issued one credit on link establishment.</li>
     * </ul>
     *
     * @param con The connection to the server.
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the commands should be consumed.
     * @param commandHandler The handler to invoke for each command received.
     * @param localCloseHandler A handler to be invoked after the link has been closed
     *                     at this peer's request using the {@link #close(Handler)} method.
     *                     The handler will be invoked with the link's source address <em>after</em>
     *                     the link has been closed but <em>before</em> the handler that has been
     *                     passed into the <em>close</em> method is invoked.
     * @param remoteCloseHandler A handler to be invoked after the link has been closed
     *                     at the remote peer's request. The handler will be invoked with the
     *                     link's source address.
     * @return A future indicating the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static final Future<DeviceSpecificCommandConsumer> create(
            final HonoConnection con,
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandHandler,
            final Handler<String> localCloseHandler,
            final Handler<String> remoteCloseHandler) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(commandHandler);
        Objects.requireNonNull(remoteCloseHandler);

        LOG.trace("creating new command consumer [tenant-id: {}, device-id: {}]", tenantId, deviceId);

        final String address = ResourceIdentifier.from(CommandConstants.NORTHBOUND_COMMAND_LEGACY_ENDPOINT, tenantId, deviceId).toString();

        final AtomicReference<ProtonReceiver> receiverRef = new AtomicReference<>();

        return con.createReceiver(
                address,
                ProtonQoS.AT_LEAST_ONCE,
                (delivery, msg) -> {

                    final Command command = Command.from(msg, tenantId, deviceId);
                    final Tracer tracer = con.getTracer();
                    // try to extract Span context from incoming message
                    final SpanContext spanContext = TracingHelper.extractSpanContext(tracer, msg);
                    final Span currentSpan = createSpan("send command", tenantId, deviceId,
                            tracer, spanContext);
                    logReceivedCommandToSpan(command, currentSpan);
                    commandHandler.handle(CommandContext.from(command, delivery, receiverRef.get(), currentSpan));
                },
                0, // no pre-fetching
                false, // no auto-accept
                sourceAddress -> {
                    LOG.debug("command receiver link [tenant-id: {}, device-id: {}] closed remotely",
                            tenantId, deviceId);
                    remoteCloseHandler.handle(sourceAddress);
                }).map(receiver -> {
                    LOG.debug("successfully created command consumer [{}]", address);
                    receiverRef.set(receiver);
                    receiver.flow(1); // allow sender to send one command
                    final DeviceSpecificCommandConsumer consumer = new DeviceSpecificCommandConsumer(con, receiver);
                    consumer.setLocalCloseHandler(sourceAddress -> {
                        LOG.debug("command receiver link [tenant-id: {}, device-id: {}] closed locally",
                                tenantId, deviceId);
                        localCloseHandler.handle(sourceAddress);
                    });
                    return consumer;
                }).recover(t -> {
                    LOG.debug("failed to create command consumer [tenant-id: {}, device-id: {}]",
                            tenantId, deviceId, t);
                    return Future.failedFuture(t);
                });
    }
}
