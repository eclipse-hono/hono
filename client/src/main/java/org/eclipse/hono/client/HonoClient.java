/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.client;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.impl.EventConsumerImpl;
import org.eclipse.hono.client.impl.EventSenderImpl;
import org.eclipse.hono.client.impl.RegistrationClientImpl;
import org.eclipse.hono.client.impl.TelemetryConsumerImpl;
import org.eclipse.hono.client.impl.TelemetrySenderImpl;
import org.eclipse.hono.connection.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * A helper class for creating Vert.x based clients for Hono's arbitrary APIs.
 */
public final class HonoClient {

    private static final Logger LOG = LoggerFactory.getLogger(HonoClient.class);
    private final Map<String, MessageSender> activeSenders = new ConcurrentHashMap<>();
    private final Map<String, RegistrationClient> activeRegClients = new ConcurrentHashMap<>();
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private ProtonClientOptions clientOptions;
    private ProtonConnection connection;
    private Vertx vertx;
    private Context context;
    private ConnectionFactory connectionFactory;


    /**
     * Creates a new client for a set of configuration properties.
     * 
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param connectionFactory The factory to use for creating an AMQP connection to the Hono server.
     */
    public HonoClient(final Vertx vertx, final ConnectionFactory connectionFactory) {
        if (vertx != null) {
            this.vertx = vertx;
        } else {
            this.vertx = Vertx.vertx();
        }
        this.connectionFactory = connectionFactory;
    }

    /**
     * Checks whether this client is connected to the Hono server.
     * 
     * @return {@code true} if this client is connected.
     */
    public boolean isConnected() {
        return connection != null && !connection.isDisconnected();
    }

    /**
     * Connects to the Hono server using given options.
     * 
     * @param options The options to use (may be {@code null}).
     * @param connectionHandler The handler to notify about the outcome of the connection attempt.
     * @return This client for command chaining.
     * @throws NullPointerException if the connection handler is {@code null}.
     */
    public HonoClient connect(final ProtonClientOptions options, final Handler<AsyncResult<HonoClient>> connectionHandler) {
        return connect(options, connectionHandler, null);
    }

