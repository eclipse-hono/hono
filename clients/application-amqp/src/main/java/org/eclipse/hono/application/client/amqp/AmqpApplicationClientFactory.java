/*
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
 */

package org.eclipse.hono.application.client.amqp;

import java.util.function.Consumer;

import org.eclipse.hono.application.client.ApplicationClientFactory;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;

import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * A factory for creating clients for Hono's AMQP-based northbound APIs.
 */
public interface AmqpApplicationClientFactory extends ApplicationClientFactory {

    /**
     * Creates a client for consuming data from Hono's northbound <em>Telemetry API</em>.
     *
     * @param tenantId The tenant to consume data for.
     * @param telemetryConsumer The handler to invoke with every message received.
     * @param autoAccept {@code true} if received deliveries should be automatically accepted (and settled) after the
     *            message handler runs for them, if no other disposition has been applied during handling. NOTE: When
     *            using {@code false} here, make sure that deliveries (from {@link AmqpMessageContext#getDelivery()})
     *            are quickly updated and settled, so that the messages don't remain <em>in flight</em> for long.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this factory is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<MessageConsumer<DownstreamMessage>> createTelemetryConsumer(
            String tenantId,
            Consumer<DownstreamMessage> telemetryConsumer,
            boolean autoAccept,
            Handler<Throwable> closeHandler);

    /**
     * Creates a client for consuming events from Hono's northbound <em>Event API</em>.
     *
     * @param tenantId The tenant to consume data for.
     * @param eventConsumer The handler to invoke with every message received.
     * @param autoAccept {@code true} if received deliveries should be automatically accepted (and settled) after the
     *            message handler runs for them, if no other disposition has been applied during handling. NOTE: When
     *            using {@code false} here, make sure that deliveries (from {@link AmqpMessageContext#getDelivery()})
     *            are quickly updated and settled, so that the messages don't remain <em>in flight</em> for long.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this factory is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<MessageConsumer<DownstreamMessage>> createEventConsumer(
            String tenantId,
            Consumer<DownstreamMessage> eventConsumer,
            boolean autoAccept,
            Handler<Throwable> closeHandler);

}
