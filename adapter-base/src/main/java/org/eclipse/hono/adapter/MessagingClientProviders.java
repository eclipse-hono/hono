/*
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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
import java.util.Optional;

import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.ext.healthchecks.HealthCheckHandler;

/**
 * A wrapper around the providers for the messaging clients required by protocol adapters.
 * <p>
 * It contains the providers for the clients for each of Hono's south bound API endpoints.
 */
public final class MessagingClientProviders implements Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(MessagingClientProviders.class);

    private final MessagingClientProvider<TelemetrySender> telemetrySenderProvider;
    private final MessagingClientProvider<EventSender> eventSenderProvider;
    private final MessagingClientProvider<CommandResponseSender> commandResponseSenderProvider;

    /**
     * Creates a new instance.
     *
     * @param telemetrySenderProvider The provider for the client for sending telemetry messages downstream.
     * @param eventSenderProvider The provider for the client for sending events downstream.
     * @param commandResponseSenderProvider The provider for the client for sending command response messages downstream.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if any of the providers does not contain at least one client implementation.
     */
    public MessagingClientProviders(
            final MessagingClientProvider<TelemetrySender> telemetrySenderProvider,
            final MessagingClientProvider<EventSender> eventSenderProvider,
            final MessagingClientProvider<CommandResponseSender> commandResponseSenderProvider) {

        Objects.requireNonNull(telemetrySenderProvider);
        Objects.requireNonNull(eventSenderProvider);
        Objects.requireNonNull(commandResponseSenderProvider);

        if (!telemetrySenderProvider.containsImplementations()) {
            throw new IllegalArgumentException("at least one TelemetrySender implementation must be set");
        }
        if (!eventSenderProvider.containsImplementations()) {
            throw new IllegalArgumentException("at least one EventSender implementation must be set");
        }
        if (!commandResponseSenderProvider.containsImplementations()) {
            throw new IllegalArgumentException("at least one CommandResponseSender implementation must be set");
        }
        this.telemetrySenderProvider = telemetrySenderProvider;
        this.eventSenderProvider = eventSenderProvider;
        this.commandResponseSenderProvider = commandResponseSenderProvider;
    }

    /**
     * Gets a client for sending events using a particular messaging system.
     *
     * @param tenant The tenant to get the client for.
     * @return The client.
     */
    public EventSender getEventSender(final TenantObject tenant) {
        return eventSenderProvider.getClient(tenant);
    }

    /**
     * Gets a client for sending telemetry messages using a particular messaging system.
     *
     * @param tenant The tenant to get the client for.
     * @return The client.
     */
    public TelemetrySender getTelemetrySender(final TenantObject tenant) {
        return telemetrySenderProvider.getClient(tenant);
    }

    /**
     * Gets a client for sending command response messages using a particular messaging system.
     *
     * @param messagingType The type of messaging system to use.
     * @param tenant The tenant to get the client for.
     * @return The client.
     */
    public CommandResponseSender getCommandResponseSender(final MessagingType messagingType, final TenantObject tenant) {
        return Optional.ofNullable(commandResponseSenderProvider.getClient(messagingType))
                .orElseGet(() -> {
                    LOG.info("no command response sender provider set for {} messaging type, using tenant or global default",
                            messagingType);
                    return commandResponseSenderProvider.getClient(tenant);
                });
    }

    /**
     * Gets the clients for sending telemetry messages downstream.
     *
     * @return The clients.
     */
    public MessagingClientProvider<TelemetrySender> getTelemetrySenderProvider() {
        return telemetrySenderProvider;
    }

    /**
     * Gets the clients for sending event messages downstream.
     *
     * @return The clients.
     */
    public MessagingClientProvider<EventSender> getEventSenderProvider() {
        return eventSenderProvider;
    }

    /**
     * Gets the clients for sending command response messages downstream.
     *
     * @return The clients.
     */
    public MessagingClientProvider<CommandResponseSender> getCommandResponseSenderProvider() {
        return commandResponseSenderProvider;
    }

    @Override
    public Future<Void> start() {

        return Future.all(
                telemetrySenderProvider.start(),
                eventSenderProvider.start(),
                commandResponseSenderProvider.start()).mapEmpty();
    }

    @Override
    public Future<Void> stop() {
        return Future.all(
                telemetrySenderProvider.stop(),
                eventSenderProvider.stop(),
                commandResponseSenderProvider.stop()).mapEmpty();
    }

    /**
     * Registers liveness checks for all clients.
     *
     * @param handler The handler to register the checks with.
     */
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        telemetrySenderProvider.registerLivenessChecks(handler);
        eventSenderProvider.registerLivenessChecks(handler);
        commandResponseSenderProvider.registerLivenessChecks(handler);
    }

    /**
     * Registers readiness checks for all clients.
     *
     * @param handler The handler to register the checks with.
     */
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        telemetrySenderProvider.registerReadinessChecks(handler);
        eventSenderProvider.registerReadinessChecks(handler);
        commandResponseSenderProvider.registerReadinessChecks(handler);
    }
}
