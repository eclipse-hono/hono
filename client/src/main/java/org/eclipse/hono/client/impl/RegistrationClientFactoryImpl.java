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
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.util.Constants;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;


/**
 * A factory for creating clients for the Hono APIs required
 * by protocol adapters.
 *
 */
public class RegistrationClientFactoryImpl extends AbstractHonoClientFactory implements RegistrationClientFactory {

    private final CachingClientFactory<RegistrationClient> registrationClientFactory;
    private final CacheProvider cacheProvider;

    /**
     * Creates a new factory for an existing connection.
     * 
     * @param connection The connection to use.
     * @param cacheProvider The cache provider to use for creating caches for tenant objects
     *                      or {@code null} if tenant objects should not be cached.
     * @throws NullPointerException if connection is {@code null}
     */
    public RegistrationClientFactoryImpl(final HonoConnection connection, final CacheProvider cacheProvider) {
        super(connection);
        this.registrationClientFactory = new CachingClientFactory<>(connection.getVertx(), c -> c.isOpen());
        this.cacheProvider = cacheProvider;
        connection.getVertx().eventBus().consumer(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDisconnect() {
        registrationClientFactory.clearState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<RegistrationClient> getOrCreateRegistrationClient(final String tenantId) {

        Objects.requireNonNull(tenantId);

        return connection.executeOrRunOnContext(result -> {
            registrationClientFactory.getOrCreateClient(
                    RegistrationClientImpl.getTargetAddress(tenantId),
                    () -> RegistrationClientImpl.create(
                            cacheProvider,
                            connection,
                            tenantId,
                            this::removeRegistrationClient,
                            this::removeRegistrationClient),
                    result);
        });
    }

    private void removeRegistrationClient(final String tenantId) {
        registrationClientFactory.removeClient(RegistrationClientImpl.getTargetAddress(tenantId));
    }

    private void handleTenantTimeout(final Message<String> msg) {
        final String address = RegistrationClientImpl.getTargetAddress(msg.body());
        final RegistrationClient client = registrationClientFactory.getClient(address);
        if (client != null) {
            client.close(v -> registrationClientFactory.removeClient(address));
        }
    }
}
