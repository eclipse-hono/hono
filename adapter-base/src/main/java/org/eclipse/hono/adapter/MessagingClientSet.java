/*
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

package org.eclipse.hono.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;

/**
 * The set of messaging clients present in a protocol adapter.
 * <p>
 * It contains one client for each connected messaging system type and allows to get the messaging to be used for a
 * tenant based on the tenants configuration.
 */
public final class MessagingClientSet implements Lifecycle {

    private final Logger log = LoggerFactory.getLogger(MessagingClientSet.class);
    private final HashMap<MessagingType, MessagingClient> clients = new HashMap<>();

    /**
     * Adds a messaging client.
     *
     * @param client The client to be added.
     * @return a reference to this for fluent use.
     * @throws NullPointerException if the client is {@code null}
     */
    public MessagingClientSet addClient(final MessagingClient client) {
        Objects.requireNonNull(client);
        log.info("adding messaging client of type {}", client.getType().name());
        clients.put(client.getType(), client);
        return this;
    }

    /**
     * Gets the client to be used for messaging.
     * <p>
     * The property to determine the messaging type is expected in the {@link TenantConstants#FIELD_EXT_MESSAGING_TYPE}
     * inside of the {@link TenantConstants#FIELD_EXT} property of the tenant. Valid values are the names of
     * {@link MessagingType}.
     *
     * @param tenant The tenant to select the messaging cleint for.
     * @return The client that is configured at the tenant or, if this is missing and only one client is set, that one,
     *         otherwise, the AMQP-based client.
     * @throws IllegalStateException if no client is set.
     */
    public MessagingClient getClientForTenant(final TenantObject tenant) {

        if (clients.isEmpty()) {
            throw new IllegalStateException("No messaging client present");
        }

        // check if configured on the tenant
        final MessagingClient tenantConfiguredClient = getTenantConfiguredSender(tenant);
        if (tenantConfiguredClient != null) {
            return tenantConfiguredClient;
        }

        // not configured -> check if only one client set
        if (clients.size() == 1) {
            return clients.values().stream().findFirst().get();
        }

        // multiple clients are present -> fallback to default
        return clients.get(MessagingType.amqp);
    }

    private MessagingClient getTenantConfiguredSender(final TenantObject tenant) {
        final JsonObject ext = Optional.ofNullable(tenant.getProperty(TenantConstants.FIELD_EXT, JsonObject.class))
                .orElse(new JsonObject());
        final String configuredType = ext.getString(TenantConstants.FIELD_EXT_MESSAGING_TYPE);

        if (configuredType != null) {
            return clients.get(MessagingType.valueOf(configuredType));
        } else {
            return null;
        }
    }

    /**
     * Checks if no client is set.
     *
     * @return true if no client is set.
     */
    public boolean isUnconfigured() {
        return clients.isEmpty();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Future<Void> start() {
        if (clients.isEmpty()) {
            throw new IllegalStateException("No messaging client set");
        }

        final List<Future> startFutures = new ArrayList<>();
        for (final MessagingClient client : clients.values()) {
            startFutures.add(client.start());
        }
        return CompositeFuture.all(startFutures).mapEmpty();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Future<Void> stop() {
        final List<Future> stopFutures = new ArrayList<>();
        for (final MessagingClient client : clients.values()) {
            stopFutures.add(client.stop());
        }
        return CompositeFuture.all(stopFutures).mapEmpty();
    }

    /**
     * Registers liveness checks for all clients.
     *
     * @param handler The handler to register the checks with.
     */
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        for (final MessagingClient client : clients.values()) {
            client.registerLivenessChecks(handler);
        }
    }

    /**
     * Registers readiness checks for all clients.
     *
     * @param handler The handler to register the checks with.
     */
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        for (final MessagingClient client : clients.values()) {
            client.registerReadinessChecks(handler);
        }
        // TODO come up with ideas for readiness checks for the Kafka producers
    }

}
