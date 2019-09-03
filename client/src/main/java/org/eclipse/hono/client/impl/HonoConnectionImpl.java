/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.client.impl;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLException;
import javax.security.sasl.AuthenticationException;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ReconnectListener;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.util.HonoProtonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.sasl.MechanismMismatchException;
import io.vertx.proton.sasl.SaslSystemException;

/**
 * A helper class for managing a vertx-proton based AMQP connection to a
 * Hono service endpoint.
 * <p>
 * The connection ensures that all interactions with the peer are performed on the
 * same vert.x {@code Context}. For this purpose the <em>connect</em> methods
 * either use the current Context or create a new Context for connecting to
 * the peer. This same Context is then used for all consecutive interactions with
 * the peer as well, e.g. when creating consumers or senders.
 * <p>
 * Closing or disconnecting the client will <em>release</em> the Context. The next
 * invocation of any of the connect methods will then use the same approach as
 * described above to determine the Context to use.
 */
public class HonoConnectionImpl implements HonoConnection {

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(HonoConnectionImpl.class);
    /**
     * The configuration properties for this client.
     */
    protected final ClientConfigProperties clientConfigProperties;
    /**
     * The vert.x instance to run on.
     */
    protected final Vertx vertx;

    /**
     * The AMQP connection to the peer.
     */
    protected ProtonConnection connection;
    /**
     * The vert.x Context to use for interacting with the peer.
     */
    protected volatile Context context;

    private final List<DisconnectListener<HonoConnection>> disconnectListeners = new ArrayList<>();
    private final List<ReconnectListener<HonoConnection>> reconnectListeners = new ArrayList<>();
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean disconnecting = new AtomicBoolean(false);
    private final ConnectionFactory connectionFactory;
    private final Object connectionLock = new Object();

    private ProtonClientOptions clientOptions;
    private AtomicInteger connectAttempts;
    private List<Symbol> offeredCapabilities = Collections.emptyList();
    private Tracer tracer = NoopTracerFactory.create();

    /**
     * Creates a new client for a set of configuration properties.
     * <p>
     * This constructor creates a connection factory using
     * {@link ConnectionFactory#newConnectionFactory(Vertx, ClientConfigProperties)}.
     *
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param clientConfigProperties The configuration properties to use.
     * @throws NullPointerException if clientConfigProperties is {@code null}
     */
    public HonoConnectionImpl(final Vertx vertx, final ClientConfigProperties clientConfigProperties) {
        this(vertx, null, clientConfigProperties);
    }

