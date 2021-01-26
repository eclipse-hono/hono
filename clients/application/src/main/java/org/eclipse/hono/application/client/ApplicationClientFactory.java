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

package org.eclipse.hono.application.client;

import java.util.function.Consumer;

import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * A factory for creating clients for Hono's north bound APIs.
 *
 * @param <T> The type of context that messages are being received in.
 */
public interface ApplicationClientFactory<T extends MessageContext> {

    /**
     * Creates a client for consuming data from Hono's north bound <em>Telemetry API</em>.
     * <p>
     * The messages passed in to the consumer will be acknowledged automatically if the consumer does not throw an
     * exception.
     *
     * @param tenantId The tenant to consume data for.
     * @param telemetryConsumer The handler to invoke with every message received.
     * @param closeHandler The handler invoked when the consumer is closed (not when the closing was triggered by
     *            calling {@link MessageConsumer#close()}). If the consumer is closed due to an error, the cause is
     *            passed to the handler. Otherwise, the throwable is {@code null}.
     * @return A future that will complete with the consumer once it is ready. The future will fail if the consumer
     *         cannot be started.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<MessageConsumer<DownstreamMessage<T>>> createTelemetryConsumer(
            String tenantId,
            Consumer<DownstreamMessage<T>> telemetryConsumer,
            Handler<Throwable> closeHandler);

    /**
     * Creates a client for consuming events from Hono's northbound <em>Event API</em>.
     * <p>
     * The messages passed in to the consumer will be acknowledged automatically if the consumer does not throw an
     * exception.
     *
     * @param tenantId The tenant to consume data for.
     * @param eventConsumer The handler to invoke with every message received.
     * @param closeHandler The handler invoked when the consumer is closed (not when the closing was triggered by
     *            calling {@link MessageConsumer#close()}). If the consumer is closed due to an error, the cause is
     *            passed to the handler. Otherwise, the throwable is {@code null}.
     * @return A future that will complete with the consumer once it is ready. The future will fail if the consumer
     *         cannot be started.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<MessageConsumer<DownstreamMessage<T>>> createEventConsumer(
            String tenantId,
            Consumer<DownstreamMessage<T>> eventConsumer,
            Handler<Throwable> closeHandler);

    // TODO add methods for command & control

}
