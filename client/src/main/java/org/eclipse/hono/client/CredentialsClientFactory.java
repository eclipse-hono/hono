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

import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.impl.CredentialsClientFactoryImpl;

import io.vertx.core.Future;

/**
 * A factory for creating clients for Hono's Credentials API.
 *
 */
public interface CredentialsClientFactory extends ConnectionLifecycle<HonoConnection> {

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to use.
     * @return The factory.
     * @throws NullPointerException if connection is {@code null}
     */
    static CredentialsClientFactory create(final HonoConnection connection) {
        return new CredentialsClientFactoryImpl(connection, null);
    }

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to use.
     * @param cacheProvider The cache provider to use for creating caches for credential objects
     *                      or {@code null} if credential objects should not be cached.
     * @return The factory.
     * @throws NullPointerException if connection is {@code null}
     */
    static CredentialsClientFactory create(final HonoConnection connection, final CacheProvider cacheProvider) {
        return new CredentialsClientFactoryImpl(connection, cacheProvider);
    }

    /**
     * Gets a client for interacting with Hono's <em>Credentials</em> API.
     * <p>
     * The client returned may be either newly created or it may be an existing
     * client for the given tenant.
     *
     * @param tenantId The tenant to manage device credentials data for.
     * @return A future that will complete with the credentials client (if successful) or fail if the client cannot be
     *         created, e.g. because the underlying connection is not established or if a concurrent request to create a
     *         client for the same tenant is already being executed.
     * @throws NullPointerException if the tenant is {@code null}.
     */
    Future<CredentialsClient> getOrCreateCredentialsClient(String tenantId);
}