    /**
     * Creates a new client for a set of configuration properties.
     * <p>
     * <em>NB</em> Make sure to always use the same set of configuration properties for both the connection factory as
     * well as the Hono client in order to prevent unexpected behavior.
     *
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param connectionFactory The factory to use for creating an AMQP connection to the Hono server.
     * @param clientConfigProperties The configuration properties to use.
     * @throws NullPointerException if clientConfigProperties is {@code null}
     */
    public HonoConnectionImpl(final Vertx vertx, final ConnectionFactory connectionFactory,
            final ClientConfigProperties clientConfigProperties) {

        Objects.requireNonNull(clientConfigProperties);

        if (vertx != null) {
            this.vertx = vertx;
        } else {
            this.vertx = Vertx.vertx();
        }
        if (connectionFactory != null) {
            this.connectionFactory = connectionFactory;
        } else {
            this.connectionFactory = ConnectionFactory.newConnectionFactory(this.vertx, clientConfigProperties);
        }
        this.clientConfigProperties = clientConfigProperties;
        this.connectAttempts = new AtomicInteger(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Vertx getVertx() {
        return vertx;
    }

    /**
     * Sets the OpenTracing {@code Tracer} to use for tracing messages
     * published by devices across Hono's components.
     * <p>
     * If not set explicitly, the {@code NoopTracer} from OpenTracing will
     * be used.
     * 
     * @param opentracingTracer The tracer.
     */
    @Autowired(required = false)
    public final void setTracer(final Tracer opentracingTracer) {
        log.info("using OpenTracing implementation [{}]", opentracingTracer.getClass().getName());
        this.tracer = Objects.requireNonNull(opentracingTracer);
    }

    /**
     * Gets the OpenTracing {@code Tracer} to use for tracing the processing
     * of messages received from or sent to devices.
     * 
     * @return The tracer.
     */
    @Override
    public final Tracer getTracer() {
        return tracer;
    }

    @Override
    public final ClientConfigProperties getConfig() {
        return clientConfigProperties;
    }

    @Override
    public final void addDisconnectListener(final DisconnectListener<HonoConnection> listener) {
        disconnectListeners.add(listener);
    }

    @Override
    public final void addReconnectListener(final ReconnectListener<HonoConnection> listener) {
        reconnectListeners.add(listener);
    }

    /**
     * Executes some code on the vert.x Context that has been used to establish the
     * connection to the peer.
     * 
     * @param <T> The type of the result that the code produces.
     * @param codeToRun The code to execute. The code is required to either complete or
     *                  fail the future that is passed into the handler.
     * @return The future passed into the handler for executing the code. The future
     *         thus indicates the outcome of executing the code. The future will
     *         be failed with a {@link ServerErrorException} if the <em>context</em>
     *         property is {@code null}.
     */
    @Override
    public final <T> Future<T> executeOrRunOnContext(final Handler<Future<T>> codeToRun) {

        if (context == null) {
            // this means that the connection to the peer is not established (yet)
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "not connected"));
        } else {
            return HonoProtonHelper.executeOrRunOnContext(context, codeToRun);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<Void> isConnected() {
        return executeOrRunOnContext(result -> checkConnected(result));
    }

    /**
     * Checks if this client is currently connected to the server.
     *
     * @return A succeeded future if this client is connected.
     */
    protected final Future<Void> checkConnected() {
        final Future<Void> result = Future.future();
        checkConnected(result);
        return result;
    }

    private void checkConnected(final Handler<AsyncResult<Void>> resultHandler) {
        if (isConnectedInternal()) {
            resultHandler.handle(Future.succeededFuture());
        } else {
            resultHandler.handle(Future.failedFuture(
                    new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "not connected")));
        }
    }

    /**
     * Checks if this client is currently connected to the server.
     * <p>
     * Note that the result returned by this method is only meaningful
     * if running on the vert.x event loop context that this client has been
     * created on.
     * 
     * @return {@code true} if the connection is established.
     */
    protected boolean isConnectedInternal() {
        return connection != null && !connection.isDisconnected();
    }

    @Override
    public final boolean isShutdown() {
        return shuttingDown.get();
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
                context = null;
            } else {
                this.offeredCapabilities = Optional.ofNullable(connection.getRemoteOfferedCapabilities())
                        .map(caps -> Collections.unmodifiableList(Arrays.asList(caps)))
                        .orElse(Collections.emptyList());
            }
        }
    }

    /**
     * Gets the underlying connection object that this client uses to interact with the server.
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
    public final Future<HonoConnection> connect() {
        return connect(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<HonoConnection> connect(final ProtonClientOptions options) {
        final Future<HonoConnection> result = Future.future();
        if (shuttingDown.get()) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "client is already shut down"));
        } else {
            connect(options, result, null);
        }
        return result;
    }

    private void connect(
            final ProtonClientOptions options,
            final Handler<AsyncResult<HonoConnection>> connectionHandler,
            final Handler<ProtonConnection> disconnectHandler) {

        context = vertx.getOrCreateContext();
        log.trace("running on vert.x context [event-loop context: {}]", context.isEventLoopContext());

        // context cannot be null thus it is safe to
        // ignore the Future returned by executeOrRunContext
        executeOrRunOnContext(ignore -> {

            if (isConnectedInternal()) {
                log.debug("already connected to server [{}:{}]", connectionFactory.getHost(),
                        connectionFactory.getPort());
                connectionHandler.handle(Future.succeededFuture(this));
            } else if (connecting.compareAndSet(false, true)) {

                log.debug("starting attempt [#{}] to connect to server [{}:{}]",
                        connectAttempts.get() + 1, connectionFactory.getHost(), connectionFactory.getPort());

                clientOptions = options;
                connectionFactory.connect(
                        clientOptions,
                        remoteClose -> onRemoteClose(remoteClose, disconnectHandler),
                        failedConnection -> onRemoteDisconnect(failedConnection, disconnectHandler),
                        conAttempt -> {
                            connecting.compareAndSet(true, false);
                            if (conAttempt.failed()) {
                                reconnect(conAttempt.cause(), connectionHandler, disconnectHandler);
                            } else {
                                final ProtonConnection newConnection = conAttempt.result();
                                if (shuttingDown.get()) {
                                    // if client was shut down in the meantime, we need to immediately
                                    // close again the newly created connection
                                    newConnection.closeHandler(null);
                                    newConnection.disconnectHandler(null);
                                    newConnection.close();
                                    // make sure we try to re-connect as often as we tried to connect initially
                                    connectAttempts = new AtomicInteger(0);
                                    connectionHandler.handle(Future.failedFuture(
                                            new ClientErrorException(HttpURLConnection.HTTP_CONFLICT,
                                                    "client is already shut down")));
                                } else {
                                    setConnection(newConnection);
                                    connectionHandler.handle(Future.succeededFuture(this));
                                }
                            }
                        });
            } else {
                log.debug("already trying to connect to server ...");
                connectionHandler.handle(Future.failedFuture(
                        new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "already connecting to server")));
            }
        });
    }

    private void onRemoteClose(final AsyncResult<ProtonConnection> remoteClose,
            final Handler<ProtonConnection> connectionLossHandler) {

        if (remoteClose.failed()) {
            log.info("remote server [{}:{}] closed connection with error condition: {}",
                    connectionFactory.getHost(), connectionFactory.getPort(), remoteClose.cause().getMessage());
        } else {
            log.info("remote server [{}:{}] closed connection", connectionFactory.getHost(),
                    connectionFactory.getPort());
        }
        connection.disconnectHandler(null);
        connection.close();
        handleConnectionLoss(connectionLossHandler);
    }

    private void onRemoteDisconnect(final ProtonConnection con, final Handler<ProtonConnection> connectionLossHandler) {

        if (con != connection) {
            log.warn("cannot handle failure of unknown connection");
        } else {
            log.debug("lost connection to server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
            handleConnectionLoss(connectionLossHandler);
        }
    }

    private void handleConnectionLoss(final Handler<ProtonConnection> connectionLossHandler) {

        if (isConnectedInternal()) {
            connection.disconnect();
        }

        final ProtonConnection failedConnection = this.connection;
        clearState();

        if (connectionLossHandler != null) {
            connectionLossHandler.handle(failedConnection);
        } else {
            reconnect(this::notifyReconnectHandlers, null);
        }
    }

    private void notifyReconnectHandlers(final AsyncResult<HonoConnection> reconnectAttempt) {
        if (reconnectAttempt.succeeded()) {
            for (final ReconnectListener<HonoConnection> listener : reconnectListeners) {
                listener.onReconnect(this);
            }
        }
    }

    /**
     * Reset all connection and link based state.
     */
    protected void clearState() {

        setConnection(null);

        notifyDisconnectHandlers();
        // make sure we make configured number of attempts to re-connect
        connectAttempts = new AtomicInteger(0);
    }

    private void notifyDisconnectHandlers() {

        for (final DisconnectListener<HonoConnection> listener : disconnectListeners) {
            listener.onDisconnect(this);
        }
    }

    private void reconnect(final Handler<AsyncResult<HonoConnection>> connectionHandler,
            final Handler<ProtonConnection> disconnectHandler) {
        reconnect(null, connectionHandler, disconnectHandler);
    }

    private void reconnect(
            final Throwable connectionFailureCause,
            final Handler<AsyncResult<HonoConnection>> connectionHandler,
            final Handler<ProtonConnection> disconnectHandler) {

        if (shuttingDown.get()) {
            // no need to try to re-connect
            log.info("client is shutting down, giving up attempt to connect");
            connectionHandler.handle(Future.failedFuture(new IllegalStateException("client is shut down")));
            return;
        }
        final int reconnectAttempt = connectAttempts.getAndIncrement();
        if (clientConfigProperties.getReconnectAttempts() - reconnectAttempt == 0) {
            log.info("max number of attempts [{}] to re-connect to peer [{}:{}] have been made, giving up",
                    clientConfigProperties.getReconnectAttempts(), connectionFactory.getHost(), connectionFactory.getPort());
            clearState();
            failConnectionAttempt(connectionFailureCause, connectionHandler);

        } else {
            if (connectionFailureCause != null) {
                logConnectionError(connectionFailureCause);
            }
            // apply exponential backoff with jitter
            // determine the max delay for this reconnect attempt as 2^attempt * delayIncrement
            final long currentMaxDelay = (long) Math.pow(2, reconnectAttempt - 1)
                    * clientConfigProperties.getReconnectDelayIncrement();
            final long reconnectInterval;
            if (currentMaxDelay > clientConfigProperties.getReconnectMinDelay()) {
                // let the actual reconnect delay be a random between the minDelay and the currentMaxDelay,
                // capped by the overall maxDelay
                reconnectInterval = ThreadLocalRandom.current().nextLong(clientConfigProperties.getReconnectMinDelay(),
                        Math.min(clientConfigProperties.getReconnectMaxDelay(), currentMaxDelay));
            } else {
                reconnectInterval = clientConfigProperties.getReconnectMinDelay();
            }
            if (reconnectInterval > 0) {
                log.trace("scheduling new connection attempt in {}ms ...", reconnectInterval);
                vertx.setTimer(reconnectInterval, tid -> {
                    connect(clientOptions, connectionHandler, disconnectHandler);
                });
            } else {
                connect(clientOptions, connectionHandler, disconnectHandler);
            }
        }
    }

    /**
     * Log the connection error.
     * 
     * @param connectionFailureCause The connection error to log, never is {@code null}.
     */
    private void logConnectionError(final Throwable connectionFailureCause) {
        if (isNoteworthyError(connectionFailureCause)) {
            log.warn("connection attempt failed", connectionFailureCause);
        } else {
            log.debug("connection attempt failed", connectionFailureCause);
        }
    }

    private boolean isNoteworthyError(final Throwable connectionFailureCause) {

        return connectionFailureCause instanceof SSLException ||
                connectionFailureCause instanceof AuthenticationException ||
                connectionFailureCause instanceof MechanismMismatchException ||
                (connectionFailureCause instanceof SaslSystemException && ((SaslSystemException) connectionFailureCause).isPermanent());
    }

    private void failConnectionAttempt(final Throwable connectionFailureCause, final Handler<AsyncResult<HonoConnection>> connectionHandler) {

        log.info("stopping connection attempt to server [host: {}, port: {}] due to terminal error",
                connectionFactory.getHost(), connectionFactory.getPort(), connectionFailureCause);

        if (connectionFailureCause == null) {
            connectionHandler.handle(Future.failedFuture(
                    new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "failed to connect")));
        } else if (connectionFailureCause instanceof AuthenticationException) {
            // wrong credentials?
            connectionHandler.handle(Future.failedFuture(
                    new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "failed to authenticate with server")));
        } else if (connectionFailureCause instanceof MechanismMismatchException) {
            connectionHandler.handle(Future.failedFuture(
                    new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "no suitable SASL mechanism found for authentication with server")));
        } else if (connectionFailureCause instanceof SSLException) {
            connectionHandler.handle(Future.failedFuture(
                    new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                            "TLS handshake with server failed: " + connectionFailureCause.getMessage(),
                            connectionFailureCause)));
        } else {
            connectionHandler.handle(Future.failedFuture(
                    new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "failed to connect",
                            connectionFailureCause)));
        }
    }

    /**
     * {@inheritDoc}
     * 
     * This method simply invokes {@link HonoProtonHelper#closeAndFree(Context, ProtonLink, Handler)}
     * with this connection's vert.x context.
     *
     * @param link The link to close. If {@code null}, the given handler is invoked immediately.
     * @param closeHandler The handler to notify once the link has been closed.
     * @throws NullPointerException if context or close handler are {@code null}.
     */
    @Override
    public void closeAndFree(
            final ProtonLink<?> link,
            final Handler<Void> closeHandler) {

        HonoProtonHelper.closeAndFree(context, link, closeHandler);
    }

    /**
     * {@inheritDoc}
     * 
     * This method simply invokes {@link HonoProtonHelper#closeAndFree(Context, ProtonLink, long, Handler)}
     * with this connection's vert.x context.
     */
    @Override
    public void closeAndFree(
            final ProtonLink<?> link,
            final long detachTimeOut,
            final Handler<Void> closeHandler) {

        HonoProtonHelper.closeAndFree(context, link, detachTimeOut, closeHandler);
    }

    /**
     * Creates a sender link.
     * 
     * @param targetAddress The target address of the link.
     * @param qos The quality of service to use for the link.
     * @param closeHook The handler to invoke when the link is closed by the peer (may be {@code null}).
     * @return A future for the created link. The future will be completed once the link is open.
     *         The future will fail with a {@link ServiceInvocationException} if the link cannot be opened.
     * @throws NullPointerException if any of the arguments other than close hook is {@code null}.
     */
    @Override
    public final Future<ProtonSender> createSender(
            final String targetAddress,
            final ProtonQoS qos,
            final Handler<String> closeHook) {

        Objects.requireNonNull(targetAddress);
        Objects.requireNonNull(qos);

        return executeOrRunOnContext(result -> {
            checkConnected().compose(v -> {
                final Future<ProtonSender> senderFuture = Future.future();
                final ProtonSender sender = connection.createSender(targetAddress);
                sender.setQoS(qos);
                sender.setAutoSettle(true);
                sender.openHandler(senderOpen -> {

                    // we only "try" to complete/fail the result future because
                    // it may already have been failed if the connection broke
                    // away after we have sent our attach frame but before we have
                    // received the peer's attach frame

                    if (senderOpen.failed()) {
                        // this means that we have received the peer's attach
                        // and the subsequent detach frame in one TCP read
                        final ErrorCondition error = sender.getRemoteCondition();
                        if (error == null) {
                            log.debug("opening sender [{}] failed", targetAddress, senderOpen.cause());
                            senderFuture.tryFail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND,
                                    "cannot open sender", senderOpen.cause()));
                        } else {
                            log.debug("opening sender [{}] failed: {} - {}", targetAddress, error.getCondition(), error.getDescription());
                            senderFuture.tryFail(StatusCodeMapper.from(error));
                        }

                    } else if (HonoProtonHelper.isLinkEstablished(sender)) {

                        log.debug("sender open [target: {}, sendQueueFull: {}]", targetAddress, sender.sendQueueFull());
                        // wait on credits a little time, if not already given
                        if (sender.getCredit() <= 0) {
                            final long waitOnCreditsTimerId = vertx.setTimer(clientConfigProperties.getFlowLatency(),
                                    timerID -> {
                                        log.debug("sender [target: {}] has {} credits after grace period of {}ms",
                                                targetAddress,
                                                sender.getCredit(), clientConfigProperties.getFlowLatency());
                                        sender.sendQueueDrainHandler(null);
                                        senderFuture.tryComplete(sender);
                                    });
                            sender.sendQueueDrainHandler(replenishedSender -> {
                                log.debug("sender [target: {}] has received {} initial credits",
                                        targetAddress, replenishedSender.getCredit());
                                if (vertx.cancelTimer(waitOnCreditsTimerId)) {
                                    result.tryComplete(replenishedSender);
                                    replenishedSender.sendQueueDrainHandler(null);
                                } // otherwise the timer has already completed the future and cleaned up
                                  // sendQueueDrainHandler
                            });
                        } else {
                            senderFuture.tryComplete(sender);
                        }

                    } else {
                        // this means that the peer did not create a local terminus for the link
                        // and will send a detach frame for closing the link very shortly
                        // see AMQP 1.0 spec section 2.6.3
                        log.debug("peer did not create terminus for target [{}] and will detach the link", targetAddress);
                        senderFuture.tryFail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
                    }
                });
                HonoProtonHelper.setDetachHandler(sender,
                        remoteDetached -> onRemoteDetach(sender, connection.getRemoteContainer(), false, closeHook));
                HonoProtonHelper.setCloseHandler(sender,
                        remoteClosed -> onRemoteDetach(sender, connection.getRemoteContainer(), true, closeHook));
                sender.open();
                vertx.setTimer(clientConfigProperties.getLinkEstablishmentTimeout(),
                        tid -> onTimeOut(sender, clientConfigProperties, senderFuture));
                return senderFuture;
            }).setHandler(result);
        });
    }

    @Override
    public Future<ProtonReceiver> createReceiver(
            final String sourceAddress,
            final ProtonQoS qos,
            final ProtonMessageHandler messageHandler,
            final Handler<String> remoteCloseHook) {
        return createReceiver(sourceAddress, qos, messageHandler, clientConfigProperties.getInitialCredits(), remoteCloseHook);
    }

    @Override
    public Future<ProtonReceiver> createReceiver(
            final String sourceAddress,
            final ProtonQoS qos,
            final ProtonMessageHandler messageHandler,
            final int preFetchSize,
            final Handler<String> remoteCloseHook) {
        return createReceiver(sourceAddress, qos, messageHandler, preFetchSize, true, remoteCloseHook);
    }

    @Override
    public Future<ProtonReceiver> createReceiver(
            final String sourceAddress,
            final ProtonQoS qos,
            final ProtonMessageHandler messageHandler,
            final int preFetchSize,
            final boolean autoAccept,
            final Handler<String> remoteCloseHook) {

        Objects.requireNonNull(sourceAddress);
        Objects.requireNonNull(qos);
        Objects.requireNonNull(messageHandler);
        if (preFetchSize < 0) {
            throw new IllegalArgumentException("pre-fetch size must be >= 0");
        }

        return executeOrRunOnContext(result -> {
            checkConnected().compose(v -> {
                final Future<ProtonReceiver> receiverFuture = Future.future();
                final ProtonReceiver receiver = connection.createReceiver(sourceAddress);
                receiver.setAutoAccept(autoAccept);
                receiver.setQoS(qos);
                receiver.setPrefetch(preFetchSize);
                receiver.handler((delivery, message) -> {
                    try {
                        messageHandler.handle(delivery, message);
                        if (log.isTraceEnabled()) {
                            final int remainingCredits = receiver.getCredit() - receiver.getQueued();
                            log.trace("handling message [remotely settled: {}, queued messages: {}, remaining credit: {}]",
                                    delivery.remotelySettled(), receiver.getQueued(), remainingCredits);
                        }
                    } catch (final Exception ex) {
                        log.warn("error handling message", ex);
                        ProtonHelper.released(delivery, true);
                    }
                });
                receiver.openHandler(recvOpen -> {

                    // we only "try" to complete/fail the result future because
                    // it may already have been failed if the connection broke
                    // away after we have sent our attach frame but before we have
                    // received the peer's attach frame

                    if (recvOpen.failed()) {
                        // this means that we have received the peer's attach
                        // and the subsequent detach frame in one TCP read
                        final ErrorCondition error = receiver.getRemoteCondition();
                        if (error == null) {
                            log.debug("opening receiver [{}] failed", sourceAddress, recvOpen.cause());
                            receiverFuture.tryFail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND,
                                    "cannot open receiver", recvOpen.cause()));
                        } else {
                            log.debug("opening receiver [{}] failed: {} - {}", sourceAddress, error.getCondition(), error.getDescription());
                            receiverFuture.tryFail(StatusCodeMapper.from(error));
                        }
                    } else if (HonoProtonHelper.isLinkEstablished(receiver)) {
                        log.debug("receiver open [source: {}]", sourceAddress);
                        receiverFuture.tryComplete(recvOpen.result());
                    } else {
                        // this means that the peer did not create a local terminus for the link
                        // and will send a detach frame for closing the link very shortly
                        // see AMQP 1.0 spec section 2.6.3
                        log.debug("peer did not create terminus for source [{}] and will detach the link", sourceAddress);
                        receiverFuture.tryFail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
                    }
                });
                HonoProtonHelper.setDetachHandler(receiver, remoteDetached -> onRemoteDetach(receiver,
                        connection.getRemoteContainer(), false, remoteCloseHook));
                HonoProtonHelper.setCloseHandler(receiver, remoteClosed -> onRemoteDetach(receiver,
                        connection.getRemoteContainer(), true, remoteCloseHook));
                receiver.open();
                vertx.setTimer(clientConfigProperties.getLinkEstablishmentTimeout(),
                        tid -> onTimeOut(receiver, clientConfigProperties, receiverFuture));
                return receiverFuture;
            }).setHandler(result);
        });
    }

    private void onTimeOut(
            final ProtonLink<?> link,
            final ClientConfigProperties clientConfig,
            final Future<?> result) {

        if (link.isOpen() && !HonoProtonHelper.isLinkEstablished(link)) {
            log.info("link establishment [peer: {}] timed out after {}ms",
                    clientConfig.getHost(), clientConfig.getLinkEstablishmentTimeout());
            link.close();
            link.free();
            result.tryFail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
        }
    }

    private void onRemoteDetach(
            final ProtonLink<?> link,
            final String remoteContainer,
            final boolean closed,
            final Handler<String> closeHook) {

        final ErrorCondition error = link.getRemoteCondition();
        final String type = link instanceof ProtonSender ? "sender" : "receiver";
        final String address = link instanceof ProtonSender ? link.getTarget().getAddress() :
            link.getSource().getAddress();
        if (error == null) {
            log.debug("{} [{}] detached (with closed={}) by peer [{}]",
                    type, address, closed, remoteContainer);
        } else {
            log.debug("{} [{}] detached (with closed={}) by peer [{}]: {} - {}",
                    type, address, closed, remoteContainer, error.getCondition(), error.getDescription());
        }
        link.close();
        if (HonoProtonHelper.isLinkEstablished(link) && closeHook != null) {
            closeHook.handle(address);
        }
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
                log.error("could not close connection to server", done.cause());
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                log.error("shutdown of client timed out after 5 seconds");
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
        Objects.requireNonNull(completionHandler);
        if (shuttingDown.compareAndSet(Boolean.FALSE, Boolean.TRUE)) {
            closeConnection(completionHandler);
        } else {
            completionHandler.handle(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_CONFLICT,
                    "already shutting down")));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void disconnect() {
        final CountDownLatch countDown = new CountDownLatch(1);
        disconnect(disconnectResult -> {
            if (disconnectResult.succeeded()) {
                log.info("successfully disconnected from the server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
                countDown.countDown();
            } else {
                log.error("could not disconnect from the server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
            }
        });
        try {
            if (!countDown.await(5, TimeUnit.SECONDS)) {
                log.error("Disconnecting from the server [{}:{}] timed out after 5 seconds",
                        connectionFactory.getHost(), connectionFactory.getPort());
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void disconnect(final Handler<AsyncResult<Void>> completionHandler) {
        Objects.requireNonNull(completionHandler);
        if (disconnecting.compareAndSet(Boolean.FALSE, Boolean.TRUE)) {
            closeConnection(completionHandler);
        } else {
            completionHandler.handle(Future.failedFuture(
                    new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "already disconnecting")));
        }
    }

    //-----------------------------------< private methods >---

    private void closeConnection(final Handler<AsyncResult<Void>> completionHandler) {

        final Handler<AsyncResult<Object>> handler = attempt -> {
            disconnecting.compareAndSet(Boolean.TRUE, Boolean.FALSE);
            if (attempt.succeeded()) {
                completionHandler.handle(Future.succeededFuture());
            } else {
                completionHandler.handle(Future.failedFuture(attempt.cause()));
            }
        };

        synchronized (connectionLock) {
            if (isConnectedInternal()) {
                executeOrRunOnContext(r -> {
                    final ProtonConnection connectionToClose = connection;
                    connectionToClose.disconnectHandler(null); // make sure we are not trying to re-connect
                    final Handler<AsyncResult<ProtonConnection>> closeHandler = remoteClose -> {
                        if (remoteClose.succeeded()) {
                            log.info("closed connection to container [{}] at [{}:{}]",
                                    connectionToClose.getRemoteContainer(), connectionFactory.getHost(), connectionFactory.getPort());
                        } else {
                            log.info("closed connection to container [{}] at [{}:{}]",
                                    connectionToClose.getRemoteContainer(), connectionFactory.getHost(),
                                    connectionFactory.getPort(), remoteClose.cause());
                        }
                        clearState();
                        r.complete();
                    };
                    final long timerId = vertx.setTimer(clientConfigProperties.getConnectTimeout(), tid -> {
                        log.info("did not receive remote peer's close frame after {}ms", clientConfigProperties.getConnectTimeout());
                        closeHandler.handle(Future.succeededFuture());
                    });
                    connectionToClose.closeHandler(remoteClose -> {
                        if (vertx.cancelTimer(timerId)) {
                            // timer has not fired yet
                            closeHandler.handle(remoteClose);
                        }
                    });
                    log.info("closing connection to container [{}] at [{}:{}] ...",
                            connectionToClose.getRemoteContainer(), connectionFactory.getHost(), connectionFactory.getPort());
                    connectionToClose.close();
                }).setHandler(handler);
            } else {
                log.info("connection to server [{}:{}] already closed", connectionFactory.getHost(), connectionFactory.getPort());
                handler.handle(Future.succeededFuture());
            }
        }
    }
}
