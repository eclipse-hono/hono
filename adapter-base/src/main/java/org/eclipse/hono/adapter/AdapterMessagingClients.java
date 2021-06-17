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

import java.util.Objects;

import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.util.MessagingClients;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.TenantObject;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.ext.healthchecks.HealthCheckHandler;

/**
 * A wrapper around the messaging clients required by protocol adapters.
 * <p>
 * It contains clients for each of Hono's south bound API endpoints.
 */
public final class AdapterMessagingClients implements Lifecycle {

    private final MessagingClients<TelemetrySender> telemetrySenders;
    private final MessagingClients<EventSender> eventSenders;
    private final MessagingClients<CommandResponseSender> commandResponseSenders;

    /**
     * Creates a new instance.
     *
     * @param telemetrySenders The clients for sending telemetry messages downstream.
     * @param eventSenders The clients for sending events downstream.
     * @param commandResponseSenders The clients for sending command response messages downstream.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if any of the senders does not contain at least one client implementation.
     */
    public AdapterMessagingClients(
            final MessagingClients<TelemetrySender> telemetrySenders,
            final MessagingClients<EventSender> eventSenders,
            final MessagingClients<CommandResponseSender> commandResponseSenders) {

        Objects.requireNonNull(telemetrySenders);
        Objects.requireNonNull(eventSenders);
        Objects.requireNonNull(commandResponseSenders);

        if (!telemetrySenders.containsImplementations()) {
            throw new IllegalArgumentException("at least one TelemetrySender implementation must be set");
        }
        if (!eventSenders.containsImplementations()) {
            throw new IllegalArgumentException("at least one EventSender implementation must be set");
        }
        if (!commandResponseSenders.containsImplementations()) {
            throw new IllegalArgumentException("at least one CommandResponseSender implementation must be set");
        }
        this.telemetrySenders = telemetrySenders;
        this.eventSenders = eventSenders;
        this.commandResponseSenders = commandResponseSenders;
    }

    /**
     * Gets a client for sending events using a particular messaging system.
     *
     * @param tenant The tenant to get the client for.
     * @return The client.
     */
    public EventSender getEventSender(final TenantObject tenant) {
        return eventSenders.getClient(tenant);
    }

    /**
     * Gets a client for sending telemetry messages using a particular messaging system.
     *
     * @param tenant The tenant to get the client for.
     * @return The client.
     */
    public TelemetrySender getTelemetrySender(final TenantObject tenant) {
        return telemetrySenders.getClient(tenant);
    }

    /**
     * Gets a client for sending command response messages using a particular messaging system.
     *
     * @param tenant The tenant to get the client for.
     * @return The client.
     */
    public CommandResponseSender getCommandResponseSender(final TenantObject tenant) {
        return commandResponseSenders.getClient(tenant);
    }

    /**
     * Gets the clients for sending telemetry messages downstream.
     *
     * @return The clients.
     */
    public MessagingClients<TelemetrySender> getTelemetrySenders() {
        return telemetrySenders;
    }

    /**
     * Gets the clients for sending event messages downstream.
     *
     * @return The clients.
     */
    public MessagingClients<EventSender> getEventSenders() {
        return eventSenders;
    }

    /**
     * Gets the clients for sending command response messages downstream.
     *
     * @return The clients.
     */
    public MessagingClients<CommandResponseSender> getCommandResponseSenders() {
        return commandResponseSenders;
    }

    @Override
    public Future<Void> start() {

        return CompositeFuture.all(
                telemetrySenders.start(),
                eventSenders.start(),
                commandResponseSenders.start()).mapEmpty();
    }

    @Override
    public Future<Void> stop() {
        return CompositeFuture.all(
                telemetrySenders.stop(),
                eventSenders.stop(),
                commandResponseSenders.stop()).mapEmpty();
    }

    /**
     * Registers liveness checks for all clients.
     *
     * @param handler The handler to register the checks with.
     */
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        telemetrySenders.registerLivenessChecks(handler);
        eventSenders.registerLivenessChecks(handler);
        commandResponseSenders.registerLivenessChecks(handler);
    }

    /**
     * Registers readiness checks for all clients.
     *
     * @param handler The handler to register the checks with.
     */
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        telemetrySenders.registerReadinessChecks(handler);
        eventSenders.registerReadinessChecks(handler);
        commandResponseSenders.registerReadinessChecks(handler);
    }
}
