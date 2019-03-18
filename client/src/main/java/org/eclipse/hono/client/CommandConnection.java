/*******************************************************************************
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import org.eclipse.hono.client.impl.CommandConnectionImpl;
import org.eclipse.hono.config.ClientConfigProperties;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * A bidirectional connection between a <em>Protocol Adapter</em> and the
 * <em>AMQP 1.0 Messaging Network</em> to receive commands and send
 * responses.
 */
public interface CommandConnection extends HonoClient, CommandConsumerFactory {

    /**
     * Closes the command consumer for a given device.
     *
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the consumer will be created.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if tenantId or deviceId are {@code null}.
     * @deprecated This method will be removed in Hono 1.0. Use {@link CommandConsumer#close(Handler)} instead.
     */
    @Deprecated
    Future<Void> closeCommandConsumer(String tenantId, String deviceId);

    /**
     * Creates a new client for a set of configuration properties.
     *
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param clientConfigProperties The configuration properties to use.
     *
     * @return CommandConnection The client that was created.
     * @throws NullPointerException if clientConfigProperties is {@code null}
     */
    static CommandConnection newConnection(final Vertx vertx, final ClientConfigProperties clientConfigProperties) {
        return new CommandConnectionImpl(vertx, clientConfigProperties);
    }
}
