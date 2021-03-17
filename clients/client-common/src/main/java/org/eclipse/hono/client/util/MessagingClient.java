/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;

/**
 * A wrapper around implementations of Hono's south bound APIs using arbitrary types of messaging systems.
 *
 * @param <T> The type of client to wrap.
 */
public final class MessagingClient<T extends Lifecycle> implements Lifecycle, ServiceClient {

    private final Map<MessagingType, T> clientImplementations = new HashMap<>();

    private void requireClientsConfigured() {
        if (!containsImplementations()) {
            throw new IllegalStateException("no messaging client configured");
        }
    }

    /**
     * Checks if any clients are set.
     *
     * @return {@code true} if at least one implementation has been set.
     */
    public boolean containsImplementations() {
        return !clientImplementations.isEmpty();
    }

    /**
     * Sets a client to use for a particular type of messaging system.
     *
     * @param type The messaging system type.
     * @param client The client.
     * @return A reference to this wrapper.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MessagingClient<T> setClient(final MessagingType type, final T client) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(client);
        this.clientImplementations.put(type, client);
        return this;
    }

    /**
     * Gets an implementation for a particular messaging system.
     * <p>
     * The type of messaging system is determined from the given tenant's
     * <em>ext/messaging-type</em> property. Supported values are the names of
     * {@link MessagingType}.
     *
     * @param tenant The tenant to get a client for.
     * @return The client.
     * @throws NullPointerException if tenant is {@code null}.
     * @throws IllegalStateException if no client implementations are set.
     */
    public T getClient(final TenantObject tenant) {

        Objects.requireNonNull(tenant);
        requireClientsConfigured();

        return Optional.ofNullable(tenant.getProperty(TenantConstants.FIELD_EXT, JsonObject.class))
            .map(ext -> ext.getString(TenantConstants.FIELD_EXT_MESSAGING_TYPE))
            .map(type -> {
                return clientImplementations.get(MessagingType.valueOf(type));
            })
            .orElseGet(this::getDefaultImplementation);
    }

    private T getDefaultImplementation() {
        if (clientImplementations.size() == 1) {
            return clientImplementations.values().iterator().next();
        }

        // multiple client sets are present -> fallback to default
        return clientImplementations.get(MessagingType.amqp);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        clientImplementations.values().stream()
            .filter(ServiceClient.class::isInstance)
            .map(ServiceClient.class::cast)
            .forEach(client -> client.registerReadinessChecks(readinessHandler));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        clientImplementations.values().stream()
            .filter(ServiceClient.class::isInstance)
            .map(ServiceClient.class::cast)
            .forEach(client -> client.registerLivenessChecks(livenessHandler));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> start() {
        requireClientsConfigured();

        @SuppressWarnings("rawtypes")
        final List<Future> futures = clientImplementations.values()
                .stream()
                .map(Lifecycle::start)
                .collect(Collectors.toList());
        return CompositeFuture.all(futures).mapEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> stop() {
        @SuppressWarnings("rawtypes")
        final List<Future> futures = clientImplementations.values()
                .stream()
                .map(Lifecycle::stop)
                .collect(Collectors.toList());
        return CompositeFuture.all(futures).mapEmpty();
    }
}
