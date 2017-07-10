/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.client.impl;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.*;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;

/**
 * A helper class for creating Vert.x based clients for Hono's arbitrary APIs.
 */
public final class HonoClientImpl implements HonoClient {

    private static final Logger LOG = LoggerFactory.getLogger(HonoClientImpl.class);
    private final Map<String, MessageSender> activeSenders = new ConcurrentHashMap<>();
    private final Map<String, RegistrationClient> activeRegClients = new ConcurrentHashMap<>();
    private final Map<String, CredentialsClient> activeCredClients = new ConcurrentHashMap<>();
    private final Map<String, Boolean> senderCreationLocks = new ConcurrentHashMap<>();
    private final List<Handler<Void>> creationRequests = new ArrayList<>();
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
    public HonoClientImpl(final Vertx vertx, final ConnectionFactory connectionFactory) {
        if (vertx != null) {
            this.vertx = vertx;
        } else {
            this.vertx = Vertx.vertx();
        }
        this.connectionFactory = connectionFactory;
    }

    /**
     * Sets the connection to the Hono server.
     * <p>
     * This method is mostly useful to inject a (mock) connection when running tests.
     * 
     * @param connection The connection to use.
     */
    void setConnection(final ProtonConnection connection) {
        this.connection = connection;
    }

    /**
     * Sets the vertx context to run all interactions with the Hono server on.
     * <p>
     * This method is mostly useful to inject a (mock) context when running tests.
     * 
     * @param context The context to use.
     */
    void setContext(final Context context) {
        this.context = context;
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#isConnected()
     */
    @Override
    public boolean isConnected() {
        return connection != null && !connection.isDisconnected();
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#getConnectionStatus()
     */
    @Override
    public Map<String, Object> getConnectionStatus() {

        Map<String, Object> result = new HashMap<>();
        result.put("name", connectionFactory.getName());
        result.put("connected", isConnected());
        result.put("server", String.format("%s:%d", connectionFactory.getHost(), connectionFactory.getPort()));
        result.put("#regClients", activeRegClients.size());
        result.put("senders", getSenderStatus());
        return result;
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#getSenderStatus()
     */
    @Override
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

    @Override
    public HonoClient connect(final ProtonClientOptions options, final Handler<AsyncResult<HonoClient>> connectionHandler) {
        return connect(options, connectionHandler, null);
    }

    @Override
    public HonoClient connect(
            final ProtonClientOptions options,
            final Handler<AsyncResult<HonoClient>> connectionHandler,
            final Handler<ProtonConnection> disconnectHandler) {

        Objects.requireNonNull(connectionHandler);

        if (isConnected()) {
            LOG.debug("already connected to server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
            connectionHandler.handle(Future.succeededFuture(this));
        } else if (connecting.compareAndSet(false, true)) {

            setConnection(null);
            if (options == null) {
                clientOptions = new ProtonClientOptions();
            } else {
                clientOptions = options;
            }

            connectionFactory.connect(
                    clientOptions,
                    remoteClose -> onRemoteClose(remoteClose, disconnectHandler),
                    failedConnection -> onRemoteDisconnect(failedConnection, disconnectHandler),
                    conAttempt -> {
                        connecting.compareAndSet(true, false);
                        if (conAttempt.failed()) {
                            reconnect(connectionHandler, disconnectHandler);
                        } else {
                            setConnection(conAttempt.result());
                            setContext(Vertx.currentContext());
                            connectionHandler.handle(Future.succeededFuture(this));
                        }
                    });
        } else {
            LOG.debug("already trying to connect to server ...");
        }
        return this;
    }

    private void reconnect(final Handler<AsyncResult<HonoClient>> connectionHandler, final Handler<ProtonConnection> disconnectHandler) {

        if (clientOptions == null || clientOptions.getReconnectAttempts() == 0) {
            connectionHandler.handle(Future.failedFuture("failed to connect"));
        } else {
            LOG.debug("scheduling re-connect attempt ...");
            // give Vert.x some time to clean up NetClient
            vertx.setTimer(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, tid -> {
                LOG.info("attempting to re-connect to server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
                connect(clientOptions, connectionHandler, disconnectHandler);
            });
        }
    }

    private void onRemoteClose(final AsyncResult<ProtonConnection> remoteClose, final Handler<ProtonConnection> disconnectHandler) {
        if (remoteClose.failed()) {
            LOG.info("remote server [{}:{}] closed connection with error condition: {}",
                    connectionFactory.getHost(), connectionFactory.getPort(), remoteClose.cause().getMessage());
        }
        connection.close();
        onRemoteDisconnect(connection, disconnectHandler);
    }

    private void onRemoteDisconnect(final ProtonConnection con, final Handler<ProtonConnection> nextHandler) {

        if (con != connection) {
            LOG.warn("cannot handle failure of unknown connection");
        } else {
            LOG.info("lost connection to server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
            connection.disconnect();
            activeSenders.clear();
            activeRegClients.clear();
            failAllCreationRequests();
            connection = null;

            if (nextHandler != null) {
                nextHandler.handle(con);
            } else {
                reconnect(attempt -> {}, failedCon -> onRemoteDisconnect(failedCon, null));
            }
        }
    }

    private void failAllCreationRequests() {

        for (Iterator<Handler<Void>> iter = creationRequests.iterator(); iter.hasNext(); ) {
            iter.next().handle(null);
            iter.remove();
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#getOrCreateTelemetrySender(java.lang.String, io.vertx.core.Handler)
     */
    @Override
    public HonoClient getOrCreateTelemetrySender(final String tenantId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        return getOrCreateTelemetrySender(tenantId, null, resultHandler);
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#getOrCreateTelemetrySender(java.lang.String, java.lang.String, io.vertx.core.Handler)
     */
    @Override
    public HonoClient getOrCreateTelemetrySender(final String tenantId, final String deviceId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        Objects.requireNonNull(tenantId);
        getOrCreateSender(
                TelemetrySenderImpl.getTargetAddress(tenantId, deviceId),
                (creationResult) -> createTelemetrySender(tenantId, deviceId, creationResult),
                resultHandler);
        return this;
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#getOrCreateEventSender(java.lang.String, io.vertx.core.Handler)
     */
    @Override
    public HonoClient getOrCreateEventSender(final String tenantId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        return getOrCreateEventSender(tenantId, null, resultHandler);
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#getOrCreateEventSender(java.lang.String, java.lang.String, io.vertx.core.Handler)
     */
    @Override
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

    void getOrCreateSender(final String key, final Consumer<Handler<AsyncResult<MessageSender>>> newSenderSupplier,
            final Handler<AsyncResult<MessageSender>> resultHandler) {

        final MessageSender sender = activeSenders.get(key);
        if (sender != null && sender.isOpen()) {
            LOG.debug("reusing existing message sender [target: {}, credit: {}]", key, sender.getCredit());
            resultHandler.handle(Future.succeededFuture(sender));
        } else if (!senderCreationLocks.computeIfAbsent(key, k -> Boolean.FALSE)) {

            // register a handler to be notified if the underlying connection to the server fails
            // so that we can fail the result handler passed in
            final Handler<Void> connectionFailureHandler = connectionLost -> {
                // remove lock so that next attempt to open a sender doesn't fail
                senderCreationLocks.remove(key);
                resultHandler.handle(Future.failedFuture("connection to server lost"));
            };
            creationRequests.add(connectionFailureHandler);
            senderCreationLocks.put(key, Boolean.TRUE);
            LOG.debug("creating new message sender for {}", key);

            newSenderSupplier.accept(creationAttempt -> {
                if (creationAttempt.succeeded()) {
                    MessageSender newSender = creationAttempt.result();
                    LOG.debug("successfully created new message sender for {}", key);
                    activeSenders.put(key, newSender);
                } else {
                    LOG.debug("failed to create new message sender for {}", key, creationAttempt.cause());
                    activeSenders.remove(key);
                }
                senderCreationLocks.remove(key);
                creationRequests.remove(connectionFailureHandler);
                resultHandler.handle(creationAttempt);
            });

        } else {
            LOG.debug("already trying to create a message sender for {}", key);
            resultHandler.handle(Future.failedFuture("sender link not established yet"));
        }
    }

    private HonoClient createTelemetrySender(
            final String tenantId,
            final String deviceId,
            final Handler<AsyncResult<MessageSender>> creationHandler) {

        Future<MessageSender> senderTracker = Future.future();
        senderTracker.setHandler(creationHandler);
        checkConnection().compose(
                connected -> TelemetrySenderImpl.create(context, connection, tenantId, deviceId,
                        onSenderClosed -> {
                            activeSenders.remove(TelemetrySenderImpl.getTargetAddress(tenantId, deviceId));
                        },
                        senderTracker.completer()),
                senderTracker);
        return this;
    }


    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#createTelemetryConsumer(java.lang.String, java.util.function.Consumer, io.vertx.core.Handler)
     */
    @Override
    public HonoClient createTelemetryConsumer(
            final String tenantId,
            final Consumer<Message> telemetryConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {
        return createTelemetryConsumer(tenantId, AbstractHonoClient.DEFAULT_SENDER_CREDITS, telemetryConsumer,
                creationHandler);
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#createTelemetryConsumer(java.lang.String, int, java.util.function.Consumer, io.vertx.core.Handler)
     */
    @Override
    public HonoClient createTelemetryConsumer(
            final String tenantId,
            final int prefetch,
            final Consumer<Message> telemetryConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        // register a handler to be notified if the underlying connection to the server fails
        // so that we can fail the result handler passed in
        final Handler<Void> connectionFailureHandler = connectionLost -> {
            creationHandler.handle(Future.failedFuture("connection to server lost"));
        };
        creationRequests.add(connectionFailureHandler);

        Future<MessageConsumer> consumerTracker = Future.future();
        consumerTracker.setHandler(attempt -> {
            creationRequests.remove(connectionFailureHandler);
            creationHandler.handle(attempt);
        });
        checkConnection().compose(
                connected -> TelemetryConsumerImpl.create(context, connection, tenantId,
                        connectionFactory.getPathSeparator(), prefetch, telemetryConsumer, consumerTracker.completer()),
                consumerTracker);
        return this;
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#createEventConsumer(java.lang.String, int, java.util.function.Consumer, io.vertx.core.Handler)
     */
    @Override
    public HonoClient createEventConsumer(
            final String tenantId,
            final Consumer<Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        createEventConsumer(tenantId, AbstractHonoClient.DEFAULT_SENDER_CREDITS, (delivery, message) -> eventConsumer.accept(message), creationHandler);
        return this;
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#createEventConsumer(java.lang.String, int, java.util.function.Consumer, io.vertx.core.Handler)
     */
    @Override
    public HonoClient createEventConsumer(
            final String tenantId,
            final int prefetch,
            final Consumer<Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        createEventConsumer(tenantId, prefetch, (delivery, message) -> eventConsumer.accept(message), creationHandler);
        return this;
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#createEventConsumer(java.lang.String, java.util.function.BiConsumer, io.vertx.core.Handler)
     */
    @Override
    public HonoClient createEventConsumer(
            final String tenantId,
            final BiConsumer<ProtonDelivery, Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        createEventConsumer(tenantId, AbstractHonoClient.DEFAULT_SENDER_CREDITS, eventConsumer, creationHandler);
        return this;
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#createEventConsumer(java.lang.String, java.util.function.BiConsumer, io.vertx.core.Handler)
     */
    @Override
    public HonoClient createEventConsumer(
            final String tenantId,
            final int prefetch,
            final BiConsumer<ProtonDelivery, Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        // register a handler to be notified if the underlying connection to the server fails
        // so that we can fail the result handler passed in
        final Handler<Void> connectionFailureHandler = connectionLost -> {
            creationHandler.handle(Future.failedFuture("connection to server lost"));
        };
        creationRequests.add(connectionFailureHandler);

        Future<MessageConsumer> consumerTracker = Future.future();
        consumerTracker.setHandler(attempt -> {
            creationRequests.remove(connectionFailureHandler);
            creationHandler.handle(attempt);
        });
        checkConnection().compose(
                connected -> EventConsumerImpl.create(context, connection, tenantId,
                        connectionFactory.getPathSeparator(), prefetch, eventConsumer, consumerTracker.completer()),
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
                connected -> EventSenderImpl.create(context, connection, tenantId, deviceId,
                        onSenderClosed -> {
                            activeSenders.remove(EventSenderImpl.getTargetAddress(tenantId, deviceId));
                        },
                        senderTracker.completer()),
                senderTracker);
        return this;
    }

    private Future<ProtonConnection> checkConnection() {
        if (connection == null || connection.isDisconnected()) {
            return Future.failedFuture("client is not connected to server (yet)");
        } else {
            return Future.succeededFuture(connection);
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#getOrCreateRegistrationClient(java.lang.String, io.vertx.core.Handler)
     */
    @Override
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

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#createRegistrationClient(java.lang.String, io.vertx.core.Handler)
     */
    @Override
    public HonoClient createRegistrationClient(
            final String tenantId,
            final Handler<AsyncResult<RegistrationClient>> creationHandler) {

        Objects.requireNonNull(tenantId);
        if (connection == null || connection.isDisconnected()) {
            creationHandler.handle(Future.failedFuture("client is not connected to server (yet)"));
        } else {
            // register a handler to be notified if the underlying connection to the server fails
            // so that we can fail the result handler passed in
            final Handler<Void> connectionFailureHandler = connectionLost -> {
                creationHandler.handle(Future.failedFuture("connection to server lost"));
            };
            creationRequests.add(connectionFailureHandler);

            LOG.debug("creating new registration client for [{}]", tenantId);
            RegistrationClientImpl.create(context, connection, tenantId, creationAttempt -> {
                if (creationAttempt.succeeded()) {
                    activeRegClients.put(tenantId, creationAttempt.result());
                    LOG.debug("successfully created registration client for [{}]", tenantId);
                } else {
                    LOG.debug("failed to create registration client for [{}]", tenantId, creationAttempt.cause());
                }
                creationRequests.remove(connectionFailureHandler);
                creationHandler.handle(creationAttempt);
            });
        }
        return this;
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#getOrCreateCredentialsClient(java.lang.String, io.vertx.core.Handler)
     */
    @Override
    public HonoClient getOrCreateCredentialsClient(final String tenantId, final Handler<AsyncResult<CredentialsClient>> resultHandler) {
        final CredentialsClient credClient = activeCredClients.get(tenantId);
        if (credClient != null && credClient.isOpen()) {
            LOG.debug("reusing existing credentials client for [{}]", tenantId);
            resultHandler.handle(Future.succeededFuture(credClient));
        } else {
            createCredentialsClient(tenantId, resultHandler);
        }
        return this;
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#createCredentialsClient(java.lang.String, io.vertx.core.Handler)
     */
    @Override
    public HonoClient createCredentialsClient(final String tenantId, final Handler<AsyncResult<CredentialsClient>> creationHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(creationHandler);
        if (connection == null || connection.isDisconnected()) {
            creationHandler.handle(Future.failedFuture("client is not connected to server (yet)"));
        } else {
            // register a handler to be notified if the underlying connection to the server fails
            // so that we can fail the result handler passed in
            final Handler<Void> connectionFailureHandler = connectionLost -> {
                creationHandler.handle(Future.failedFuture("connection to server lost"));
            };
            creationRequests.add(connectionFailureHandler);

            LOG.debug("creating new credentials client for [{}]", tenantId);
            CredentialsClientImpl.create(context, connection, tenantId, creationAttempt -> {
                if (creationAttempt.succeeded()) {
                    activeCredClients.put(tenantId, creationAttempt.result());
                    LOG.debug("successfully created credentials client for [{}]", tenantId);
                } else {
                    LOG.debug("failed to create credentials client for [{}]", tenantId, creationAttempt.cause());
                }
                creationRequests.remove(connectionFailureHandler);
                creationHandler.handle(creationAttempt);
            });
        }
        return this;
    }

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#shutdown()
     */
    @Override
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

    /* (non-Javadoc)
     * @see org.eclipse.hono.client.HonoClient#shutdown(io.vertx.core.Handler)
     */
    @Override
    public void shutdown(final Handler<AsyncResult<Void>> completionHandler) {

        if (connection == null || connection.isDisconnected()) {
            LOG.info("connection to server [{}:{}] already closed", connectionFactory.getHost(), connectionFactory.getPort());
            completionHandler.handle(Future.succeededFuture());
        } else {
            context.runOnContext(close -> {
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
            });
        }
    }
}
