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

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.adapter.client.amqp.AbstractRequestResponseClient;
import org.eclipse.hono.adapter.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.client.impl.RegistrationClientImpl;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.DecodeException;


/**
 * A vertx-proton based client of Hono's Device Registration service.
 *
 */
public class ProtonBasedDeviceRegistrationClient extends AbstractRequestResponseClient<RegistrationResult>
        implements DeviceRegistrationClient {

    private final CachingClientFactory<org.eclipse.hono.client.RegistrationClient> clientFactory;

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the Device Registration service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @param cacheProvider The cache provider to use for creating a cache for service responses or
     *                      {@code null} if responses should not be cached.
     * @throws NullPointerException if any of the parameters other than the cache provider are {@code null}.
     */
    public ProtonBasedDeviceRegistrationClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig,
            final CacheProvider cacheProvider) {
        super(connection, samplerFactory, adapterConfig, cacheProvider);
        this.clientFactory = new CachingClientFactory<>(connection.getVertx(), RegistrationClient::isOpen);
        connection.getVertx().eventBus().consumer(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);
    }

    private Future<RegistrationClient> getOrCreateRegistrationClient(final String tenantId) {

        Objects.requireNonNull(tenantId);

        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    clientFactory.getOrCreateClient(
                            RegistrationClientImpl.getTargetAddress(tenantId),
                            () -> RegistrationClientImpl.create(
                                    responseCacheProvider,
                                    connection,
                                    tenantId,
                                    samplerFactory.create(RegistrationConstants.REGISTRATION_ENDPOINT),
                                    this::removeRegistrationClient,
                                    this::removeRegistrationClient),
                            result);
                }));
    }

    private void removeRegistrationClient(final String tenantId) {
        clientFactory.removeClient(RegistrationClientImpl.getTargetAddress(tenantId));
    }

    private void handleTenantTimeout(final Message<String> msg) {
        final String address = RegistrationClientImpl.getTargetAddress(msg.body());
        final RegistrationClient client = clientFactory.getClient(address);
        if (client != null) {
            client.close(v -> clientFactory.removeClient(address));
        }
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
    public Future<RegistrationAssertion> assertRegistration(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return getOrCreateRegistrationClient(tenantId)
                .compose(client -> client.assertRegistration(deviceId, gatewayId, context))
                .map(json -> {
                    try {
                        return json.mapTo(RegistrationAssertion.class);
                    } catch (final DecodeException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("registration service returned invalid response:{}{}",
                                    System.lineSeparator(), json.encodePrettily());
                        }
                        throw new ServerErrorException(
                                HttpURLConnection.HTTP_INTERNAL_ERROR,
                                "registration service returned invalid response");
                    }
                });
    }
}
