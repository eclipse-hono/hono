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


package org.eclipse.hono.client.impl;

import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.util.TenantConstants;

import io.vertx.core.Future;


/**
 * A factory for creating clients for Hono's Tenant service.
 *
 */
public class TenantClientFactoryImpl extends AbstractHonoClientFactory implements TenantClientFactory {

    private final CachingClientFactory<TenantClient> tenantClientFactory;
    private final CacheProvider cacheProvider;

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to use.
     * @param cacheProvider The cache provider to use for creating caches for tenant objects
     *                      or {@code null} if tenant objects should not be cached.
     * @param samplerFactory The sampler factory to use.
     * @throws NullPointerException if connection is {@code null}
     */
    public TenantClientFactoryImpl(final HonoConnection connection, final CacheProvider cacheProvider, final SendMessageSampler.Factory samplerFactory) {
        super(connection, samplerFactory);
        this.tenantClientFactory = new CachingClientFactory<>(connection.getVertx(), c -> c.isOpen());
        this.cacheProvider = cacheProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDisconnect() {
        tenantClientFactory.clearState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<TenantClient> getOrCreateTenantClient() {

        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    tenantClientFactory.getOrCreateClient(
                            TenantClientImpl.getTargetAddress(),
                            () -> TenantClientImpl.create(
                                    cacheProvider,
                                    connection,
                                    samplerFactory.create(TenantConstants.TENANT_ENDPOINT),
                                    this::removeTenantClient,
                                    this::removeTenantClient),
                            result);
                }));
    }

    private void removeTenantClient(final String tenantId) {
        // the tenantId is not relevant for this client, so ignore it
        tenantClientFactory.removeClient(TenantClientImpl.getTargetAddress());
    }
}
