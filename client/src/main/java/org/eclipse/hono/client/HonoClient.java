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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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
     * Gets properties describing the status of the connection to the Hono server.
     * <p>
     * The returned map contains the following properties:
     * <ul>
     * <li><em>name</em> - The name being indicated as the <em>container-id</em> in the
     * client's AMQP <em>Open</em> frame.</li>
     * <li><em>connected</em> - A boolean indicating whether this client is currently connected
     * to the Hono server.</li>
     * <li><em>Hono server</em> - The host (either name or literal IP address) and port of the
     * server this client is configured to connect to.</li>
     * </ul>
     * 
     * @return The connection status properties.
     */
    public Map<String, Object> getConnectionStatus() {

        Map<String, Object> result = new HashMap<>();
        result.put("name", connectionFactory.getName());
        result.put("connected", isConnected());
        result.put("Hono server", String.format("%s:%d", connectionFactory.getHost(), connectionFactory.getPort()));
        result.put("#regClients", activeRegClients.size());
        result.put("senders", getSenderStatus());
        return result;
    }

    /**
     * Gets a list of all senders and their current status.
     * <p>
     * For each sender the following properties are contained:
     * <ol>
     * <li>address - the link target address</li>
     * <li>open - indicates whether the link is (still) open</li>
     * <li>credit - the link-credit available</li>
     * </ol>
     * 
     * @return The status information.
     */
    public JsonArray getSenderStatus() {

        JsonArray result = new JsonArray();
        for (Entry<String, MessageSender> senderEntry : activeSenders.entrySet()) {
            final MessageSender sender = senderEntry.getValue();
            JsonObject senderStatus = new JsonObject()
                .put("address", senderEntry.getKey())
                .put("open", sender.isOpen())
                .put("credit", sender.getCredit());
            result.add(senderStatus);
        }
        return result;
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
                    this::onRemoteClose,
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

    private void onRemoteClose(final AsyncResult<ProtonConnection> remoteClose) {
        if (remoteClose.failed()) {
            LOG.info("Hono server [{}:{}] closed connection with error condition: {}",
                    connectionFactory.getHost(), connectionFactory.getPort(), remoteClose.cause().getMessage());
        }
        connection.close();
        onRemoteDisconnect(connection);
    }

    private void onRemoteDisconnect(final ProtonConnection con) {

        LOG.warn("lost connection to Hono server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
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
            LOG.debug("reusing existing message sender for [{}]", key);
            resultHandler.handle(Future.succeededFuture(sender));
        } else {
            LOG.debug("creating new message sender for {}", key);
            final Future<MessageSender> internal = Future.future();
            internal.setHandler(creationAttempt -> {
                if (creationAttempt.succeeded()) {
                    MessageSender newSender = creationAttempt.result();
                    LOG.debug("successfully created new message sender for {}", key);
                    activeSenders.put(key, newSender);
                } else {
                    LOG.debug("failed to create new message sender for {}", key, creationAttempt.cause());
                }
                resultHandler.handle(creationAttempt);
            });
            newSenderSupplier.accept(internal.completer());
        }
    }

    private HonoClient createTelemetrySender(
            final String tenantId,
            final String deviceId,
            final Handler<AsyncResult<MessageSender>> creationHandler) {

        Future<MessageSender> senderTracker = Future.future();
        senderTracker.setHandler(creationHandler);
        checkConnection().compose(
                connected -> TelemetrySenderImpl.create(context, connection, tenantId, deviceId, senderTracker.completer()),
                senderTracker);
        return this;
    }

    public HonoClient createTelemetryConsumer(
            final String tenantId,
            final Consumer<Message> telemetryConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        Future<MessageConsumer> consumerTracker = Future.future();
        consumerTracker.setHandler(creationHandler);
        checkConnection().compose(
                connected -> TelemetryConsumerImpl.create(context, connection, tenantId, connectionFactory.getPathSeparator(), telemetryConsumer, consumerTracker.completer()),
                consumerTracker);
        return this;
    }

    public HonoClient createEventConsumer(
            final String tenantId,
            final Consumer<Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        Future<MessageConsumer> consumerTracker = Future.future();
        consumerTracker.setHandler(creationHandler);
        checkConnection().compose(
                connected -> EventConsumerImpl.create(context, connection, tenantId, connectionFactory.getPathSeparator(), eventConsumer, consumerTracker.completer()),
                consumerTracker);
        return this;
    }

    private HonoClient createEventSender(
            final String tenantId,
            final String deviceId,
            final Handler<AsyncResult<MessageSender>> creationHandler) {

        Future<MessageSender> senderTracker = Future.future();
        senderTracker.setHandler(creationHandler);
        checkConnection().compose(
                connected -> EventSenderImpl.create(context, connection, tenantId, deviceId, senderTracker.completer()),
                senderTracker);
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
        if (regClient != null && regClient.isOpen()) {
            LOG.debug("reusing existing registration client for [{}]", tenantId);
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
            LOG.debug("creating new registration client for [{}]", tenantId);
            RegistrationClientImpl.create(context, connection, tenantId, creationAttempt -> {
                if (creationAttempt.succeeded()) {
                    activeRegClients.put(tenantId, creationAttempt.result());
                    LOG.debug("successfully created registration client for [{}]", tenantId);
                    creationHandler.handle(Future.succeededFuture(creationAttempt.result()));
                } else {
                    LOG.debug("failed to create registration client for [{}]", tenantId, creationAttempt.cause());
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
