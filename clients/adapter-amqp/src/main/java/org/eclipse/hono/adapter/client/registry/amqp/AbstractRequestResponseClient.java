/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.adapter.client.registry.amqp;

import org.eclipse.hono.adapter.client.amqp.AbstractServiceClient;
import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.RequestResponseResult;

/**
 * A vertx-proton based parent class for the implementation of API clients that follow the request response pattern.
 * <p>
 * Provides access to a {@link CacheProvider} which can be used to create caches for service response messages.
 *
 * @param <R> The type of result this client expects the peer to return.
 *
 */
public abstract class AbstractRequestResponseClient<R extends RequestResponseResult<?>>
        extends AbstractServiceClient {

    /**
     * A provider for caches to use for responses received from the service.
     */
    protected CacheProvider responseCacheProvider;

    /**
     * Creates a request-response client.
     *
     * @param connection The connection to the service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @param cacheProvider The provider to use for creating cache instances for service responses.
     * @throws NullPointerException if any of the parameters other than cacheProvider are {@code null}.
     */
    protected AbstractRequestResponseClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig,
            final CacheProvider cacheProvider) {

        super(connection, samplerFactory, adapterConfig);
        this.responseCacheProvider = cacheProvider;
    }
}
