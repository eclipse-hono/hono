/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */
package org.eclipse.hono.client.impl;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RequestResponseClient;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
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
    private CacheManager cacheManager;

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
     * Sets a manager for creating cache instances to be used in Hono clients.
     * 
     * @param manager The cache manager.
     * @throws NullPointerException if manager is {@code null}.
     */
    public void setCacheManager(final CacheManager manager) {
        this.cacheManager = Objects.requireNonNull(manager);
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
    public void connect(final ProtonClientOptions options, final Handler<AsyncResult<HonoClient>> connectionHandler) {
        connect(options, connectionHandler, null);
    }

    @Override
    public void connect(
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

    private void onRemoteClose(final AsyncResult<ProtonConnection> remoteClose, final Handler<ProtonConnection> connectionLossHandler) {

        if (remoteClose.failed()) {
            LOG.info("remote server [{}:{}] closed connection with error condition: {}",
                    connectionFactory.getHost(), connectionFactory.getPort(), remoteClose.cause().getMessage());
        } else {
            LOG.info("remote server [{}:{}] closed connection", connectionFactory.getHost(), connectionFactory.getPort());
        }
        connection.disconnectHandler(null);
        connection.close();
        handleConnectionLoss(connectionLossHandler);
    }

    private void onRemoteDisconnect(final ProtonConnection con, final Handler<ProtonConnection> connectionLossHandler) {

        if (con != connection) {
            LOG.warn("cannot handle failure of unknown connection");
        } else {
            LOG.debug("lost connection to server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
            handleConnectionLoss(connectionLossHandler);
        }
    }

    private void handleConnectionLoss(final Handler<ProtonConnection> connectionLossHandler) {
        if (connection != null && !connection.isDisconnected()) {
            connection.disconnect();
        }

        final ProtonConnection failedConnection = this.connection;
        this.connection = null;

        activeSenders.clear();
        activeRequestResponseClients.clear();
        failAllCreationRequests();

        if (connectionLossHandler != null) {
            connectionLossHandler.handle(failedConnection);
        } else {
            reconnect(attempt -> {}, failedCon -> onRemoteDisconnect(failedCon, null));
        }
    }

    private void failAllCreationRequests() {

        for (Iterator<Handler<Void>> iter = creationRequests.iterator(); iter.hasNext(); ) {
            iter.next().handle(null);
            iter.remove();
        }
    }

    @Override
    public void getOrCreateTelemetrySender(final String tenantId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        getOrCreateTelemetrySender(tenantId, null, resultHandler);
    }

    @Override
    public void getOrCreateTelemetrySender(final String tenantId, final String deviceId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        Objects.requireNonNull(tenantId);
        getOrCreateSender(
                TelemetrySenderImpl.getTargetAddress(tenantId, deviceId),
                () -> createTelemetrySender(tenantId, deviceId),
                resultHandler);
    }

    @Override
    public void getOrCreateEventSender(final String tenantId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        getOrCreateEventSender(tenantId, null, resultHandler);
    }

    @Override
    public void getOrCreateEventSender(
            final String tenantId,
            final String deviceId,
            final Handler<AsyncResult<MessageSender>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);
        getOrCreateSender(
                EventSenderImpl.getTargetAddress(tenantId, deviceId),
                () -> createEventSender(tenantId, deviceId),
                resultHandler);
    }

    void getOrCreateSender(
            final String key,
            final Supplier<Future<MessageSender>> newSenderSupplier,
            final Handler<AsyncResult<MessageSender>> resultHandler) {

        final MessageSender sender = activeSenders.get(key);
        if (sender != null && sender.isOpen()) {
            LOG.debug("reusing existing message sender [target: {}, credit: {}]", key, sender.getCredit());
            resultHandler.handle(Future.succeededFuture(sender));
        } else if (!isConnected()) {
            resultHandler.handle(Future.failedFuture(new ServerErrorException(
                    HttpURLConnection.HTTP_UNAVAILABLE, "no connection to service")));
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
            LOG.debug("creating new message sender for {}", key);

            newSenderSupplier.get().setHandler(creationAttempt -> {
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
            resultHandler.handle(Future.failedFuture(new ServerErrorException(
                    HttpURLConnection.HTTP_UNAVAILABLE, "no connection to service")));
        }
    }

    private Future<MessageSender> createTelemetrySender(
            final String tenantId,
            final String deviceId) {

        return checkConnection().compose(connected -> {
            final Future<MessageSender> result = Future.future();
            TelemetrySenderImpl.create(context, clientConfigProperties, connection, tenantId, deviceId,
                        onSenderClosed -> {
                            activeSenders.remove(TelemetrySenderImpl.getTargetAddress(tenantId, deviceId));
                        },
                        result.completer());
            return result;
        });
    }

    @Override
    public void createTelemetryConsumer(
            final String tenantId,
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
                connected -> TelemetryConsumerImpl.create(context, clientConfigProperties, connection, tenantId,
                        connectionFactory.getPathSeparator(), telemetryConsumer, consumerTracker.completer()),
                consumerTracker);
    }

    @Override
    public void createEventConsumer(
            final String tenantId,
            final Consumer<Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        createEventConsumer(tenantId, (delivery, message) -> eventConsumer.accept(message), creationHandler);
    }

    @Override
    public void createEventConsumer(
            final String tenantId,
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
                connected -> EventConsumerImpl.create(context, clientConfigProperties, connection, tenantId,
                        connectionFactory.getPathSeparator(), eventConsumer, consumerTracker.completer()),
                consumerTracker);
    }

    private Future<MessageSender> createEventSender(
            final String tenantId,
            final String deviceId) {

        return checkConnection().compose(connected -> {
            Future<MessageSender> result = Future.future();
            EventSenderImpl.create(context, clientConfigProperties, connection, tenantId, deviceId,
                        onSenderClosed -> {
                            activeSenders.remove(EventSenderImpl.getTargetAddress(tenantId, deviceId));
                        },
                        result.completer());
            return result;
        });
    }

    private Future<ProtonConnection> checkConnection() {

        if (isConnected()) {
            return Future.succeededFuture(connection);
        } else {
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "client is not connected to server (yet)"));
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
            final Supplier<Future<RequestResponseClient>> clientSupplier,
            final Handler<AsyncResult<RequestResponseClient>> resultHandler) {

        final RequestResponseClient client = activeRequestResponseClients.get(key);
        if (client != null && client.isOpen()) {
            LOG.debug("reusing existing client [target: {}]", key);
            resultHandler.handle(Future.succeededFuture(client));
        } else if (!isConnected()) {
            resultHandler.handle(Future.failedFuture(new ServerErrorException(
                    HttpURLConnection.HTTP_UNAVAILABLE, "no connection to service")));
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

            clientSupplier.get().setHandler(creationAttempt -> {
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
            resultHandler.handle(Future.failedFuture(new ServerErrorException(
                    HttpURLConnection.HTTP_UNAVAILABLE, "no connection to service")));
        }
    }

    @Override
    public void getOrCreateRegistrationClient(
            final String tenantId,
            final Handler<AsyncResult<RegistrationClient>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);
        getOrCreateRequestResponseClient(
                RegistrationClientImpl.getTargetAddress(tenantId),
                () -> createRegistrationClient(tenantId),
                attempt -> {
                    if (attempt.succeeded()) {
                        resultHandler.handle(Future.succeededFuture((RegistrationClient) attempt.result()));
                    } else {
                        resultHandler.handle(Future.failedFuture(attempt.cause()));
                    }
                });
    }

    private Future<RequestResponseClient> createRegistrationClient(final String tenantId) {

        Objects.requireNonNull(tenantId);

        return checkConnection().compose(connected -> {

            final Future<RegistrationClient> result = Future.future();
            RegistrationClientImpl.create(
                    context,
                    clientConfigProperties,
                    cacheManager,
                    connection,
                    tenantId,
                    this::removeRegistrationClient,
                    this::removeRegistrationClient,
                    result.completer());
            return result.map(client -> (RequestResponseClient) client);
        });
    }

    @Override
    public void getOrCreateCredentialsClient(
            final String tenantId,
            final Handler<AsyncResult<CredentialsClient>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);
        getOrCreateRequestResponseClient(
                CredentialsClientImpl.getTargetAddress(tenantId),
                () -> createCredentialsClient(tenantId),
                attempt -> {
                    if (attempt.succeeded()) {
                        resultHandler.handle(Future.succeededFuture((CredentialsClient) attempt.result()));
                    } else {
                        resultHandler.handle(Future.failedFuture(attempt.cause()));
                    }
                });
    }

    private Future<RequestResponseClient> createCredentialsClient(final String tenantId) {

        Objects.requireNonNull(tenantId);

        return checkConnection().compose(connected -> {

            final Future<CredentialsClient> result = Future.future();
            CredentialsClientImpl.create(
                    context,
                    clientConfigProperties,
                    connection,
                    tenantId,
                    this::removeCredentialsClient,
                    this::removeCredentialsClient,
                    result.completer());
            return result.map(client -> (RequestResponseClient) client);
        });
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

        Objects.requireNonNull(completionHandler);
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
