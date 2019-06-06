/**
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
 */


package org.eclipse.hono.client;

import org.eclipse.hono.client.impl.DownstreamSenderFactoryImpl;

import io.vertx.core.Future;

/**
 * A factory for creating clients for Hono's south bound Telemetry and Event APIs.
 *
 */
public interface DownstreamSenderFactory extends ConnectionLifecycle<HonoConnection> {

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to use.
     * @return The factory.
     * @throws NullPointerException if connection is {@code null}
     */
    static DownstreamSenderFactory create(final HonoConnection connection) {
        return new DownstreamSenderFactoryImpl(connection);
    }

    /**
     * Gets a client for sending data to Hono's south bound <em>Telemetry</em> API.
     * <p>
     * The client returned may be either newly created or it may be an existing
     * client for the given tenant.
     *
     * @param tenantId The ID of the tenant to send messages for.
     * @return A future that will complete with the sender once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this client is not connected or if a concurrent request to
     *         create a sender for the same tenant is already being executed.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    Future<DownstreamSender> getOrCreateTelemetrySender(String tenantId);

    /**
     * Gets a client for sending data to Hono's south bound <em>Event</em> API.
     * <p>
     * The client returned may be either newly created or it may be an existing
     * client for the given tenant.
     *
     * @param tenantId The ID of the tenant to send messages for.
     * @return A future that will complete with the sender once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this client is not connected or if a concurrent request to
     *         create a sender for the same tenant is already being executed.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    Future<DownstreamSender> getOrCreateEventSender(String tenantId);
}
