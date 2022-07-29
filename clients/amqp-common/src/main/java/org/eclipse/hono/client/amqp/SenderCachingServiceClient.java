/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client.amqp;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.amqp.config.AddressHelper;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.util.CachingClientFactory;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;


/**
 * A base class for implementing AMQP 1.0 based clients for Hono's service APIs.
 * <p>
 * This class provides support for caching sender links.
 */
public abstract class SenderCachingServiceClient extends AbstractServiceClient {

    /**
     * The factory for creating downstream sender links.
     */
    private final CachingClientFactory<GenericSenderLink> clientFactory;

    /**
     * Creates a new client.
     * <p>
     * The created sender links are expected to be tenant-specific and are closed
     * when a tenant timeout occurs.
     *
     * @param connection The connection to the Hono service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected SenderCachingServiceClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory) {

        this(connection, samplerFactory, true);
    }

    /**
     * Creates a new client.
     *
     * @param connection The connection to the Hono service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param isTenantSpecificLink If the links created for this client are tenant-specific, leading
     *            them to get closed when a tenant timeout occurs.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected SenderCachingServiceClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final boolean isTenantSpecificLink) {

        super(connection, samplerFactory);
        this.clientFactory = new CachingClientFactory<>(connection.getVertx(), GenericSenderLink::isOpen);
        if (isTenantSpecificLink) {
            connection.getVertx().eventBus().consumer(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                    this::handleTenantTimeout);
        }
    }

    private void handleTenantTimeout(final Message<String> msg) {
        final String tenantId = msg.body();
        List.of(AddressHelper.getTargetAddress(TelemetryConstants.TELEMETRY_ENDPOINT, tenantId, null, connection.getConfig()),
                AddressHelper.getTargetAddress(EventConstants.EVENT_ENDPOINT, tenantId, null, connection.getConfig()))
            .forEach(key -> Optional.ofNullable(clientFactory.getClient(key))
                    .ifPresent(client -> client.close().onComplete(r -> clientFactory.removeClient(key))));
    }

    /**
     * Invoked when the underlying connection to the Hono server is lost unexpectedly.
     * <p>
     * Fails all pending client creation requests and clears the state of the client factory.
     */
    @Override
    protected void onDisconnect() {
        clientFactory.onDisconnect();
    }

    /**
     * Gets an existing or creates a new sender link.
     * <p>
     * This method first tries to look up an already existing
     * link using the given key. If no link exists yet, a new
     * instance is created using the endpoint and tenant ID for
     * the link's target address and put to the cache.
     *
     * @param endpoint The endpoint to get or create the link for.
     * @param tenantId The identifier of the tenant to get or create the link for.
     * @return A future indicating the outcome of the operation.
     *         The future will be completed with the sender link
     *         or will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}
     *         if no sender link could be created.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected final Future<GenericSenderLink> getOrCreateSenderLink(
            final String endpoint,
            final String tenantId) {

        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(tenantId);

        return connection
                .isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    final String key = AddressHelper.getTargetAddress(
                            endpoint,
                            tenantId,
                            null,
                            connection.getConfig());
                    clientFactory.getOrCreateClient(
                            key,
                            () -> GenericSenderLink.create(
                                    connection,
                                    endpoint,
                                    tenantId,
                                    samplerFactory.create(endpoint),
                                    onSenderClosed -> removeClient(key)),
                            result);
                }));
    }

    /**
     * Gets an existing or creates a new sender link.
     * <p>
     * This method first tries to look up an already existing
     * link using the given key. If no link exists yet, a new
     * instance is created using the given link target address
     * and put to the cache.
     * <p>
     * This method is to be used for creating links that are
     * independent of a particular tenant.
     *
     * @param address The target address of the link.
     * @return A future indicating the outcome of the operation.
     *         The future will be completed with the sender link
     *         or will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}
     *         if no sender link could be created.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final Future<GenericSenderLink> getOrCreateSenderLink(final String address) {
        Objects.requireNonNull(address);
        return connection
                .isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    final String key = AddressHelper.rewrite(address, connection.getConfig());
                    clientFactory.getOrCreateClient(
                            key,
                            () -> GenericSenderLink.create(
                                    connection,
                                    address,
                                    onSenderClosed -> removeClient(key)),
                            result);
                }));
    }

    /**
     * Removes a sender from the cache.
     *
     * @param key The key of the sender to remove.
     */
    protected final void removeClient(final String key) {
        clientFactory.removeClient(key);
    }
}
