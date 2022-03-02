/**
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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

import java.util.Objects;
import java.util.UUID;

import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.amqp.connection.ConnectionLifecycle;
import org.eclipse.hono.client.amqp.connection.DisconnectListener;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.ReconnectListener;
import org.eclipse.hono.client.util.ServiceClient;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessagingClient;
import org.eclipse.hono.util.MessagingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;

/**
 * A base class for implementing Hono service clients.
 */
public abstract class AbstractServiceClient implements ConnectionLifecycle<HonoConnection>, MessagingClient, ServiceClient, Lifecycle {

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
     * Creates a new client.
     *
     * @param connection The connection to the Hono service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected AbstractServiceClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory) {

        this.connection = Objects.requireNonNull(connection);
        this.connection.addDisconnectListener(con -> onDisconnect());
        this.samplerFactory = Objects.requireNonNull(samplerFactory);
    }

    @Override
    public final MessagingType getMessagingType() {
        return MessagingType.amqp;
    }

    /**
     * Creates a new <em>OpenTracing</em> span for tracing the execution of a service invocation.
     * <p>
     * The returned span will already contain the following tags:
     * <ul>
     * <li>{@link Tags#COMPONENT} - set to <em>hono-client</em></li>
     * <li>{@link Tags#PEER_HOSTNAME} - set to {@link org.eclipse.hono.config.ClientConfigProperties#getHost()}</li>
     * <li>{@link Tags#PEER_PORT} - set to {@link org.eclipse.hono.config.ClientConfigProperties#getPort()}</li>
     * <li>{@link TracingHelper#TAG_PEER_CONTAINER} - set to {@link HonoConnection#getRemoteContainerId()}</li>
     * </ul>
     *
     * @param parent The existing span. If not {@code null} then the new span will have a
     *                     {@link References#CHILD_OF} reference to the existing span.
     * @param operationName The operation name that the span should be created for.
     * @return The new span.
     */
    protected final Span newChildSpan(final SpanContext parent, final String operationName) {

        return newSpan(parent, References.CHILD_OF, operationName);
    }

    /**
     * Creates a new <em>OpenTracing</em> span for tracing the execution of a service invocation.
     * <p>
     * The returned span will already contain the following tags:
     * <ul>
     * <li>{@link Tags#COMPONENT} - set to <em>hono-client</em></li>
     * <li>{@link Tags#PEER_HOSTNAME} - set to {@link org.eclipse.hono.config.ClientConfigProperties#getHost()}</li>
     * <li>{@link Tags#PEER_PORT} - set to {@link org.eclipse.hono.config.ClientConfigProperties#getPort()}</li>
     * <li>{@link TracingHelper#TAG_PEER_CONTAINER} - set to {@link HonoConnection#getRemoteContainerId()}</li>
     * </ul>
     *
     * @param parent The existing span. If not {@code null} then the new span will have a
     *                     {@link References#FOLLOWS_FROM} reference to the existing span.
     * @param operationName The operation name that the span should be created for.
     * @return The new span.
     */
    protected final Span newFollowingSpan(final SpanContext parent, final String operationName) {

        return newSpan(parent, References.FOLLOWS_FROM, operationName);
    }

    private Span newSpan(final SpanContext parent, final String referenceType, final String operationName) {

        return TracingHelper.buildSpan(connection.getTracer(), parent, operationName, referenceType)
                .ignoreActiveSpan()
                .withTag(Tags.COMPONENT.getKey(), "hono-client")
                .withTag(Tags.PEER_HOSTNAME.getKey(), connection.getConfig().getHost())
                .withTag(Tags.PEER_PORT.getKey(), connection.getConfig().getPort())
                .withTag(TracingHelper.TAG_PEER_CONTAINER.getKey(), connection.getRemoteContainerId())
                .start();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Simply delegates to {@link HonoConnection#addDisconnectListener(DisconnectListener)}.
     */
    @Override
    public final void addDisconnectListener(final DisconnectListener<HonoConnection> listener) {
        connection.addDisconnectListener(listener);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Simply delegates to {@link HonoConnection#addReconnectListener(ReconnectListener)}.
     */
    @Override
    public final void addReconnectListener(final ReconnectListener<HonoConnection> listener) {
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
                String.format("connection-to-%s-%s", connection.getConfig().getServerRole(), UUID.randomUUID()),
                status -> {
                    connection.isConnected()
                        .onSuccess(ok -> status.tryComplete(Status.OK()))
                        .onFailure(t -> status.tryComplete(Status.KO()));
                });
    }

    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        // no liveness checks to be added
    }

    /**
     * {@inheritDoc}
     *
     * @return The outcome of the connection's {@link HonoConnection#connect()} method.
     */
    @Override
    public Future<Void> start() {
        return connection.connect()
                .onSuccess(ok -> log.info("connection to {} endpoint has been established", connection.getConfig().getServerRole()))
                .onFailure(t -> log.warn("failed to establish connection to {} endpoint", connection.getConfig().getServerRole(), t))
                .mapEmpty();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Invokes the connection's {@link HonoConnection#shutdown(Handler)} method.
     */
    @Override
    public Future<Void> stop() {
        final Promise<Void> result = Promise.promise();
        connection.shutdown(result);
        return result.future()
            .onSuccess(ok -> log.info("connection to {} endpoint has been closed",
                    connection.getConfig().getServerRole()));
    }
}
