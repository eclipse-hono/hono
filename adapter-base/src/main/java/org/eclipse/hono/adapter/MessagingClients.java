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
 * The messaging clients present in a protocol adapter.
 * <p>
 * It contains one set of clients for each connected messaging system type and allows to get the messaging clients to be
 * used for a tenant based on the tenants configuration.
 */
public final class MessagingClients implements Lifecycle {

    private final Logger log = LoggerFactory.getLogger(MessagingClients.class);
    private final HashMap<MessagingType, MessagingClientSet> clientSets = new HashMap<>();

    /**
     * Adds a set of messaging clients of for a messaging type.
     *
     * @param clientSet The client set to be added.
     * @return a reference to this for fluent use.
     * @throws NullPointerException if the client set is {@code null}
     */
    public MessagingClients addClientSet(final MessagingClientSet clientSet) {
        Objects.requireNonNull(clientSet);
        log.info("adding messaging client set of type {}", clientSet.getType().name());
        clientSets.put(clientSet.getType(), clientSet);
        return this;
    }

    /**
     * Gets the messaging client set to be used for a tenant.
     * <p>
     * The property to determine the messaging type is expected in the {@link TenantConstants#FIELD_EXT_MESSAGING_TYPE}
     * inside of the {@link TenantConstants#FIELD_EXT} property of the tenant. Valid values are the names of
     * {@link MessagingType}.
     *
     * @param tenant The tenant to select the messaging clients for.
     * @return The messaging client set that is configured at the tenant or, if this is missing and only one client set
     *         is present, that one, otherwise, the AMQP-based client.
     * @throws IllegalStateException if no client set has been added.
     */
    public MessagingClientSet getClientSetForTenant(final TenantObject tenant) {

        if (clientSets.isEmpty()) {
            throw new IllegalStateException("No messaging client present");
        }

        // check if configured on the tenant
        final MessagingClientSet tenantConfiguredClientSet = getTenantConfiguredClientSet(tenant);
        if (tenantConfiguredClientSet != null) {
            return tenantConfiguredClientSet;
        }

        // not configured -> check if only one client set present
        if (clientSets.size() == 1) {
            return clientSets.values().stream().findFirst().get();
        }

        // multiple client sets are present -> fallback to default
        return clientSets.get(MessagingType.amqp);
    }

    private MessagingClientSet getTenantConfiguredClientSet(final TenantObject tenant) {
        final JsonObject ext = Optional.ofNullable(tenant.getProperty(TenantConstants.FIELD_EXT, JsonObject.class))
                .orElse(new JsonObject());
        final String configuredType = ext.getString(TenantConstants.FIELD_EXT_MESSAGING_TYPE);

        if (configuredType != null) {
            return clientSets.get(MessagingType.valueOf(configuredType));
        } else {
            return null;
        }
    }

    /**
     * Checks if no client set is present.
     *
     * @return true if no client set has been added.
     */
    public boolean isUnconfigured() {
        return clientSets.isEmpty();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Future<Void> start() {
        if (clientSets.isEmpty()) {
            throw new IllegalStateException("No messaging client set present");
        }

        final List<Future> startFutures = new ArrayList<>();
        for (final MessagingClientSet clientSet : clientSets.values()) {
            startFutures.add(clientSet.start());
        }
        return CompositeFuture.all(startFutures).mapEmpty();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Future<Void> stop() {
        final List<Future> stopFutures = new ArrayList<>();
        for (final MessagingClientSet clientSet : clientSets.values()) {
            stopFutures.add(clientSet.stop());
        }
        return CompositeFuture.all(stopFutures).mapEmpty();
    }

    /**
     * Registers liveness checks for all clients.
     *
     * @param handler The handler to register the checks with.
     */
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        for (final MessagingClientSet clientSet : clientSets.values()) {
            clientSet.registerLivenessChecks(handler);
        }
    }

    /**
     * Registers readiness checks for all clients.
     *
     * @param handler The handler to register the checks with.
     */
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        for (final MessagingClientSet clientSet : clientSets.values()) {
            clientSet.registerReadinessChecks(handler);
        }
        // TODO come up with ideas for readiness checks for the Kafka producers
    }

}
