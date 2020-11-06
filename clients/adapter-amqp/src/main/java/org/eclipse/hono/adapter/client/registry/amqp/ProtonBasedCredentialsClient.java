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

import java.util.Objects;

import org.eclipse.hono.adapter.client.registry.CredentialsClient;
import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.client.impl.CredentialsClientImpl;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;


/**
 * A vertx-proton based client of Hono's Credentials service.
 *
 */
public class ProtonBasedCredentialsClient extends AbstractRequestResponseClient<CredentialsResult<CredentialsObject>> implements CredentialsClient {

    private final CachingClientFactory<org.eclipse.hono.client.CredentialsClient> clientFactory;

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the Credentials service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @param cacheProvider The cache provider to use for creating a cache for service responses or
     *                      {@code null} if responses should not be cached.
     * @throws NullPointerException if any of the parameters other than the cache provider are {@code null}.
     */
    public ProtonBasedCredentialsClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig,
            final CacheProvider cacheProvider) {
        super(connection, samplerFactory, adapterConfig, cacheProvider);
        this.clientFactory = new CachingClientFactory<>(connection.getVertx(), org.eclipse.hono.client.CredentialsClient::isOpen);
        connection.getVertx().eventBus().consumer(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);
    }

    private void removeCredentialsClient(final String tenantId) {
        clientFactory.removeClient(CredentialsClientImpl.getTargetAddress(tenantId));
    }

    private void handleTenantTimeout(final Message<String> msg) {
        final String address = CredentialsClientImpl.getTargetAddress(msg.body());
        final org.eclipse.hono.client.CredentialsClient client = clientFactory.getClient(address);
        if (client != null) {
            client.close(v -> clientFactory.removeClient(address));
        }
    }

    private Future<org.eclipse.hono.client.CredentialsClient> getOrCreateCredentialsClient(
            final String tenantId) {

        Objects.requireNonNull(tenantId);
        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    clientFactory.getOrCreateClient(
                            CredentialsClientImpl.getTargetAddress(tenantId),
                            () -> CredentialsClientImpl.create(
                                    responseCacheProvider,
                                    connection,
                                    tenantId,
                                    samplerFactory.create(CredentialsConstants.CREDENTIALS_ENDPOINT),
                                    this::removeCredentialsClient,
                                    this::removeCredentialsClient),
                            result);
                }));
    }

    /**
     * {@inheritDoc}
     *
     * Clears the state of the client factory.
     */
    @Override
    protected void onDisconnect() {
        clientFactory.clearState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<CredentialsObject> get(
            final String tenantId,
            final String type,
            final String authId,
            final SpanContext spanContext) {

        return get(tenantId, type, authId, new JsonObject(), spanContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<CredentialsObject> get(
            final String tenantId,
            final String type,
            final String authId,
            final JsonObject clientContext,
            final SpanContext spanContext) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(clientContext);

        return getOrCreateCredentialsClient(tenantId)
                .compose(client -> client.get(type, authId, clientContext, spanContext));
    }
}
