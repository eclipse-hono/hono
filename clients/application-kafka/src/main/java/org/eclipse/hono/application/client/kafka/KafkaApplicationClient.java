/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.application.client.kafka;

import org.eclipse.hono.application.client.ApplicationClient;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;

import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * A Kafka based client that supports Hono's north bound operations to send commands and receive telemetry,
 * event and command response messages.
 */
public interface KafkaApplicationClient extends ApplicationClient<KafkaMessageContext> {

    /**
     * Creates a client for consuming data from Hono's north bound <em>Telemetry API</em>.
     *
     * @param tenantId The tenant to consume data for.
     * @param messageHandler The handler to be invoked for each message created from a record.
     * @param closeHandler The handler invoked when the consumer is closed due to an error.
     * @return A future that will complete with the consumer once it is ready. The future will fail if the consumer
     *         cannot be started.
     * @throws NullPointerException if any of tenant ID or message handler are {@code null}.
     */
    @Override
    Future<MessageConsumer> createTelemetryConsumer(
            String tenantId,
            Handler<DownstreamMessage<KafkaMessageContext>> messageHandler,
            Handler<Throwable> closeHandler);

    /**
     * Creates a client for consuming data from Hono's north bound <em>Event API</em>.
     *
     * @param tenantId The tenant to consume data for.
     * @param messageHandler The handler to be invoked for each message created from a record.
     * @param closeHandler The handler invoked when the consumer is closed due to an error.
     * @return A future that will complete with the consumer once it is ready. The future will fail if the consumer
     *         cannot be started.
     * @throws NullPointerException if any of tenant ID or message handler are {@code null}.
     */
    @Override
    Future<MessageConsumer> createEventConsumer(
            String tenantId,
            Handler<DownstreamMessage<KafkaMessageContext>> messageHandler,
            Handler<Throwable> closeHandler);
}
