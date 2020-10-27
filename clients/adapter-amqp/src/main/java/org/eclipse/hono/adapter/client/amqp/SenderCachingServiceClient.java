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


package org.eclipse.hono.adapter.client.amqp;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.AddressHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
    private final CachingClientFactory<DownstreamSender> clientFactory;

    /**
     * Creates a new client.
     *
     * @param connection The connection to the Hono service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected SenderCachingServiceClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {

        super(connection, samplerFactory, adapterConfig);
        this.clientFactory = new CachingClientFactory<>(connection.getVertx(), s -> s.isOpen());
        connection.getVertx().eventBus().consumer(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);
    }

    private void handleTenantTimeout(final Message<String> msg) {
        List.of(AddressHelper.getTargetAddress(TelemetryConstants.TELEMETRY_ENDPOINT, msg.body(), null, connection.getConfig()),
                AddressHelper.getTargetAddress(EventConstants.EVENT_ENDPOINT, msg.body(), null, connection.getConfig()))
            .forEach(key -> Optional.ofNullable(clientFactory.getClient(key)).ifPresent(client -> client.close(v -> clientFactory.removeClient(key))));
    }

    /**
     * Gets an existing or creates a new sender for telemetry and/or event messages.
     * <p>
     * This method first tries to look up an already existing
     * sender using the given key. If no sender exists yet, a new
     * instance is created using the given factory and put to the cache.
     *
     * @param key The key to cache the sender under.
     * @param clientInstanceSupplier The factory to use for creating a
     *        new sender (if necessary).
     * @param result The handler to invoke with the outcome of the creation attempt.
     *         The handler will be invoked with a succeeded future containing
     *         the sender or with a failed future containing a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} if no sender
     *         could be created.
     */
    protected void getOrCreateSender(
            final String key,
            final Supplier<Future<DownstreamSender>> clientInstanceSupplier,
            final Handler<AsyncResult<DownstreamSender>> result) {

        clientFactory.getOrCreateClient(key, clientInstanceSupplier, result);
    }

    /**
     * Removes a sender from the cache.
     *
     * @param key The key of the sender to remove.
     */
    protected void removeClient(final String key) {
        clientFactory.removeClient(key);
    }

}
