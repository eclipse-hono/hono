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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RequestResponseClient;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.ConnectionFactoryImpl.ConnectionFactoryBuilder;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class HonoClientImpl implements HonoClient {

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
    private CacheProvider cacheProvider;
    private AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private List<Symbol> offeredCapabilities = Collections.emptyList();

    /**
     * Creates a new client for a set of configuration properties.
     * <p>
     * This constructor creates a connection factory using {@link ConnectionFactoryBuilder}.
     * 
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param clientConfigProperties The configuration properties to use.
     */
    public HonoClientImpl(final Vertx vertx, final ClientConfigProperties clientConfigProperties) {

        this(vertx, null, clientConfigProperties);
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
        if (connectionFactory != null) {
            this.connectionFactory = connectionFactory;
        } else {
            this.connectionFactory = ConnectionFactoryBuilder.newBuilder(clientConfigProperties).vertx(this.vertx).build();
        }
        this.context = this.vertx.getOrCreateContext();
        this.clientConfigProperties = clientConfigProperties;
    }

    /**
     * Sets a provider for creating cache instances to be used in Hono clients.
     * 
     * @param cacheProvider The cache provider.
     * @throws NullPointerException if manager is {@code null}.
     */
    public final void setCacheProvider(final CacheProvider cacheProvider) {
        this.cacheProvider = Objects.requireNonNull(cacheProvider);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<Void> isConnected() {

        final Future<Void> result = Future.future();
        context.runOnContext(check -> {
            if (isConnectedInternal()) {
                result.complete();
            } else {
                result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
            }
        });
        return result;
    }

    /**
     * Checks if this client is currently connected to the server.
     * 
     * @return A succeeded future if this client is connected.
     */
    protected final Future<Void> checkConnected() {

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
            if (connection == null) {
                this.offeredCapabilities = Collections.emptyList();
            } else {
                this.offeredCapabilities = Optional.ofNullable(connection.getRemoteOfferedCapabilities())
                        .map(caps -> Collections.unmodifiableList(Arrays.asList(caps)))
                        .orElse(Collections.emptyList());
            }
        }
    }

    /**
     * Gets the underlying connection object that this client
     * uses to interact with the server.
     * 
     * @return The connection.
     */
    protected final ProtonConnection getConnection() {
        synchronized (connectionLock) {
            return this.connection;
        }
    }

    @Override
    public final boolean supportsCapability(final Symbol capability) {
        if (capability == null) {
            return false;
        } else {
            synchronized (connectionLock) {
                return offeredCapabilities.contains(capability);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<HonoClient> connect() {
        return connect(null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<HonoClient> connect(final ProtonClientOptions options) {
        return connect(Objects.requireNonNull(options), null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<HonoClient> connect(final Handler<ProtonConnection> disconnectHandler) {
        return connect(null, Objects.requireNonNull(disconnectHandler));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<HonoClient> connect(
            final ProtonClientOptions options,
            final Handler<ProtonConnection> disconnectHandler) {

        final Future<HonoClient> result = Future.future();
        if (shuttingDown.get()) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "client is already shut down"));
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
                    // by default, try to re-connect forever
                    clientOptions = new ProtonClientOptions()
                            .setConnectTimeout(200)
                            .setReconnectAttempts(-1)
                            .setReconnectInterval(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS);
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
                                if (conAttempt.cause() instanceof SecurityException) {
                                    // SASL handshake has failed
                                    connectionHandler.handle(Future.failedFuture(
                                            new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "failed to authenticate with server")));
                                } else {
                                    reconnect(conAttempt.cause(), connectionHandler, disconnectHandler);
                                }
                            } else {
                                // make sure we try to re-connect as often as we tried to connect initially
                                reconnectAttempts = new AtomicInteger(0);
                                final ProtonConnection newConnection = conAttempt.result();
                                if (shuttingDown.get()) {
                                    // if client was shut down in the meantime, we need to immediately
                                    // close again the newly created connection
                                    newConnection.closeHandler(null);
                                    newConnection.disconnectHandler(null);
                                    newConnection.close();
                                    connectionHandler.handle(Future.failedFuture(
                                            new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "client is already shut down")));
                                } else {
                                    setConnection(newConnection);
                                    connectionHandler.handle(Future.succeededFuture(this));
                                }
                            }
                        });
            } else {
                LOG.debug("already trying to connect to server ...");
                connectionHandler.handle(Future.failedFuture(
                        new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "already connecting to server")));
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
        offeredCapabilities = Collections.emptyList();

        activeSenders.clear();
        activeRequestResponseClients.clear();
        failAllCreationRequests();

        if (connectionLossHandler != null) {
            connectionLossHandler.handle(failedConnection);
        } else {
            reconnect(attempt -> {}, null);
        }
    }

    private void failAllCreationRequests() {

        for (Iterator<Handler<Void>> iter = creationRequests.iterator(); iter.hasNext(); ) {
            iter.next().handle(null);
            iter.remove();
        }
    }

    private void reconnect(final Handler<AsyncResult<HonoClient>> connectionHandler, final Handler<ProtonConnection> disconnectHandler) {
        reconnect(null, connectionHandler, disconnectHandler);
    }

    private void reconnect(
            final Throwable connectionFailureCause,
            final Handler<AsyncResult<HonoClient>> connectionHandler,
            final Handler<ProtonConnection> disconnectHandler) {

        if (shuttingDown.get()) {
            // no need to try to re-connect
            connectionHandler.handle(Future.failedFuture(new IllegalStateException("client is shut down")));
        } else if (clientOptions.getReconnectAttempts() - reconnectAttempts.get() == 0) {
            reconnectAttempts = new AtomicInteger(0);
            LOG.debug("max number of attempts [{}] to re-connect to peer [{}:{}] have been made, giving up",
                    clientOptions.getReconnectAttempts(), connectionFactory.getHost(), connectionFactory.getPort());
            if (connectionFailureCause == null) {
                connectionHandler.handle(Future.failedFuture(
                        new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "failed to connect")));
            } else {
                connectionHandler.handle(Future.failedFuture(
                        new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "failed to connect", connectionFailureCause)));
            }
        } else {
            LOG.trace("scheduling attempt to re-connect ...");
            reconnectAttempts.getAndIncrement();
            // give Vert.x some time to clean up NetClient
            vertx.setTimer(clientOptions.getReconnectInterval(), tid -> {
                LOG.debug("starting attempt [#{}] to re-connect to server [{}:{}]",
                        reconnectAttempts.get(), connectionFactory.getHost(), connectionFactory.getPort());
                connect(clientOptions, connectionHandler, disconnectHandler);
            });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageSender> getOrCreateTelemetrySender(final String tenantId) {
        return getOrCreateTelemetrySender(tenantId, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageSender> getOrCreateTelemetrySender(final String tenantId, final String deviceId) {

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

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageSender> getOrCreateEventSender(final String tenantId) {
        return getOrCreateEventSender(tenantId, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageSender> getOrCreateEventSender(
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

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageConsumer> createTelemetryConsumer(
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

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageConsumer> createEventConsumer(
            final String tenantId,
            final Consumer<Message> eventConsumer,
            final Handler<Void> closeHandler) {

        return createEventConsumer(tenantId, (delivery, message) -> eventConsumer.accept(message), closeHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageConsumer> createEventConsumer(
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

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<CredentialsClient> getOrCreateCredentialsClient(
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

    /**
     * Creates a new instance of {@link CredentialsClient} scoped for the given tenant identifier.
     * <p>
     * Custom implementation of {@link CredentialsClient} can be instantiated by overriding this method. Any such
     * instance should be scoped to the given tenantId. Custom extension of {@link HonoClientImpl} must invoke
     * {@link #removeCredentialsClient(String)} to cleanup when finished with the client.
     *  
     * @param tenantId tenant scope for which the client is instantiated
     * @return a future containing an instance of {@link CredentialsClient}
     * @see CredentialsClient
     */
    protected Future<RequestResponseClient> newCredentialsClient(final String tenantId) {

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

    /**
     * Removes a credentials client from the list of active clients.
     * <p>
     * Once a client has been removed, the next invocation
     * of the corresponding <em>getOrCreateCredentialsClient</em>
     * method will result in a new client being created
     * (and added to the list of active clients).
     * 
     * @param tenantId The tenant that the client is scoped to.
     */
    protected final void removeCredentialsClient(final String tenantId) {

        final String targetAddress = CredentialsClientImpl.getTargetAddress(tenantId);
        removeActiveRequestResponseClient(targetAddress);
    }

    private void removeActiveRequestResponseClient(final String targetAddress) {

        final RequestResponseClient client = activeRequestResponseClients.remove(targetAddress);
        if (client != null) {
            client.close(s -> {});
            LOG.debug("closed and removed client for [{}]", targetAddress);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<RegistrationClient> getOrCreateRegistrationClient(
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

    /**
     * Creates a new instance of {@link RegistrationClient} scoped for the given tenantId.
     * <p>
     * Custom implementation of {@link RegistrationClient} can be instantiated by overriding this method. Any such
     * instance should be scoped to the given tenantId. Custom extension of {@link HonoClientImpl} must invoke
     * {@link #removeRegistrationClient(String)} to cleanup when finished with the client.
     *
     * @param tenantId tenant scope for which the client is instantiated
     * @return a future containing an instance of {@link RegistrationClient}
     * @see RegistrationClient
     */
    protected Future<RequestResponseClient> newRegistrationClient(final String tenantId) {

        Objects.requireNonNull(tenantId);

        return checkConnected().compose(connected -> {

            final Future<RegistrationClient> result = Future.future();
            RegistrationClientImpl.create(
                    context,
                    clientConfigProperties,
                    cacheProvider,
                    connection,
                    tenantId,
                    this::removeRegistrationClient,
                    this::removeRegistrationClient,
                    result.completer());
            return result.map(client -> (RequestResponseClient) client);
        });
    }

    /**
     * Removes a registration client from the list of active clients.
     * <p>
     * Once a client has been removed, the next invocation
     * of the corresponding <em>getOrCreateRegistrationClient</em>
     * method will result in a new client being created
     * (and added to the list of active clients).
     * 
     * @param tenantId The tenant that the client is scoped to.
     */
    protected final void removeRegistrationClient(final String tenantId) {

        final String targetAddress = RegistrationClientImpl.getTargetAddress(tenantId);
        removeActiveRequestResponseClient(targetAddress);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<TenantClient> getOrCreateTenantClient() {

        final Future<TenantClient> result = Future.future();
        getOrCreateRequestResponseClient(
                TenantClientImpl.getTargetAddress(),
                () -> newTenantClient(),
                attempt -> {
                    if (attempt.succeeded()) {
                        result.complete((TenantClient) attempt.result());
                    } else {
                        result.fail(attempt.cause());
                    }
                });
        return result;
    }

    /**
     * Creates a new instance of {@link TenantClient}.
     * <p>
     * Custom implementation of {@link TenantClient} can be instantiated by overriding this method.
     * Custom extension of {@link HonoClientImpl} must invoke
     * {@link #removeTenantClient()} to cleanup when finished with the client.
     *
     * @return a future containing an instance of {@link TenantClient}
     * @see TenantClient
     */
    protected Future<RequestResponseClient> newTenantClient() {

        return checkConnected().compose(connected -> {

            final Future<TenantClient> result = Future.future();
            TenantClientImpl.create(
                    context,
                    clientConfigProperties,
                    cacheProvider,
                    connection,
                    this::removeTenantClient,
                    this::removeTenantClient,
                    result.completer());
            return result.map(client -> (RequestResponseClient) client);
        });
    }

    private void removeTenantClient(final String tenantId) {
        // the tenantId is not relevant for this client, so ignore it
        removeTenantClient();
    }

    /**
     *
     * Removes a tenant client from the list of active clients.
     * <p>
     * Once a client has been removed, the next invocation
     * of the corresponding <em>getOrCreateTenantClient</em>
     * method will result in a new client being created
     * (and added to the list of active clients).
     *
     */
    protected void removeTenantClient() {

        final String targetAddress = TenantClientImpl.getTargetAddress();
        removeActiveRequestResponseClient(targetAddress);
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
                LOG.debug("creating new client [target: {}]", key);

                clientSupplier.get().setHandler(creationAttempt -> {
                    if (creationAttempt.succeeded()) {
                        LOG.debug("successfully created new client [target: {}]", key);
                        activeRequestResponseClients.put(key, creationAttempt.result());
                    } else {
                        LOG.debug("failed to create new client [target: {}]", key, creationAttempt.cause());
                        activeRequestResponseClients.remove(key);
                    }
                    creationLocks.remove(key);
                    creationRequests.remove(connectionFailureHandler);
                    resultHandler.handle(creationAttempt);
                });

            } else {
                LOG.debug("already trying to create a client [target: {}]", key);
                resultHandler.handle(Future.failedFuture(new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE, "no connection to service")));
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void shutdown() {

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

    /**
     * {@inheritDoc}
     */
    @Override
    public final void shutdown(final Handler<AsyncResult<Void>> completionHandler) {

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
