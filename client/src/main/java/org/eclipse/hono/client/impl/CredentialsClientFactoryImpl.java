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


package org.eclipse.hono.client.impl;

import java.util.Objects;

import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.HonoConnection;

import io.vertx.core.Future;


/**
 * A factory for creating clients for the Hono APIs required
 * by protocol adapters.
 *
 */
public class CredentialsClientFactoryImpl extends AbstractHonoClientFactory implements CredentialsClientFactory {

    private final CachingClientFactory<CredentialsClient> credentialsClientFactory;
    private final CacheProvider cacheProvider;

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to use.
     * @param cacheProvider The cache provider to use for creating caches for credential objects
     *                      or {@code null} if credentials objects should not be cached.
     */
    public CredentialsClientFactoryImpl(final HonoConnection connection, final CacheProvider cacheProvider) {
        super(connection);
        credentialsClientFactory = new CachingClientFactory<>(c -> c.isOpen());
        this.cacheProvider = cacheProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDisconnect() {
        credentialsClientFactory.clearState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<CredentialsClient> getOrCreateCredentialsClient(
            final String tenantId) {

        Objects.requireNonNull(tenantId);
        return connection.executeOrRunOnContext(result -> {
            credentialsClientFactory.getOrCreateClient(
                    CredentialsClientImpl.getTargetAddress(tenantId),
                    () -> CredentialsClientImpl.create(
                            cacheProvider,
                            connection,
                            tenantId,
                            this::removeCredentialsClient,
                            this::removeCredentialsClient),
                    result);
        });
    }

    private void removeCredentialsClient(final String tenantId) {
        credentialsClientFactory.removeClient(CredentialsClientImpl.getTargetAddress(tenantId));
    }
}
