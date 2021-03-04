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

import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.util.ServiceClient;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessagingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.ext.healthchecks.HealthCheckHandler;

/**
 * A client for messaging used by a protocol adapter.
 */
public final class MessagingClient implements Lifecycle {

    private final Logger log = LoggerFactory.getLogger(MessagingClient.class);

    private final MessagingType type;
    private final EventSender eventSender;
    private final TelemetrySender telemetrySender;
    private final CommandResponseSender commandResponseSender;
    // TODO add command consumer

    /**
     * Creates a new messaging client.
     *
     * @param type The type of messaging of this client.
     * @param eventSender The event sender to be used.
     * @param telemetrySender The telemetry sender to be used.
     * @param commandResponseSender The command response sender to be used.
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    public MessagingClient(final MessagingType type, final EventSender eventSender,
            final TelemetrySender telemetrySender, final CommandResponseSender commandResponseSender) {

        this.type = Objects.requireNonNull(type);
        this.eventSender = Objects.requireNonNull(eventSender);
        this.telemetrySender = Objects.requireNonNull(telemetrySender);
        this.commandResponseSender = Objects.requireNonNull(commandResponseSender);
    }

    /**
     * Gets the messaging type of the client.
     *
     * @return The type.
     */
    public MessagingType getType() {
        return type;
    }

    /**
     * Gets the event sender.
     *
     * @return The sender.
     */
    public EventSender getEventSender() {
        return eventSender;
    }

    /**
     * Gets the telemetry sender.
     *
     * @return The sender.
     */
    public TelemetrySender getTelemetrySender() {
        return telemetrySender;
    }

    /**
     * Gets the command response sender.
     *
     * @return The sender.
     */
    public CommandResponseSender getCommandResponseSender() {
        return commandResponseSender;
    }

    @Override
    public Future<Void> start() {
        return CompositeFuture.all(
                startServiceClient(eventSender, type.name() + " Event"),
                startServiceClient(telemetrySender, type.name() + " Telemetry"),
                startServiceClient(commandResponseSender, type.name() + " Command response"))
                .mapEmpty();
    }

    @Override
    public Future<Void> stop() {
        return CompositeFuture.all(
                eventSender.stop(),
                telemetrySender.stop(),
                commandResponseSender.stop())
                .mapEmpty();
    }

    private Future<Void> startServiceClient(final Lifecycle serviceClient, final String serviceName) {
        Objects.requireNonNull(serviceName);
        return serviceClient.start().map(c -> {
            log.info("{} client [{}] successfully connected", serviceName, serviceClient);
            return c;
        }).recover(t -> {
            log.warn("{} client [{}] failed to connect", serviceName, serviceClient, t);
            return Future.failedFuture(t);
        });
    }

    /**
     * Registers liveness checks for this client.
     *
     * @param handler The handler to register the checks with.
     */
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        registerLivenessChecks(eventSender, handler);
        registerLivenessChecks(telemetrySender, handler);
        registerLivenessChecks(commandResponseSender, handler);
    }

    /**
     * Registers readiness checks for this client.
     *
     * @param handler The handler to register the checks with.
     */
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        registerReadinessChecks(eventSender, handler);
        registerReadinessChecks(telemetrySender, handler);
        registerReadinessChecks(commandResponseSender, handler);
    }

    private void registerLivenessChecks(final Object sender, final HealthCheckHandler handler) {
        if (sender instanceof ServiceClient) {
            ((ServiceClient) sender).registerLivenessChecks(handler);
        }
    }

    private void registerReadinessChecks(final Object sender, final HealthCheckHandler handler) {
        if (sender instanceof ServiceClient) {
            ((ServiceClient) sender).registerReadinessChecks(handler);
        }
    }
}
