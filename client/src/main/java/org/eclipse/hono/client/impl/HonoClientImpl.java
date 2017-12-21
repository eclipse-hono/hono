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

import java.net.HttpURLConnection;
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
import org.eclipse.hono.config.ClientConfigProperties;
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
    private final Map<String, RequestResponseClient> activeRequestResponseClients = new ConcurrentHashMap<>();
    private final Map<String, Boolean> creationLocks = new ConcurrentHashMap<>();
    private final List<Handler<Void>> creationRequests = new ArrayList<>();
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final ConnectionFactory connectionFactory;
    private final ClientConfigProperties clientConfigProperties;
    private final Vertx vertx;
    private volatile boolean shutdown = false;
    private ProtonClientOptions clientOptions;
    private ProtonConnection connection;
    private Context context;

    /**
     * Creates a new client for a set of configuration properties.
     *
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param connectionFactory The factory to use for creating an AMQP connection to the Hono server.
     * @param clientConfigProperties The config properties to use (beside the connection properties)
     */
    public HonoClientImpl(final Vertx vertx, final ConnectionFactory connectionFactory, final ClientConfigProperties clientConfigProperties) {
        if (vertx != null) {
            this.vertx = vertx;
        } else {
            this.vertx = Vertx.vertx();
        }
        this.connectionFactory = connectionFactory;
        this.clientConfigProperties = clientConfigProperties;
    }

    /**
     * Creates a new client for a set of configuration properties.
     *
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param connectionFactory The factory to use for creating an AMQP connection to the Hono server.
     */
    public HonoClientImpl(final Vertx vertx, final ConnectionFactory connectionFactory) {
        this(vertx, connectionFactory, new ClientConfigProperties());
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

    @Override
    public boolean isConnected() {
        return connection != null && !connection.isDisconnected();
    }

    @Override
    public Map<String, Object> getConnectionStatus() {

        Map<String, Object> result = new HashMap<>();
        result.put("name", connectionFactory.getName());
        result.put("connected", isConnected());
        result.put("server", String.format("%s:%d", connectionFactory.getHost(), connectionFactory.getPort()));
        result.put("#clients", activeRequestResponseClients.size());
        result.put("senders", getSenderStatus());
        return result;
    }

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

        if (shutdown) {
            connectionHandler.handle(Future.failedFuture(new IllegalStateException("client was already shutdown")));
        } else if (isConnected()) {
            LOG.debug("already connected to server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
            connectionHandler.handle(Future.succeededFuture(this));
        } else if (connecting.compareAndSet(false, true)) {

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
                            if (shutdown) {
                                // if client was already shutdown in the meantime we give our best to cleanup connection
                                shutdownConnection(result -> {});
                                connectionHandler.handle(Future.failedFuture(new IllegalStateException("client was already shutdown")));
                            } else {
                                connectionHandler.handle(Future.succeededFuture(this));
                            }
                        }
                    });
        } else {
            LOG.debug("already trying to connect to server ...");
        }
        return this;
    }

    private void reconnect(final Handler<AsyncResult<HonoClient>> connectionHandler, final Handler<ProtonConnection> disconnectHandler) {

        if (clientOptions == null || clientOptions.getReconnectAttempts() == 0) {
            connectionHandler.handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "failed to connect")));
        } else {
            LOG.trace("scheduling re-connect attempt ...");
            // give Vert.x some time to clean up NetClient
            vertx.setTimer(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, tid -> {
                LOG.debug("attempting to re-connect to server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
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
            LOG.debug("lost connection to server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
            connection.disconnect();
            activeSenders.clear();
            activeRequestResponseClients.clear();
            failAllCreationRequests();

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

    @Override
    public HonoClient getOrCreateTelemetrySender(final String tenantId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        return getOrCreateTelemetrySender(tenantId, null, resultHandler);
    }

    @Override
    public HonoClient getOrCreateTelemetrySender(final String tenantId, final String deviceId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        Objects.requireNonNull(tenantId);
        getOrCreateSender(
                TelemetrySenderImpl.getTargetAddress(tenantId, deviceId),
                (creationResult) -> createTelemetrySender(tenantId, deviceId, clientConfigProperties.getFlowLatency(), creationResult),
                resultHandler);
        return this;
    }

    @Override
    public HonoClient getOrCreateEventSender(final String tenantId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        return getOrCreateEventSender(tenantId, null, resultHandler);
    }

    @Override
    public HonoClient getOrCreateEventSender(
            final String tenantId,
            final String deviceId,
            final Handler<AsyncResult<MessageSender>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);
        getOrCreateSender(
                EventSenderImpl.getTargetAddress(tenantId, deviceId),
                (creationResult) -> createEventSender(tenantId, deviceId, clientConfigProperties.getFlowLatency(), creationResult),
                resultHandler);
        return this;
    }

    void getOrCreateSender(final String key, final Consumer<Handler<AsyncResult<MessageSender>>> newSenderSupplier,
            final Handler<AsyncResult<MessageSender>> resultHandler) {

        final MessageSender sender = activeSenders.get(key);
        if (sender != null && sender.isOpen()) {
            LOG.debug("reusing existing message sender [target: {}, credit: {}]", key, sender.getCredit());
            resultHandler.handle(Future.succeededFuture(sender));
        } else if (!creationLocks.computeIfAbsent(key, k -> Boolean.FALSE)) {

            // register a handler to be notified if the underlying connection to the server fails
            // so that we can fail the result handler passed in
            final Handler<Void> connectionFailureHandler = connectionLost -> {
                // remove lock so that next attempt to open a sender doesn't fail
                creationLocks.remove(key);
                resultHandler.handle(Future.failedFuture(
                        new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "connection to server lost")));
            };
            creationRequests.add(connectionFailureHandler);
            creationLocks.put(key, Boolean.TRUE);
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
                creationLocks.remove(key);
                creationRequests.remove(connectionFailureHandler);
                resultHandler.handle(creationAttempt);
            });

        } else {
            LOG.debug("already trying to create a message sender for {}", key);
            resultHandler.handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
        }
    }

    private HonoClient createTelemetrySender(
            final String tenantId,
            final String deviceId,
            final long waitForInitialCredits,
            final Handler<AsyncResult<MessageSender>> creationHandler) {

        Future<MessageSender> senderTracker = Future.future();
        senderTracker.setHandler(creationHandler);
        checkConnection().compose(
                connected -> TelemetrySenderImpl.create(context, connection, tenantId, deviceId, waitForInitialCredits,
                        onSenderClosed -> {
                            activeSenders.remove(TelemetrySenderImpl.getTargetAddress(tenantId, deviceId));
                        },
                        senderTracker.completer()),
                senderTracker);
        return this;
    }

    @Override
    public HonoClient createTelemetryConsumer(
            final String tenantId,
            final Consumer<Message> telemetryConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {
        return createTelemetryConsumer(tenantId, clientConfigProperties.getInitialCredits(), telemetryConsumer,
                creationHandler);
    }

    @Override
    public HonoClient createTelemetryConsumer(
            final String tenantId,
            final int prefetch,
            final Consumer<Message> telemetryConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        // register a handler to be notified if the underlying connection to the server fails
        // so that we can fail the result handler passed in
        final Handler<Void> connectionFailureHandler = connectionLost -> {
            creationHandler.handle(Future.failedFuture(
                    new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "connection to server lost")));
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

    @Override
    public HonoClient createEventConsumer(
            final String tenantId,
            final Consumer<Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        createEventConsumer(tenantId, clientConfigProperties.getInitialCredits(), (delivery, message) -> eventConsumer.accept(message), creationHandler);
        return this;
    }

    @Override
    public HonoClient createEventConsumer(
            final String tenantId,
            final int prefetch,
            final Consumer<Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        createEventConsumer(tenantId, prefetch, (delivery, message) -> eventConsumer.accept(message), creationHandler);
        return this;
    }

    @Override
    public HonoClient createEventConsumer(
            final String tenantId,
            final BiConsumer<ProtonDelivery, Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        createEventConsumer(tenantId, clientConfigProperties.getInitialCredits(), eventConsumer, creationHandler);
        return this;
    }

    @Override
    public HonoClient createEventConsumer(
            final String tenantId,
            final int prefetch,
            final BiConsumer<ProtonDelivery, Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        // register a handler to be notified if the underlying connection to the server fails
        // so that we can fail the result handler passed in
        final Handler<Void> connectionFailureHandler = connectionLost -> {
            creationHandler.handle(Future.failedFuture(
                    new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "connection to server lost")));
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
            final long waitForInitialCredits,
            final Handler<AsyncResult<MessageSender>> creationHandler) {

        Future<MessageSender> senderTracker = Future.future();
        senderTracker.setHandler(creationHandler);
        checkConnection().compose(
                connected -> EventSenderImpl.create(context, connection, tenantId, deviceId, waitForInitialCredits,
                        onSenderClosed -> {
                            activeSenders.remove(EventSenderImpl.getTargetAddress(tenantId, deviceId));
                        },
                        senderTracker.completer()),
                senderTracker);
        return this;
    }

    private Future<ProtonConnection> checkConnection() {
        if (connection == null || connection.isDisconnected()) {
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "client is not connected to server (yet)"));
        } else {
            return Future.succeededFuture(connection);
        }
    }

    /**
     * Gets an existing or creates a new request-response client for a particular service.
     * 
     * @param key The key to look-up the client by.
     * @param clientSupplier A consumer for an attempt to create a new client.
     * @param resultHandler The handler to inform about the outcome of the operation.
     */
    void getOrCreateRequestResponseClient(
            final String key, 
            final Consumer<Handler<AsyncResult<RequestResponseClient>>> clientSupplier,
            final Handler<AsyncResult<RequestResponseClient>> resultHandler) {

        final RequestResponseClient client = activeRequestResponseClients.get(key);
        if (client != null && client.isOpen()) {
            LOG.debug("reusing existing client [target: {}]", key);
            resultHandler.handle(Future.succeededFuture(client));
        } else if (!creationLocks.computeIfAbsent(key, k -> Boolean.FALSE)) {

            // register a handler to be notified if the underlying connection to the server fails
            // so that we can fail the result handler passed in
            final Handler<Void> connectionFailureHandler = connectionLost -> {
                // remove lock so that next attempt to open a sender doesn't fail
                creationLocks.remove(key);
                resultHandler.handle(Future.failedFuture(
                        new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no connection to service")));
            };
            creationRequests.add(connectionFailureHandler);
            creationLocks.put(key, Boolean.TRUE);
            LOG.debug("creating new client for {}", key);

            clientSupplier.accept(creationAttempt -> {
                if (creationAttempt.succeeded()) {
                    RequestResponseClient newClient = creationAttempt.result();
                    LOG.debug("successfully created new client for {}", key);
                    activeRequestResponseClients.put(key, newClient);
                } else {
                    LOG.debug("failed to create new client for {}", key, creationAttempt.cause());
                    activeRequestResponseClients.remove(key);
                }
                creationLocks.remove(key);
                creationRequests.remove(connectionFailureHandler);
                resultHandler.handle(creationAttempt);
            });

        } else {
            LOG.debug("already trying to create a client for {}", key);
            resultHandler.handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
        }
    }

    @Override
    public HonoClient getOrCreateRegistrationClient(
            final String tenantId,
            final Handler<AsyncResult<RegistrationClient>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);
        getOrCreateRequestResponseClient(
                RegistrationClientImpl.getTargetAddress(tenantId),
                (creationResult) -> createRegistrationClient(tenantId, creationResult),
                attempt -> {
                    if (attempt.succeeded()) {
                        resultHandler.handle(Future.succeededFuture((RegistrationClient) attempt.result()));
                    } else {
                        resultHandler.handle(Future.failedFuture(attempt.cause()));
                    }
                });
        return this;
    }

    private void createRegistrationClient(
            final String tenantId,
            final Handler<AsyncResult<RequestResponseClient>> creationHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(creationHandler);

        Future<RequestResponseClient> clientTracker = Future.future();
        clientTracker.setHandler(creationHandler);

        checkConnection().compose(connected -> {

            RegistrationClientImpl.create(
                    context,
                    connection,
                    tenantId,
                    clientConfigProperties.getInitialCredits(),
                    clientConfigProperties.getFlowLatency(),
                    this::removeRegistrationClient,
                    this::removeRegistrationClient,
                    creationAttempt -> {
                        if (creationAttempt.succeeded()) {
                            RegistrationClient registrationClient = creationAttempt.result();
                            registrationClient.setRequestTimeout(clientConfigProperties.getRequestTimeout());
                            clientTracker.complete(registrationClient);
                        } else {
                            clientTracker.fail(creationAttempt.cause());
                        }
                    });
        }, clientTracker);
    }

    @Override
    public HonoClient getOrCreateCredentialsClient(
            final String tenantId,
            final Handler<AsyncResult<CredentialsClient>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);
        getOrCreateRequestResponseClient(
                CredentialsClientImpl.getTargetAddress(tenantId),
                (creationResult) -> createCredentialsClient(tenantId, creationResult),
                attempt -> {
                    if (attempt.succeeded()) {
                        resultHandler.handle(Future.succeededFuture((CredentialsClient) attempt.result()));
                    } else {
                        resultHandler.handle(Future.failedFuture(attempt.cause()));
                    }
                });
        return this;

    }

    private void createCredentialsClient(
            final String tenantId,
            final Handler<AsyncResult<RequestResponseClient>> creationHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(creationHandler);

        Future<RequestResponseClient> clientTracker = Future.future();
        clientTracker.setHandler(creationHandler);

        checkConnection().compose(connected -> {

            CredentialsClientImpl.create(
                    context,
                    connection,
                    tenantId,
                    clientConfigProperties.getInitialCredits(),
                    clientConfigProperties.getFlowLatency(),
                    this::removeCredentialsClient,
                    this::removeCredentialsClient,
                    creationAttempt -> {
                        if (creationAttempt.succeeded()) {
                            CredentialsClient credentialsClient = creationAttempt.result();
                            credentialsClient.setRequestTimeout(clientConfigProperties.getRequestTimeout());
                            clientTracker.complete(credentialsClient);
                        } else {
                            clientTracker.fail(creationAttempt.cause());
                        }
                    });
        }, clientTracker);
    }

    private void removeCredentialsClient(final String tenantId) {
        String key = CredentialsClientImpl.getTargetAddress(tenantId);
        RequestResponseClient client = activeRequestResponseClients.remove(key);
        if (client != null) {
            client.close(s -> {});
            LOG.debug("closed and removed credentials client for [{}]", tenantId);
        }
    }

    private void removeRegistrationClient(final String tenantId) {
        String key = RegistrationClientImpl.getTargetAddress(tenantId);
        RequestResponseClient client = activeRequestResponseClients.remove(key);
        if (client != null) {
            client.close(s -> {});
            LOG.debug("closed and removed registration client for [{}]", tenantId);
        }
    }

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

    @Override
    public void shutdown(final Handler<AsyncResult<Void>> completionHandler) {
        shutdown = true;
        if (connection == null || connection.isDisconnected()) {
            LOG.info("connection to server [{}:{}] already closed", connectionFactory.getHost(), connectionFactory.getPort());
            completionHandler.handle(Future.succeededFuture());
        } else {
            shutdownConnection(completionHandler);
        }
    }

    private void shutdownConnection(final Handler<AsyncResult<Void>> completionHandler) {
        context.runOnContext(close -> {
            LOG.info("closing connection to server [{}:{}]...", connectionFactory.getHost(), connectionFactory.getPort());
            connection.disconnectHandler(null); // make sure we are not trying to re-connect
            connection.closeHandler(closedCon -> {
                if (closedCon.succeeded()) {
                    LOG.info("closed connection to server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
                } else {
                    LOG.info("closed connection to server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort(), closedCon.cause());
                }
                connection.disconnect();
                if (completionHandler != null) {
                    completionHandler.handle(Future.succeededFuture());
                }
            }).close();
        });
    }
}