    /**
     * Connects to the Hono server using given options.
     * 
     * @param options The options to use (may be {@code null}).
     * @param connectionHandler The handler to notify about the outcome of the connection attempt.
     * @param disconnectHandler A  handler to notify about connection loss (may be {@code null}).
     * @return This client for command chaining.
     * @throws NullPointerException if the connection handler is {@code null}.
     */
    public HonoClient connect(
            final ProtonClientOptions options,
            final Handler<AsyncResult<HonoClient>> connectionHandler,
            final Handler<ProtonConnection> disconnectHandler) {

        Objects.requireNonNull(connectionHandler);

        if (isConnected()) {
            LOG.debug("already connected to server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
            connectionHandler.handle(Future.succeededFuture(this));
        } else if (connecting.compareAndSet(false, true)) {

            connection = null;
            if (options == null) {
                clientOptions = new ProtonClientOptions();
            } else {
                clientOptions = options;
            }

            connectionFactory.connect(
                    clientOptions,
                    null, // no particular close handler
                    disconnectHandler != null ? disconnectHandler : this::onRemoteDisconnect,
                    conAttempt -> {
                        connecting.compareAndSet(true, false);
                        if (conAttempt.failed()) {
                            connectionHandler.handle(Future.failedFuture(conAttempt.cause()));
                        } else {
                            connection = conAttempt.result();
                            context = Vertx.currentContext();
                            connectionHandler.handle(Future.succeededFuture(this));
                        }
                    });
        } else {
            LOG.debug("already trying to connect to Hono server ...");
        }
        return this;
    }

    private void onRemoteDisconnect(final ProtonConnection con) {

        LOG.warn("lost connection to Hono server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
        con.disconnectHandler(null);
        con.disconnect();
        activeSenders.clear();
        activeRegClients.clear();
        if (clientOptions.getReconnectAttempts() != 0) {
            // give Vert.x some time to clean up NetClient
            vertx.setTimer(300, reconnect -> {
                LOG.info("attempting to re-connect to Hono server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
                connect(clientOptions, done -> {});
            });
        }
    }

    /**
     * Gets a client for sending telemetry messages to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send messages for.
     * @param resultHandler The handler to notify about the client.
     * @return This for command chaining.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public HonoClient getOrCreateTelemetrySender(final String tenantId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        return getOrCreateTelemetrySender(tenantId, null, resultHandler);
    }

    /**
     * Gets a client for sending telemetry messages to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send messages for.
     * @param deviceId The ID of the device to send events for (may be {@code null}).
     * @param resultHandler The handler to notify about the client.
     * @return This for command chaining.
     * @throws NullPointerException if any of the tenantId or resultHandler is {@code null}.
     */
    public HonoClient getOrCreateTelemetrySender(final String tenantId, final String deviceId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        Objects.requireNonNull(tenantId);
        getOrCreateSender(
                TelemetrySenderImpl.getTargetAddress(tenantId, deviceId),
                (creationResult) -> createTelemetrySender(tenantId, deviceId, creationResult),
                resultHandler);
        return this;
    }

    /**
     * Gets a client for sending events to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send events for.
     * @param resultHandler The handler to notify about the client.
     * @return This for command chaining.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public HonoClient getOrCreateEventSender(final String tenantId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        return getOrCreateEventSender(tenantId, null, resultHandler);
    }

    /**
     * Gets a client for sending events to a Hono server.
     * 
     * @param tenantId The ID of the tenant to send events for.
     * @param deviceId The ID of the device to send events for (may be {@code null}).
     * @param resultHandler The handler to notify about the client.
     * @return This for command chaining.
     * @throws NullPointerException if any of the tenantId or resultHandler is {@code null}.
     */
    public HonoClient getOrCreateEventSender(
            final String tenantId,
            final String deviceId,
            final Handler<AsyncResult<MessageSender>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);
        getOrCreateSender(
                EventSenderImpl.getTargetAddress(tenantId, deviceId),
                (creationResult) -> createEventSender(tenantId, deviceId, creationResult),
                resultHandler);
        return this;
    }

    private void getOrCreateSender(final String key, final Consumer<Handler> newSenderSupplier,
            final Handler<AsyncResult<MessageSender>> resultHandler) {

        final MessageSender sender = activeSenders.get(key);
        if (sender != null && sender.isOpen()) {
            resultHandler.handle(Future.succeededFuture(sender));
        } else {
            final Future<MessageSender> internal = Future.future();
            internal.setHandler(result -> {
                if (result.succeeded()) {
                    activeSenders.put(key, result.result());
                }
                resultHandler.handle(result);
            });
            newSenderSupplier.accept(internal.completer());
        }
    }

    private HonoClient createTelemetrySender(
            final String tenantId,
            final String deviceId,
            final Handler<AsyncResult<MessageSender>> creationHandler) {

        checkConnection().compose(
                connected -> TelemetrySenderImpl.create(context, connection, tenantId, deviceId, creationHandler),
                Future.<MessageSender> future().setHandler(creationHandler));
        return this;
    }

    public HonoClient createTelemetryConsumer(
            final String tenantId,
            final Consumer<Message> telemetryConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        checkConnection().compose(
                connected -> TelemetryConsumerImpl.create(context, connection, tenantId, connectionFactory.getPathSeparator(), telemetryConsumer, creationHandler),
                Future.<MessageConsumer> future().setHandler(creationHandler));
        return this;
    }

    public HonoClient createEventConsumer(
            final String tenantId,
            final Consumer<Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        checkConnection().compose(
                connected -> EventConsumerImpl.create(context, connection, tenantId, connectionFactory.getPathSeparator(), eventConsumer, creationHandler),
                Future.<MessageConsumer> future().setHandler(creationHandler));
        return this;
    }

    private HonoClient createEventSender(
            final String tenantId,
            final String deviceId,
            final Handler<AsyncResult<MessageSender>> creationHandler) {

        checkConnection().compose(
                connected -> EventSenderImpl.create(context, connection, tenantId, deviceId, creationHandler),
                Future.<MessageSender> future().setHandler(creationHandler));
        return this;
    }

    private <T> Future<T> checkConnection() {
        if (connection == null || connection.isDisconnected()) {
            return Future.failedFuture("client is not connected to Hono (yet)");
        } else {
            return Future.succeededFuture();
        }
    }

    public HonoClient getOrCreateRegistrationClient(
            final String tenantId,
            final Handler<AsyncResult<RegistrationClient>> resultHandler) {

        final RegistrationClient regClient = activeRegClients.get(Objects.requireNonNull(tenantId));
        if (regClient != null) {
            resultHandler.handle(Future.succeededFuture(regClient));
        } else {
            createRegistrationClient(tenantId, resultHandler);
        }
        return this;
    }

    public HonoClient createRegistrationClient(
            final String tenantId,
            final Handler<AsyncResult<RegistrationClient>> creationHandler) {

        Objects.requireNonNull(tenantId);
        if (connection == null || connection.isDisconnected()) {
            creationHandler.handle(Future.failedFuture("client is not connected to Hono (yet)"));
        } else {
            RegistrationClientImpl.create(context, connection, tenantId, creationAttempt -> {
                if (creationAttempt.succeeded()) {
                    activeRegClients.put(tenantId, creationAttempt.result());
                    creationHandler.handle(Future.succeededFuture(creationAttempt.result()));
                } else {
                    creationHandler.handle(Future.failedFuture(creationAttempt.cause()));
                }
            });
        }
        return this;
    }

    /**
     * Closes this client's connection to the Hono server.
     * <p>
     * This method waits for at most 5 seconds for the connection to be closed properly.
     */
    public void shutdown() {
        final CountDownLatch latch = new CountDownLatch(1);
        shutdown(done -> {
            if (done.succeeded()) {
                latch.countDown();
            } else {
                LOG.error("could not close connection to server", done.cause());
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                LOG.error("shutdown of client timed out");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown(final Handler<AsyncResult<Void>> completionHandler) {
        if (connection == null || connection.isDisconnected()) {
            LOG.info("connection to server [{}:{}] already closed", connectionFactory.getHost(), connectionFactory.getPort());
            completionHandler.handle(Future.succeededFuture());
        } else {
            LOG.info("closing connection to server [{}:{}]...", connectionFactory.getHost(), connectionFactory.getPort());
            connection.disconnectHandler(null); // make sure we are not trying to re-connect
            connection.closeHandler(closedCon -> {
                if (closedCon.succeeded()) {
                    LOG.info("closed connection to server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
                } else {
                    LOG.info("could not close connection to server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort(), closedCon.cause());
                }
                connection.disconnect();
                if (completionHandler != null) {
                    completionHandler.handle(Future.succeededFuture());
                }
            }).close();
        }
    }
}
