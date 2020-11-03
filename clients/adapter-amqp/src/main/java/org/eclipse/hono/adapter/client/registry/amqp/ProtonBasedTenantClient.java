/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.client.registry.amqp;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.client.impl.TenantClientImpl;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;

import io.opentracing.SpanContext;
import io.vertx.core.Future;


/**
 * A vertx-proton based client of Hono's Tenant service.
 *
 */
public final class ProtonBasedTenantClient extends AbstractRequestResponseClient<TenantResult<TenantObject>> implements TenantClient {

    private final CachingClientFactory<org.eclipse.hono.client.TenantClient> tenantClientFactory;

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @param cacheProvider The cache provider to use for creating the cache for service responses.
     * @throws NullPointerException if any of the parameters other than cacheProvider are {@code null}.
     */
    public ProtonBasedTenantClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig,
            final CacheProvider cacheProvider) {
        super(connection, samplerFactory, adapterConfig, cacheProvider);
        this.tenantClientFactory = new CachingClientFactory<>(connection.getVertx(), c -> c.isOpen());
    }

    private Future<org.eclipse.hono.client.TenantClient> getOrCreateTenantClient() {

        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    tenantClientFactory.getOrCreateClient(
                            TenantClientImpl.getTargetAddress(),
                            () -> TenantClientImpl.create(
                                    responseCacheProvider,
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

    /**
     * {@inheritDoc}
     *
     * Clears the state of the client factory.
     */
    @Override
    protected void onDisconnect() {
        tenantClientFactory.clearState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<TenantObject> get(final String tenantId, final SpanContext context) {
        return getOrCreateTenantClient()
                .compose(client -> client.get(tenantId, context));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<TenantObject> get(final X500Principal subjectDn, final SpanContext context) {
        return getOrCreateTenantClient()
                .compose(client -> client.get(subjectDn, context));
    }
}
