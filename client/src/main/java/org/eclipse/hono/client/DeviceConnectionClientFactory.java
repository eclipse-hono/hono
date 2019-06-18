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

import org.eclipse.hono.client.impl.DeviceConnectionClientFactoryImpl;

import io.vertx.core.Future;

/**
 * A factory for creating clients for Hono's Device Connection API.
 *
 */
public interface DeviceConnectionClientFactory extends ConnectionLifecycle<HonoConnection> {

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to use.
     * @return The factory.
     * @throws NullPointerException if connection is {@code null}
     */
    static DeviceConnectionClientFactory create(final HonoConnection connection) {
        return new DeviceConnectionClientFactoryImpl(connection);
    }

    /**
     * Gets a client for invoking operations on a service implementing Hono's <em>Device Connection</em> API.
     *
     * @param tenantId The tenant to manage device connection data for.
     * @return A future that will complete with the device connection client (if successful) or fail if the client
     *         cannot be created, e.g. because the underlying connection is not established or if a concurrent
     *         request to create a client for the same tenant is already being executed.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    Future<DeviceConnectionClient> getOrCreateDeviceConnectionClient(String tenantId);
}
