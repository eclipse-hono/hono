/**
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
import org.eclipse.hono.client.impl.TenantClientFactoryImpl;

import io.vertx.core.Future;

/**
 * A factory for creating clients for Hono's Tenant API.
 *
 * @deprecated Use {@code org.eclipse.hono.client.registry.amqp.ProtonBasedTenantClient} instead.
 */
@Deprecated
public interface TenantClientFactory extends ConnectionLifecycle<HonoConnection> {

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to use.
     * @return The factory.
     * @throws NullPointerException if connection is {@code null}
     */
    static TenantClientFactory create(final HonoConnection connection) {
        return new TenantClientFactoryImpl(connection, null, SendMessageSampler.Factory.noop());
    }

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to use.
     * @param cacheProvider The provider to use for creating caches for tenant objects
     *                      or {@code null} if tenant objects should not be cached.
     * @param samplerFactory The sampler factory to use.
     * @return The factory.
     * @throws NullPointerException if connection is {@code null}
     */
    static TenantClientFactory create(final HonoConnection connection, final CacheProvider cacheProvider, final SendMessageSampler.Factory samplerFactory) {
        return new TenantClientFactoryImpl(connection, cacheProvider, samplerFactory);
    }

    /**
     * Gets a client for interacting with Hono's <em>Tenant</em> API.
     * <p>
     * The client returned may be either newly created or it may be an existing
     * client for the given tenant.
     *
     * @return A future that will complete with the tenant client (if successful) or fail if the client cannot be
     *         created, e.g. because the underlying connection is not established or if a concurrent request to create a
     *         client for the same tenant is already being executed.
     */
    Future<TenantClient> getOrCreateTenantClient();
}
