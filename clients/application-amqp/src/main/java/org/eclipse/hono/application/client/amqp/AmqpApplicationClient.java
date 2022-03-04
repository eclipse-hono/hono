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

import java.util.function.Function;

import org.eclipse.hono.application.client.ApplicationClient;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.client.amqp.connection.ConnectionLifecycle;
import org.eclipse.hono.client.amqp.connection.HonoConnection;

import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * An AMQP 1.0 based client that supports Hono's north bound operations to send commands and receive telemetry,
 * event and command response messages.
 */
public interface AmqpApplicationClient
        extends ApplicationClient<AmqpMessageContext>, ConnectionLifecycle<HonoConnection> {

    /**
     * Creates a client for consuming messages from Hono's north bound <em>Telemetry API</em>.
     * <p>
     * The messages passed in to the provided consumer will be acknowledged automatically if the future
     * returned by the consumer is succeeded.
     *
     * @param tenantId The tenant to consume messages for.
     * @param messageHandler The handler to invoke with every message received. The future returned
     *                        will be succeeded if the message has been processed successfully.
     *                        It will be failed if an error occurred while processing the message.
     *                        <p>
     *                        Implementors are encouraged to specify in detail the types of exceptions that the
     *                        future might be failed with, what kind of problem they indicate and what the
     *                        consequences regarding the underlying messaging infrastructure will be.
     * @param closeHandler An (optional) handler to be invoked when the consumer is being closed by the peer.
     *                     The handler will be invoked with an exception indicating the cause of the consumer
     *                     being closed or {@code null} if unknown.
     *                     <p>
     *                     Note that the handler will also be invoked when the underlying AMQP connection is being
     *                     closed by the remote peer without the peer having explicitly closed the consumer before.
     *                     <p>
     *                     Implementors are encouraged to specify in detail the types of exceptions that might
     *                     be passed in, what kind of problem they indicate and what the consequences regarding the
     *                     underlying messaging infrastructure will be.
     * @return A future that will complete with the consumer once it is ready. The future will fail if the consumer
     *         cannot be started.
     * @throws NullPointerException if any of tenant ID or message handler are {@code null}.
     */
    Future<MessageConsumer> createTelemetryConsumer(
            String tenantId,
            Function<DownstreamMessage<AmqpMessageContext>, Future<Void>> messageHandler,
            Handler<Throwable> closeHandler);

    /**
     * Creates a client for consuming messages from Hono's north bound <em>Event API</em>.
     * <p>
     * The messages passed in to the provided consumer will be acknowledged automatically if the future
     * returned by the consumer is succeeded.
     *
     * @param tenantId The tenant to consume messages for.
     * @param messageHandler The handler to invoke with every message received. The future returned
     *                        will be succeeded if the message has been processed successfully.
     *                        It will be failed if an error occurred while processing the message.
     *                        <p>
     *                        Implementors are encouraged to specify in detail the types of exceptions that the
     *                        future might be failed with, what kind of problem they indicate and what the
     *                        consequences regarding the underlying messaging infrastructure will be.
     * @param closeHandler An (optional) handler to be invoked when the consumer is being closed by the peer.
     *                     The handler will be invoked with an exception indicating the cause of the consumer
     *                     being closed or {@code null} if unknown.
     *                     <p>
     *                     Note that the handler will also be invoked when the underlying AMQP connection is being
     *                     closed by the remote peer without the peer having explicitly closed the consumer before.
     *                     <p>
     *                     Implementors are encouraged to specify in detail the types of exceptions that might
     *                     be passed in, what kind of problem they indicate and what the consequences regarding the
     *                     underlying messaging infrastructure will be.
     * @return A future that will complete with the consumer once it is ready. The future will fail if the consumer
     *         cannot be started.
     * @throws NullPointerException if any of tenant ID or message handler are {@code null}.
     */
    Future<MessageConsumer> createEventConsumer(
            String tenantId,
            Function<DownstreamMessage<AmqpMessageContext>, Future<Void>> messageHandler,
            Handler<Throwable> closeHandler);
}
