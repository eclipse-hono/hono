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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.eclipse.hono.connection.ConnectionFactoryImpl.ConnectionFactoryBuilder;
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

    private final Map<String, MessageSender> activeSenders = new HashMap<>();
    private final Map<String, RequestResponseClient> activeRequestResponseClients = new HashMap<>();
    private final Map<String, Boolean> creationLocks = new HashMap<>();
    private final List<Handler<Void>> creationRequests = new ArrayList<>();
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final ConnectionFactory connectionFactory;
    private final ClientConfigProperties clientConfigProperties;
    private final Vertx vertx;
    private final Object connectionLock = new Object();
    private final Context context;

    private ProtonClientOptions clientOptions;
    private ProtonConnection connection;
    private CacheManager cacheManager;

    /**
     * Creates a new client for a set of configuration properties.
     * <p>
     * This constructor creates a connection factory using {@link ConnectionFactoryBuilder}.
     * 
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param clientConfigProperties The configuration properties to use.
     */
    public HonoClientImpl(final Vertx vertx, final ClientConfigProperties clientConfigProperties) {

        if (vertx != null) {
            this.vertx = vertx;
        } else {
            this.vertx = Vertx.vertx();
        }
        this.context = vertx.getOrCreateContext();
        this.clientConfigProperties = clientConfigProperties;
        this.connectionFactory = ConnectionFactoryBuilder.newBuilder(clientConfigProperties).vertx(vertx).build();
    }

    /**
     * Creates a new client for a set of configuration properties.
     * <p>
     * <em>NB</em> Make sure to always use the same set of configuration properties for both
     * the connection factory as well as the Hono client in order to prevent unexpected behavior.
     * 
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param connectionFactory The factory to use for creating an AMQP connection to the Hono server.
     * @param clientConfigProperties The configuration properties to use.
     */
    public HonoClientImpl(final Vertx vertx, final ConnectionFactory connectionFactory, final ClientConfigProperties clientConfigProperties) {

        if (vertx != null) {
            this.vertx = vertx;
        } else {
            this.vertx = Vertx.vertx();
        }
        this.context = vertx.getOrCreateContext();
        this.connectionFactory = connectionFactory;
        this.clientConfigProperties = clientConfigProperties;
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

    @Override
    public Future<Boolean> isConnected() {

        final Future<Boolean> result = Future.future();
        context.runOnContext(check -> {
            result.complete(isConnectedInternal());
        });
        return result;
    }

    private Future<Void> checkConnected() {

        final Future<Void> result = Future.future();
        if (isConnectedInternal()) {
            result.complete();
        } else {
            result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "not connected"));
        }
        return result;
    }

    private boolean isConnectedInternal() {
        return connection != null && !connection.isDisconnected();
    }

    /**
     * Sets the connection used to interact with the Hono server.
     *
     * @param connection The connection to use.
     */
    void setConnection(final ProtonConnection connection) {
        synchronized (connectionLock) {
            this.connection = connection;
        }
    }

    @Override
    public Future<HonoClient> connect(final ProtonClientOptions options) {
        return connect(options, null);
    }

    @Override
    public Future<HonoClient> connect(
            final ProtonClientOptions options,
            final Handler<ProtonConnection> disconnectHandler) {

        final Future<HonoClient> result = Future.future();
        if (shuttingDown.get()) {
            result.fail(new IllegalStateException("client is shut down"));
        } else {
            connect(options, result.completer(), disconnectHandler);
        }
        return result;
    }

    private void connect(
            final ProtonClientOptions options,
            final Handler<AsyncResult<HonoClient>> connectionHandler,
            final Handler<ProtonConnection> disconnectHandler) {

        context.runOnContext(connect -> {

            if (isConnectedInternal()) {
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
                                if (shuttingDown.get()) {
                                    // if client was already shutdown in the meantime we give our best to cleanup connection
                                    shutdownConnection(result -> {});
                                    connectionHandler.handle(Future.failedFuture(new IllegalStateException("client is shut down")));
                                } else {
                                    connectionHandler.handle(Future.succeededFuture(this));
                                }
                            }
                        });
            } else {
                LOG.debug("already trying to connect to server ...");
                connectionHandler.handle(Future.failedFuture(new IllegalStateException("already connecting to server")));
            }
        });
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

        if (isConnectedInternal()) {
            connection.disconnect();
        }

        final ProtonConnection failedConnection = this.connection;
        setConnection(null);

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

    private void reconnect(final Handler<AsyncResult<HonoClient>> connectionHandler, final Handler<ProtonConnection> disconnectHandler) {

        if (clientOptions == null || clientOptions.getReconnectAttempts() == 0) {
            connectionHandler.handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "failed to connect")));
        } else if (shuttingDown.get()) {
            // no need to try to re-connect
            connectionHandler.handle(Future.failedFuture(new IllegalStateException("client is shut down")));
        } else {
            LOG.trace("scheduling re-connect attempt ...");
            // give Vert.x some time to clean up NetClient
            vertx.setTimer(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, tid -> {
                LOG.debug("attempting to re-connect to server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
                connect(clientOptions, connectionHandler, disconnectHandler);
            });
        }
    }

    @Override
    public Future<MessageSender> getOrCreateTelemetrySender(final String tenantId) {
        return getOrCreateTelemetrySender(tenantId, null);
    }

    @Override
    public Future<MessageSender> getOrCreateTelemetrySender(final String tenantId, final String deviceId) {

        Objects.requireNonNull(tenantId);
        return getOrCreateSender(
                TelemetrySenderImpl.getTargetAddress(tenantId, deviceId),
                () -> createTelemetrySender(tenantId, deviceId));
    }

    private Future<MessageSender> createTelemetrySender(
            final String tenantId,
            final String deviceId) {

        return checkConnected().compose(connected -> {
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
    public Future<MessageSender> getOrCreateEventSender(final String tenantId) {
        return getOrCreateEventSender(tenantId, null);
    }

    @Override
    public Future<MessageSender> getOrCreateEventSender(
            final String tenantId,
            final String deviceId) {

        Objects.requireNonNull(tenantId);
        return getOrCreateSender(
                EventSenderImpl.getTargetAddress(tenantId, deviceId),
                () -> createEventSender(tenantId, deviceId));
    }

    private Future<MessageSender> createEventSender(
            final String tenantId,
            final String deviceId) {

        return checkConnected().compose(connected -> {
            Future<MessageSender> result = Future.future();
            EventSenderImpl.create(context, clientConfigProperties, connection, tenantId, deviceId,
                        onSenderClosed -> {
                            activeSenders.remove(EventSenderImpl.getTargetAddress(tenantId, deviceId));
                        },
                        result.completer());
            return result;
        });
    }

    Future<MessageSender> getOrCreateSender(
            final String key,
            final Supplier<Future<MessageSender>> newSenderSupplier) {

        final Future<MessageSender> result = Future.future();

        context.runOnContext(get -> {
            final MessageSender sender = activeSenders.get(key);
            if (sender != null && sender.isOpen()) {
                LOG.debug("reusing existing message sender [target: {}, credit: {}]", key, sender.getCredit());
                result.complete(sender);
            } else if (!creationLocks.computeIfAbsent(key, k -> Boolean.FALSE)) {
                // register a handler to be notified if the underlying connection to the server fails
                // so that we can fail the result handler passed in
                final Handler<Void> connectionFailureHandler = connectionLost -> {
                    // remove lock so that next attempt to open a sender doesn't fail
                    creationLocks.remove(key);
                    result.tryFail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no connection to service"));
                };
                creationRequests.add(connectionFailureHandler);
                creationLocks.put(key, Boolean.TRUE);
                LOG.debug("creating new message sender for {}", key);

                newSenderSupplier.get().setHandler(creationAttempt -> {
                    creationLocks.remove(key);
                    creationRequests.remove(connectionFailureHandler);
                    if (creationAttempt.succeeded()) {
                        MessageSender newSender = creationAttempt.result();
                        LOG.debug("successfully created new message sender for {}", key);
                        activeSenders.put(key, newSender);
                        result.tryComplete(newSender);
                    } else {
                        LOG.debug("failed to create new message sender for {}", key, creationAttempt.cause());
                        activeSenders.remove(key);
                        result.tryFail(creationAttempt.cause());
                    }
                });

            } else {
                LOG.debug("already trying to create a message sender for {}", key);
                result.fail(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "no connection to service"));
            }
        });
        return result;
    }

    @Override
    public Future<MessageConsumer> createTelemetryConsumer(
            final String tenantId,
            final Consumer<Message> messageConsumer,
            final Handler<Void> closeHandler) {

        return createConsumer(
                tenantId,
                () -> newTelemetryConsumer(tenantId, messageConsumer, closeHandler));
    }

    private Future<MessageConsumer> newTelemetryConsumer(
            final String tenantId,
            final Consumer<Message> messageConsumer,
            final Handler<Void> closeHandler) {

        return checkConnected().compose(con -> {
            final Future<MessageConsumer> result = Future.future();
            TelemetryConsumerImpl.create(context, clientConfigProperties, connection, tenantId,
                        connectionFactory.getPathSeparator(), messageConsumer, result.completer(),
                    closeHook -> closeHandler.handle(null));
            return result;
        });
    }

    @Override
    public Future<MessageConsumer> createEventConsumer(
            final String tenantId,
            final Consumer<Message> eventConsumer,
            final Handler<Void> closeHandler) {

        return createEventConsumer(tenantId, (delivery, message) -> eventConsumer.accept(message), closeHandler);
    }

    @Override
    public Future<MessageConsumer> createEventConsumer(
            final String tenantId,
            final BiConsumer<ProtonDelivery, Message> messageConsumer,
            final Handler<Void> closeHandler) {

        return createConsumer(
                tenantId,
                () -> newEventConsumer(tenantId, messageConsumer, closeHandler));
    }

    private Future<MessageConsumer> newEventConsumer(
            final String tenantId,
            final BiConsumer<ProtonDelivery, Message> messageConsumer,
            final Handler<Void> closeHandler) {

        return checkConnected().compose(con -> {
            final Future<MessageConsumer> result = Future.future();
            EventConsumerImpl.create(context, clientConfigProperties, connection, tenantId,
                    connectionFactory.getPathSeparator(), messageConsumer, result.completer(),
                    closeHook -> closeHandler.handle(null));
            return result;
        });
    }

    Future<MessageConsumer> createConsumer(
            final String tenantId,
            final Supplier<Future<MessageConsumer>> newConsumerSupplier) {

        final Future<MessageConsumer> result = Future.future();
        context.runOnContext(get -> {

            // register a handler to be notified if the underlying connection to the server fails
            // so that we can fail the result handler passed in
            final Handler<Void> connectionFailureHandler = connectionLost -> {
                result.tryFail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "connection to server lost"));
            };
            creationRequests.add(connectionFailureHandler);

            newConsumerSupplier.get().setHandler(attempt -> {
                creationRequests.remove(connectionFailureHandler);
                if (attempt.succeeded()) {
                    result.tryComplete(attempt.result());
                } else {
                    result.tryFail(attempt.cause());
                }
            });
        });
        return result;
    }

    @Override
    public Future<CredentialsClient> getOrCreateCredentialsClient(
            final String tenantId) {

        Objects.requireNonNull(tenantId);
        final Future<CredentialsClient> result = Future.future();
        getOrCreateRequestResponseClient(
                CredentialsClientImpl.getTargetAddress(tenantId),
                () -> newCredentialsClient(tenantId),
                attempt -> {
                    if (attempt.succeeded()) {
                        result.complete((CredentialsClient) attempt.result());
                    } else {
                        result.fail(attempt.cause());
                    }
                });
        return result;
    }

    private Future<RequestResponseClient> newCredentialsClient(final String tenantId) {

        return checkConnected().compose(connected -> {

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

        final String key = CredentialsClientImpl.getTargetAddress(tenantId);
        final RequestResponseClient client = activeRequestResponseClients.remove(key);
        if (client != null) {
            client.close(s -> {});
            LOG.debug("closed and removed credentials client for [{}]", tenantId);
        }
    }

    @Override
    public Future<RegistrationClient> getOrCreateRegistrationClient(
            final String tenantId) {

        Objects.requireNonNull(tenantId);

        final Future<RegistrationClient> result = Future.future();
        getOrCreateRequestResponseClient(
                RegistrationClientImpl.getTargetAddress(tenantId),
                () -> newRegistrationClient(tenantId),
                attempt -> {
                    if (attempt.succeeded()) {
                        result.complete((RegistrationClient) attempt.result());
                    } else {
                        result.fail(attempt.cause());
                    }
                });
        return result;
    }

    private Future<RequestResponseClient> newRegistrationClient(final String tenantId) {

        Objects.requireNonNull(tenantId);

        return checkConnected().compose(connected -> {

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

    private void removeRegistrationClient(final String tenantId) {

        final String key = RegistrationClientImpl.getTargetAddress(tenantId);
        final RequestResponseClient client = activeRequestResponseClients.remove(key);
        if (client != null) {
            client.close(s -> {});
            LOG.debug("closed and removed registration client for [{}]", tenantId);
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

        context.runOnContext(get -> {
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

                clientSupplier.get().setHandler(creationAttempt -> {
                    if (creationAttempt.succeeded()) {
                        LOG.debug("successfully created new client for {}", key);
                        activeRequestResponseClients.put(key, creationAttempt.result());
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
        });
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
                LOG.error("shutdown of client timed out after 5 seconds");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void shutdown(final Handler<AsyncResult<Void>> completionHandler) {

        if (shuttingDown.compareAndSet(Boolean.FALSE, Boolean.TRUE)) {
            context.runOnContext(shutDown -> {
                if (isConnectedInternal()) {
                    shutdownConnection(completionHandler);
                } else {
                    LOG.info("connection to server [{}:{}] already closed", connectionFactory.getHost(), connectionFactory.getPort());
                    completionHandler.handle(Future.succeededFuture());
                }
            });
        } else {
            completionHandler.handle(Future.failedFuture(new IllegalStateException("already shutting down")));
        }
    }

    private void shutdownConnection(final Handler<AsyncResult<Void>> completionHandler) {

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
    }
}
