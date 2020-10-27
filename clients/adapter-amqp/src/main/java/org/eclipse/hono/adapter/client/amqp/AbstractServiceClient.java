/**
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import java.util.Objects;

import org.eclipse.hono.adapter.client.util.ServiceClient;
import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ReconnectListener;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;

/**
 * A base class for implementing Hono service clients.
 */
public abstract class AbstractServiceClient implements ConnectionLifecycle<HonoConnection>, ServiceClient, Lifecycle {

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());
    /**
     * The connection to use for interacting with Hono.
     */
    protected final HonoConnection connection;
    /**
     * The factory for creating <em>send message</em> samplers.
     */
    protected final SendMessageSampler.Factory samplerFactory;
    /**
     * The protocol adapter configuration.
     */
    protected final ProtocolAdapterProperties adapterConfig;

    /**
     * Creates a new client.
     *
     * @param connection The connection to the Hono service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected AbstractServiceClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {

        this.connection = Objects.requireNonNull(connection);
        this.connection.addDisconnectListener(con -> onDisconnect());
        this.samplerFactory = Objects.requireNonNull(samplerFactory);
        this.adapterConfig = Objects.requireNonNull(adapterConfig);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Simply delegates to {@link HonoConnection#addDisconnectListener(DisconnectListener)}.
     */
    @Override
    public void addDisconnectListener(final DisconnectListener<HonoConnection> listener) {
        connection.addDisconnectListener(listener);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Simply delegates to {@link HonoConnection#addReconnectListener(ReconnectListener)}.
     */
    @Override
    public void addReconnectListener(final ReconnectListener<HonoConnection> listener) {
        connection.addReconnectListener(listener);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Simply delegates to {@link HonoConnection#connect()}.
     */
    @Override
    public Future<HonoConnection> connect() {
        return connection.connect();
    }

    /**
     * Checks whether this client is connected to the service.
     * <p>
     * Simply delegates to {@link HonoConnection#isConnected()}.
     *
     * @return A succeeded future if this factory is connected.
     *         Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServerErrorException}.
     */
    @Override
    public final Future<Void> isConnected() {
        return connection.isConnected();
    }

    /**
     * Checks whether this client is connected to the service.
     * <p>
     * If a connection attempt is currently in progress, the returned future is completed
     * with the outcome of the connection attempt. If the connection attempt (including
     * potential reconnect attempts) isn't finished after the given timeout, the returned
     * future is failed.
     * <p>
     * Simply delegates to {@link HonoConnection#isConnected(long)}.
     *
     * @param waitForCurrentConnectAttemptTimeout The maximum number of milliseconds to wait for
     *                                            an ongoing connection attempt to finish.
     * @return A succeeded future if this factory is connected.
     *         Otherwise, the future will be failed with a {@link org.eclipse.hono.client.ServerErrorException}.
     */
    @Override
    public final Future<Void> isConnected(final long waitForCurrentConnectAttemptTimeout) {
        return connection.isConnected(waitForCurrentConnectAttemptTimeout);
    }

    /**
     * Gets the default timeout used when checking whether this client is connected to the service.
     * <p>
     * The value returned here is the {@link org.eclipse.hono.config.ClientConfigProperties#getLinkEstablishmentTimeout()}.
     *
     * @return The timeout value in milliseconds.
     */
    public final long getDefaultConnectionCheckTimeout() {
        return connection.getConfig().getLinkEstablishmentTimeout();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This default implementation simply delegates to {@link HonoConnection#disconnect()}.
     */
    @Override
    public void disconnect() {
        connection.disconnect();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This default implementation simply delegates to {@link HonoConnection#disconnect(Handler)}.
     */
    @Override
    public void disconnect(final Handler<AsyncResult<Void>> completionHandler) {
        connection.disconnect(completionHandler);
    }

    /**
     * Invoked when the underlying connection to the Hono server
     * is lost unexpectedly.
     * <p>
     * This default implementation does nothing.
     * Subclasses should override this method in order to clean
     * up any state that may have become stale with the loss
     * of the connection.
     */
    protected void onDisconnect() {
        // do nothing
    }


    /**
     * {@inheritDoc}
     * <p>
     * Registers a procedure for checking if this client's connection is established.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        // verify that client is connected
        readinessHandler.register(
                String.format("connection to %s", connection.getConfig().getServerRole()),
                status -> {
                    connection.isConnected()
                        .onSuccess(ok -> status.complete(Status.OK()))
                        .onFailure(t -> status.complete(Status.KO()));
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        // no liveness checks to be added
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> start() {
        return connection.connect().mapEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> stop() {
        final Promise<Void> result = Promise.promise();
        connection.disconnect(result);
        return result.future();
    }
}
