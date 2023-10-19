/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.client.amqp.connection.impl;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLException;
import javax.security.sasl.AuthenticationException;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.ConnectionFactory;
import org.eclipse.hono.client.amqp.connection.DisconnectListener;
import org.eclipse.hono.client.amqp.connection.ErrorConverter;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.HonoProtonHelper;
import org.eclipse.hono.client.amqp.connection.ReconnectListener;
import org.eclipse.hono.util.Futures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonSession;
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
public final class HonoConnectionImpl implements HonoConnection {

    private static final String MSG_NOT_CONNECTED = "not connected";
    private static final Logger LOG = LoggerFactory.getLogger(HonoConnectionImpl.class);

    private final ClientConfigProperties clientConfigProperties;
    private final Vertx vertx;
    private final List<DisconnectListener<HonoConnection>> disconnectListeners = new ArrayList<>();
    private final List<DisconnectListener<HonoConnection>> oneTimeDisconnectListeners = Collections.synchronizedList(new ArrayList<>());
    private final List<ReconnectListener<HonoConnection>> reconnectListeners = new ArrayList<>();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean disconnecting = new AtomicBoolean(false);
    private final ConnectionFactory connectionFactory;
    private final Object connectionLock = new Object();
    private final AtomicReference<ConnectionAttempt> currentConnectionAttempt = new AtomicReference<>();
    private final String containerId;
    private final DeferredConnectionCheckHandler deferredConnectionCheckHandler;

    /**
     * The vert.x Context to use for interacting with the peer.
     */
    private Context context;
    private ProtonClientOptions lastUsedClientOptions;
    private List<Symbol> offeredCapabilities = Collections.emptyList();
    private Tracer tracer = NoopTracerFactory.create();
    private ProtonSession session;
    /**
     * The AMQP connection to the peer.
     */
    private ProtonConnection connection;

    /**
     * Creates a new client for a set of configuration properties.
     * <p>
     * This constructor creates a connection factory using
     * {@link ConnectionFactory#newConnectionFactory(Vertx, ClientConfigProperties)}.
     *
     * @param vertx The Vert.x instance to execute the client on.
     * @param clientConfigProperties The configuration properties to use.
     * @throws NullPointerException if vertx or clientConfigProperties is {@code null}.
     */
    public HonoConnectionImpl(final Vertx vertx, final ClientConfigProperties clientConfigProperties) {
        this(vertx, clientConfigProperties, null);
    }

    /**
     * Creates a new client for a set of configuration properties.
     *
     * @param vertx The Vert.x instance to run the connection on.
     * @param clientConfigProperties The configuration properties to use. This constructor creates and then uses a copy
     *                               of the passed in properties object.
     *                               <p>
     *                               <em>NB</em> When passing in a connection factory, make sure that the connection
     *                               factory is using the client configuration passed in via this parameter.
     * @param connectionFactory The factory to use for creating an AMQP connection to the peer or {@code null} in order
     *                          to use the default connection factory based on the given client configuration.
     * @throws NullPointerException if vertx or clientConfigProperties are {@code null}.
     */
    public HonoConnectionImpl(
            final Vertx vertx,
            final ClientConfigProperties clientConfigProperties,
            final ConnectionFactory connectionFactory) {

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(clientConfigProperties);

        this.vertx = vertx;
        this.deferredConnectionCheckHandler = new DeferredConnectionCheckHandler(vertx);
        if (connectionFactory != null) {
            this.connectionFactory = connectionFactory;
        } else {
            this.connectionFactory = ConnectionFactory.newConnectionFactory(this.vertx, clientConfigProperties);
        }
        this.containerId = ConnectionFactory.createContainerId(clientConfigProperties.getName(),
                clientConfigProperties.getServerRole(), UUID.randomUUID());
        this.clientConfigProperties = new ClientConfigProperties(clientConfigProperties);
    }

    @Override
    public Vertx getVertx() {
        return vertx;
    }

    /**
     * Sets the OpenTracing {@code Tracer} to associate with this connection.
     * <p>
     * If not set explicitly, the {@code NoopTracer} from OpenTracing will
     * be used.
     *
     * @param opentracingTracer The tracer.
     * @throws NullPointerException if tracer is {@code null}.
     */
    public void setTracer(final Tracer opentracingTracer) {
        this.tracer = Objects.requireNonNull(opentracingTracer);
    }

    /**
     * Gets the OpenTracing {@code Tracer} that is associated with this connection.
     *
     * @return The tracer.
     */
    @Override
    public Tracer getTracer() {
        return tracer;
    }

    @Override
    public ClientConfigProperties getConfig() {
        return clientConfigProperties;
    }

    @Override
    public void addDisconnectListener(final DisconnectListener<HonoConnection> listener) {
        disconnectListeners.add(listener);
    }

    @Override
    public void addReconnectListener(final ReconnectListener<HonoConnection> listener) {
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
    public <T> Future<T> executeOnContext(final Handler<Promise<T>> codeToRun) {

        if (context == null) {
            // this means that the connection to the peer is not established (yet) and no (re)connect attempt is in progress
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, MSG_NOT_CONNECTED));
        } else {
            return Futures.executeOnContextWithSameRoot(context, codeToRun);
        }
    }

    @Override
    public Future<Void> isConnected() {
        return executeOnContext(this::checkConnected);
    }

    /**
     * Checks if this client is currently connected to the server.
     *
     * @return A succeeded future if this client is connected.
     */
    private Future<Void> checkConnected() {
        final Promise<Void> result = Promise.promise();
        checkConnected(result);
        return result.future();
    }

    private void checkConnected(final Handler<AsyncResult<Void>> resultHandler) {
        if (isConnectedInternal()) {
            resultHandler.handle(Future.succeededFuture());
        } else {
            resultHandler.handle(Future.failedFuture(
                    new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, MSG_NOT_CONNECTED)));
        }
    }

    @Override
    public Future<Void> isConnected(final long waitForCurrentConnectAttemptTimeout) {
        return executeOnContext(result -> checkConnected(result, waitForCurrentConnectAttemptTimeout));
    }

    private void checkConnected(final Handler<AsyncResult<Void>> resultHandler, final long waitForCurrentConnectAttemptTimeout) {
        if (isConnectedInternal()) {
            resultHandler.handle(Future.succeededFuture());
        } else if (waitForCurrentConnectAttemptTimeout > 0 && deferredConnectionCheckHandler.isConnectionAttemptInProgress()) {
            // connect attempt in progress - let its completion complete the resultHandler here
            LOG.debug("connection attempt to server [{}:{}] in progress, connection check will be completed with its result",
                    connectionFactory.getHost(), connectionFactory.getPort());
            final boolean added = deferredConnectionCheckHandler.addConnectionCheck(resultHandler,
                    waitForCurrentConnectAttemptTimeout);
            if (!added) {
                // connection attempt was finished in between
                checkConnected(resultHandler);
            }
        } else {
            resultHandler.handle(Future.failedFuture(
                    new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, MSG_NOT_CONNECTED)));
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
    private boolean isConnectedInternal() {
        return connection != null && !connection.isDisconnected() && session != null;
    }

    @Override
    public boolean isShutdown() {
        return shuttingDown.get();
    }

    /**
     * Sets the connection used to interact with the Hono server.
     *
     * @param connection The connection to use.
     * @param session The session to use for links created on the connection.
     */
    private void setConnection(final ProtonConnection connection, final ProtonSession session) {
        synchronized (connectionLock) {
            this.connection = connection;
            this.session = session;
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

    @Override
    public boolean supportsCapability(final Symbol capability) {
        if (capability == null) {
            return false;
        } else {
            synchronized (connectionLock) {
                return offeredCapabilities.contains(capability);
            }
        }
    }

    @Override
    public Future<HonoConnection> connect() {
        return connect(null);
    }

    @Override
    public Future<HonoConnection> connect(final ProtonClientOptions options) {
        final Promise<HonoConnection> result = Promise.promise();
        connect(options, result, false);
        return result.future();
    }

    private void connect(
            final ProtonClientOptions options,
            final Handler<AsyncResult<HonoConnection>> connectionHandler,
            final boolean isReconnect) {

        if (shuttingDown.get()) {
            connectionHandler.handle(Future.failedFuture(
                    new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "client is already shut down")));
            return;
        }
        context = vertx.getOrCreateContext();
        LOG.trace("running on vert.x context [event-loop context: {}]", context.isEventLoopContext());

        // context cannot be null thus it is safe to
        // ignore the Future returned by executeOnContext
        executeOnContext(ignore -> {

            if (isConnectedInternal()) {
                LOG.debug("already connected to server [{}:{}, role: {}]",
                        connectionFactory.getHost(),
                        connectionFactory.getPort(),
                        connectionFactory.getServerRole());
                connectionHandler.handle(Future.succeededFuture(this));
            } else {
                final ConnectionAttempt connectionAttempt = new ConnectionAttempt(options, connectionHandler);
                if (connectionAttempt.start(isReconnect)) {
                    lastUsedClientOptions = options;
                } else {
                    LOG.debug("already trying to connect to server ...");
                    connectionHandler.handle(Future.failedFuture(
                            new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "already connecting to server")));
                }
            }
        });
    }

    private void onRemoteClose(final AsyncResult<ProtonConnection> remoteClose) {

        if (remoteClose.failed()) {
            LOG.info("remote server [{}:{}, role: {}] closed connection: {}",
                    connectionFactory.getHost(),
                    connectionFactory.getPort(),
                    connectionFactory.getServerRole(),
                    remoteClose.cause().getMessage());
        } else {
            LOG.info("remote server [{}:{}, role: {}] closed connection",
                    connectionFactory.getHost(),
                    connectionFactory.getPort(),
                    connectionFactory.getServerRole());
        }
        connection.disconnectHandler(null);
        connection.close();
        handleConnectionLoss();
    }

    private void onRemoteDisconnect(final ProtonConnection con) {

        if (con != connection) {
            LOG.warn("cannot handle failure of unknown connection");
        } else {
            LOG.debug("lost connection to server [{}:{}, role: {}]",
                    connectionFactory.getHost(),
                    connectionFactory.getPort(),
                    connectionFactory.getServerRole());
            handleConnectionLoss();
        }
    }

    private void handleConnectionLoss() {

        if (isConnectedInternal()) {
            connection.disconnect();
        }

        notifyDisconnectHandlers();
        clearState();

        if (!shuttingDown.get() && clientConfigProperties.getReconnectAttempts() != 0) {
            connect(lastUsedClientOptions, this::notifyReconnectHandlers, true);
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
     * Resets all connection and link based state.
     */
    private void clearState() {
        setConnection(null, null);
    }

    private void notifyDisconnectHandlers() {
        for (final var listener : disconnectListeners) {
            notifyDisconnectHandler(listener);
        }
        for (final var iter = oneTimeDisconnectListeners.iterator(); iter.hasNext();) {
            notifyDisconnectHandler(iter.next());
            iter.remove();
        }
    }

    private void notifyDisconnectHandler(final DisconnectListener<HonoConnection> listener) {
        try {
            listener.onDisconnect(this);
        } catch (final Exception ex) {
            LOG.warn("error executing disconnectHandler", ex);
        }
    }

    /**
     * Get the maximum delay to be applied before the given reconnect attempt.
     *
     * @param reconnectAttempt The reconnect attempt.
     * @return The delay in milliseconds.
     */
    long getReconnectMaxDelay(final int reconnectAttempt) {
        if (reconnectAttempt <= 0) {
            return 0L;
        } else if (reconnectAttempt <= 31) {
            // determine the max delay for this reconnect attempt as 2^attempt * delayIncrement
            final long currentMaxDelay = (1 << (reconnectAttempt - 1)) * clientConfigProperties.getReconnectDelayIncrement();
            return currentMaxDelay >= 0 ? Math.min(clientConfigProperties.getReconnectMaxDelay(), currentMaxDelay)
                    : clientConfigProperties.getReconnectMaxDelay();
        } else {
            // higher reconnectAttempt values would result in a maxDelay of more than a year, so use the configured maxDelay
            return clientConfigProperties.getReconnectMaxDelay();
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
     * @throws NullPointerException if close handler is {@code null}.
     */
    @Override
    public void closeAndFree(
            final ProtonLink<?> link,
            final Handler<Void> closeHandler) {

        if (context == null) {
            // this means that the connection to the peer is not established (yet) and no (re)connect attempt is in progress
            closeHandler.handle(null);
        } else {
            HonoProtonHelper.closeAndFree(context, link, closeHandler);
        }
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

        if (context == null) {
            // this means that the connection to the peer is not established (yet) and no (re)connect attempt is in progress
            closeHandler.handle(null);
        } else {
            HonoProtonHelper.closeAndFree(context, link, detachTimeOut, closeHandler);
        }
    }

    /**
     * Creates a sender link.
     *
     * @param targetAddress The target address of the link. If the address is {@code null}, the
     *                      sender link will be established to the 'anonymous relay' and each
     *                      message must specify its destination address.
     * @param qos The quality of service to use for the link.
     * @param closeHook The handler to invoke when the link is closed by the peer (may be {@code null}).
     * @return A future for the created link. The future will be completed once the link is open.
     *         The future will fail with a {@link ServiceInvocationException} if the link cannot be opened.
     * @throws NullPointerException if qos is {@code null}.
     */
    @Override
    public Future<ProtonSender> createSender(
            final String targetAddress,
            final ProtonQoS qos,
            final Handler<String> closeHook) {

        Objects.requireNonNull(qos);

        return executeOnContext(result -> checkConnected().compose(v -> {

            if (targetAddress == null && !supportsCapability(AmqpUtils.CAP_ANONYMOUS_RELAY)) {
                // AnonTerm spec requires peer to offer ANONYMOUS-RELAY capability
                // before a client can use anonymous terminus
                return Future.failedFuture(new ServerErrorException(
                        HttpURLConnection.HTTP_NOT_IMPLEMENTED,
                        "server does not support anonymous terminus"));
            }

            final Promise<ProtonSender> senderPromise = Promise.promise();
            final ProtonSender sender = session.createSender(targetAddress);
            sender.setQoS(qos);
            sender.setAutoSettle(true);
            final DisconnectListener<HonoConnection> disconnectBeforeOpenListener = con -> {
                LOG.debug("opening sender [{}] failed: got disconnected", targetAddress);
                senderPromise.tryFail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, MSG_NOT_CONNECTED));
            };
            oneTimeDisconnectListeners.add(disconnectBeforeOpenListener);
            sender.openHandler(senderOpen -> {

                oneTimeDisconnectListeners.remove(disconnectBeforeOpenListener);

                // the result future may have already been completed here in case of a link establishment timeout
                if (senderPromise.future().isComplete()) {
                    LOG.debug("ignoring server response for opening sender [{}]: sender creation already timed out", targetAddress);
                } else if (senderOpen.failed()) {
                    // this means that we have received the peer's attach
                    // and the subsequent detach frame in one TCP read
                    final ErrorCondition error = sender.getRemoteCondition();
                    if (error == null) {
                        LOG.debug("opening sender [{}] failed", targetAddress, senderOpen.cause());
                        senderPromise.tryFail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND,
                                "cannot open sender", senderOpen.cause()));
                    } else {
                        LOG.debug("opening sender [{}] failed: {} - {}", targetAddress, error.getCondition(), error.getDescription());
                        senderPromise.tryFail(ErrorConverter.fromAttachError(error));
                    }

                } else if (HonoProtonHelper.isLinkEstablished(sender)) {

                    LOG.debug("sender open [target: {}, sendQueueFull: {}, remote max-message-size: {}]",
                            targetAddress, sender.sendQueueFull(), sender.getRemoteMaxMessageSize());
                    final long remoteMaxMessageSize = Optional.ofNullable(sender.getRemoteMaxMessageSize())
                            .map(UnsignedLong::longValue)
                            .orElse(0L);
                    if (remoteMaxMessageSize > 0 && remoteMaxMessageSize < clientConfigProperties.getMinMaxMessageSize()) {
                        // peer won't accept our (biggest) messages
                        sender.close();
                        final String msg = String.format(
                                "peer does not support minimum max-message-size [required: %d, supported: %d",
                                clientConfigProperties.getMinMaxMessageSize(), remoteMaxMessageSize);
                        LOG.debug(msg);
                        senderPromise.tryFail(new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED, msg));
                    } else if (sender.getCredit() <= 0) {
                        // wait on credits a little time, if not already given
                        final long waitOnCreditsTimerId = vertx.setTimer(clientConfigProperties.getFlowLatency(),
                                timerID -> {
                                    LOG.debug("sender [target: {}] has {} credits after grace period of {}ms",
                                            targetAddress,
                                            sender.getCredit(), clientConfigProperties.getFlowLatency());
                                    sender.sendQueueDrainHandler(null);
                                    senderPromise.tryComplete(sender);
                                });
                        sender.sendQueueDrainHandler(replenishedSender -> {
                            LOG.debug("sender [target: {}] has received {} initial credits",
                                    targetAddress, replenishedSender.getCredit());
                            if (vertx.cancelTimer(waitOnCreditsTimerId)) {
                                result.tryComplete(replenishedSender);
                                replenishedSender.sendQueueDrainHandler(null);
                            } // otherwise the timer has already completed the future and cleaned up
                              // sendQueueDrainHandler
                        });
                    } else {
                        senderPromise.tryComplete(sender);
                    }

                } else {
                    // this means that the peer did not create a local terminus for the link
                    // and will send a detach frame for closing the link very shortly
                    // see AMQP 1.0 spec section 2.6.3
                    LOG.debug("peer did not create terminus for target [{}] and will detach the link", targetAddress);
                    senderPromise.tryFail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
                }
            });
            HonoProtonHelper.setDetachHandler(sender,
                    remoteDetached -> onRemoteDetach(sender, connection.getRemoteContainer(), false, closeHook));
            HonoProtonHelper.setCloseHandler(sender,
                    remoteClosed -> onRemoteDetach(sender, connection.getRemoteContainer(), true, closeHook));
            sender.open();
            vertx.setTimer(clientConfigProperties.getLinkEstablishmentTimeout(),
                    tid -> {
                        final boolean notOpenedAndNotDisconnectedYet = oneTimeDisconnectListeners.remove(disconnectBeforeOpenListener);
                        if (notOpenedAndNotDisconnectedYet) {
                            onLinkEstablishmentTimeout(sender, clientConfigProperties, senderPromise);
                        }
                    });
            return senderPromise.future();
            }).onComplete(result));
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

        return executeOnContext(result -> checkConnected().compose(v -> {
            final Promise<ProtonReceiver> receiverPromise = Promise.promise();
            final ProtonReceiver receiver = session.createReceiver(sourceAddress);
            if (clientConfigProperties.getMaxMessageSize() > ClientConfigProperties.MAX_MESSAGE_SIZE_UNLIMITED) {
                receiver.setMaxMessageSize(new UnsignedLong(clientConfigProperties.getMaxMessageSize()));
            }
            receiver.setAutoAccept(autoAccept);
            receiver.setQoS(qos);
            receiver.setPrefetch(preFetchSize);
            receiver.handler((delivery, message) -> {
                HonoProtonHelper.onReceivedMessageDeliveryUpdatedFromRemote(delivery,
                        d -> LOG.debug("got unexpected disposition update for received message [remote state: {}]", delivery.getRemoteState()));
                try {
                    messageHandler.handle(delivery, message);
                    if (LOG.isTraceEnabled()) {
                        final int remainingCredits = receiver.getCredit() - receiver.getQueued();
                        LOG.trace("handling message [remotely settled: {}, queued messages: {}, remaining credit: {}]",
                                delivery.remotelySettled(), receiver.getQueued(), remainingCredits);
                    }
                } catch (final Exception ex) {
                    LOG.warn("error handling message", ex);
                    ProtonHelper.released(delivery, true);
                }
            });
            final DisconnectListener<HonoConnection> disconnectBeforeOpenListener = con -> {
                LOG.debug("opening receiver [{}] failed: got disconnected", sourceAddress);
                receiverPromise.tryFail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, MSG_NOT_CONNECTED));
            };
            oneTimeDisconnectListeners.add(disconnectBeforeOpenListener);
            receiver.openHandler(recvOpen -> {

                oneTimeDisconnectListeners.remove(disconnectBeforeOpenListener);

                // the result future may have already been completed here in case of a link establishment timeout
                if (receiverPromise.future().isComplete()) {
                    LOG.debug("ignoring server response for opening receiver [{}]: receiver creation already timed out", sourceAddress);
                } else if (recvOpen.failed()) {
                    // this means that we have received the peer's attach
                    // and the subsequent detach frame in one TCP read
                    final ErrorCondition error = receiver.getRemoteCondition();
                    if (error == null) {
                        LOG.debug("opening receiver [{}] failed", sourceAddress, recvOpen.cause());
                        receiverPromise.tryFail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND,
                                "cannot open receiver", recvOpen.cause()));
                    } else {
                        LOG.debug("opening receiver [{}] failed: {} - {}", sourceAddress, error.getCondition(), error.getDescription());
                        receiverPromise.tryFail(ErrorConverter.fromAttachError(error));
                    }
                } else if (HonoProtonHelper.isLinkEstablished(receiver)) {
                    LOG.debug("receiver open [source: {}]", sourceAddress);
                    receiverPromise.tryComplete(recvOpen.result());
                } else {
                    // this means that the peer did not create a local terminus for the link
                    // and will send a detach frame for closing the link very shortly
                    // see AMQP 1.0 spec section 2.6.3
                    LOG.debug("peer did not create terminus for source [{}] and will detach the link", sourceAddress);
                    receiverPromise.tryFail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
                }
            });
            HonoProtonHelper.setDetachHandler(receiver, remoteDetached -> onRemoteDetach(receiver,
                    connection.getRemoteContainer(), false, remoteCloseHook));
            HonoProtonHelper.setCloseHandler(receiver, remoteClosed -> onRemoteDetach(receiver,
                    connection.getRemoteContainer(), true, remoteCloseHook));
            receiver.open();
            vertx.setTimer(clientConfigProperties.getLinkEstablishmentTimeout(),
                    tid -> {
                        final boolean notOpenedAndNotDisconnectedYet = oneTimeDisconnectListeners.remove(disconnectBeforeOpenListener);
                        if (notOpenedAndNotDisconnectedYet) {
                            onLinkEstablishmentTimeout(receiver, clientConfigProperties, receiverPromise);
                        }
                    });
            return receiverPromise.future();
        }).onComplete(result));
    }

    private void onLinkEstablishmentTimeout(
            final ProtonLink<?> link,
            final ClientConfigProperties clientConfig,
            final Promise<?> result) {

        if (link.isOpen() && !HonoProtonHelper.isLinkEstablished(link)) {
            LOG.info("link establishment [peer: {}] timed out after {}ms",
                    clientConfig.getHost(), clientConfig.getLinkEstablishmentTimeout());
            link.close();
            // don't free the link here - this may result in an inconsistent session state (see PROTON-2177)
            // instead the link will be freed when the detach from the server is received
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
            LOG.debug("{} [{}] detached (with closed={}) by peer [{}]",
                    type, address, closed, remoteContainer);
        } else {
            LOG.debug("{} [{}] detached (with closed={}) by peer [{}]: {} - {}",
                    type, address, closed, remoteContainer, error.getCondition(), error.getDescription());
        }
        link.close();
        if (HonoProtonHelper.isLinkEstablished(link) && closeHook != null) {
            closeHook.handle(address);
        }
    }

    @Override
    public void shutdown() {
        // we don't want to block any event loop thread (even if it's different from the one of the 'context' variable)
        // therefore the latch used for blocking is not used in that case
        final CountDownLatch latch = Context.isOnEventLoopThread() ? null : new CountDownLatch(1);

        shutdown(done -> {
            if (!done.succeeded()) {
                LOG.warn("could not close connection to server", done.cause());
            }
            if (latch != null) {
                latch.countDown();
            }
        });
        if (latch != null) {
            try {
                // use a timeout slightly higher than the one used in closeConnection()
                final int timeout = clientConfigProperties.getCloseConnectionTimeout() + 20;
                if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
                    LOG.warn("shutdown of client timed out after {}ms", timeout);
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void shutdown(final Handler<AsyncResult<Void>> completionHandler) {
        Objects.requireNonNull(completionHandler);
        cancelCurrentConnectionAttempt("client is getting shut down");
        if (shuttingDown.compareAndSet(Boolean.FALSE, Boolean.TRUE)) {
            closeConnection(completionHandler);
        } else {
            completionHandler.handle(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_CONFLICT,
                    "already shutting down")));
        }
    }

    @Override
    public void disconnect() {
        // we don't want to block any event loop thread (even if it's different from the one of the 'context' variable)
        // therefore the latch used for blocking is not used in that case
        final CountDownLatch latch = Context.isOnEventLoopThread() ? null : new CountDownLatch(1);

        disconnect(disconnectResult -> {
            if (disconnectResult.succeeded()) {
                LOG.info("successfully disconnected from the server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
            } else {
                LOG.warn("could not disconnect from the server [{}:{}]", connectionFactory.getHost(), connectionFactory.getPort());
            }
            if (latch != null) {
                latch.countDown();
            }
        });
        if (latch != null) {
            try {
                // use a timeout slightly higher than the one used in closeConnection()
                final int timeout = clientConfigProperties.getCloseConnectionTimeout() + 20;
                if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
                    LOG.warn("Disconnecting from server [{}:{}, role: {}] timed out after {}ms",
                            connectionFactory.getHost(),
                            connectionFactory.getPort(),
                            connectionFactory.getServerRole(),
                            timeout);
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void disconnect(final Handler<AsyncResult<Void>> completionHandler) {
        Objects.requireNonNull(completionHandler);
        cancelCurrentConnectionAttempt("client got disconnected");
        if (disconnecting.compareAndSet(Boolean.FALSE, Boolean.TRUE)) {
            closeConnection(completionHandler);
        } else {
            completionHandler.handle(Future.failedFuture(
                    new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "already disconnecting")));
        }
    }

    private void cancelCurrentConnectionAttempt(final String errorMessage) {
        Optional.ofNullable(currentConnectionAttempt.get()).ifPresent(
                attempt -> attempt.cancel(errorMessage));
    }

    /**
     * Gets the remote container id as advertised by the peer.
     *
     * @return The remote container id or {@code null}.
     */
    @Override
    public String getRemoteContainerId() {
        if (!isConnectedInternal()) {
            return null;
        }
        return connection.getRemoteContainer();
    }

    @Override
    public String getContainerId() {
        return containerId;
    }

    //-----------------------------------< private methods >---

    private void closeConnection(final Handler<AsyncResult<Void>> completionHandler) {

        final Handler<AsyncResult<Void>> handler = attempt -> {
            disconnecting.compareAndSet(Boolean.TRUE, Boolean.FALSE);
            completionHandler.handle(attempt);
        };

        synchronized (connectionLock) {
            if (isConnectedInternal()) {

                final ProtonConnection connectionToClose = connection;
                LOG.info("closing connection to container [{}] at [{}:{}, role: {}] ...",
                        connectionToClose.getRemoteContainer(),
                        connectionFactory.getHost(),
                        connectionFactory.getPort(),
                        connectionFactory.getServerRole());
                HonoProtonHelper.closeConnection(
                        connectionToClose,
                        clientConfigProperties.getCloseConnectionTimeout(),
                        context)
                    .onSuccess(con -> LOG.info("closed connection to container [{}] at [{}:{}, role: {}]",
                                    connectionToClose.getRemoteContainer(),
                                    connectionFactory.getHost(),
                                    connectionFactory.getPort(),
                                    connectionFactory.getServerRole()))
                    .onFailure(t -> LOG.info("closed connection to container [{}] at [{}:{}, role: {}]",
                                    connectionToClose.getRemoteContainer(),
                                    connectionFactory.getHost(),
                                    connectionFactory.getPort(),
                                    connectionFactory.getServerRole(),
                                    t))
                    .map((Void) null)
                    .onComplete(result -> {
                        notifyDisconnectHandlers();
                        clearState();
                    })
                    .onComplete(completionHandler);

            } else {
                LOG.info("connection to server [{}:{}, role: {}] already closed",
                        connectionFactory.getHost(),
                        connectionFactory.getPort(),
                        connectionFactory.getServerRole());
                handler.handle(Future.succeededFuture());
            }
        }
    }

    /**
     * Encapsulates a connection attempt (with possible retries).
     */
    private class ConnectionAttempt {

        private final ProtonClientOptions clientOptions;
        private final Handler<AsyncResult<HonoConnection>> connectionHandler;
        private final AtomicInteger connectAttempts = new AtomicInteger(0);
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private Long reconnectTimerId;

        ConnectionAttempt(final ProtonClientOptions clientOptions, final Handler<AsyncResult<HonoConnection>> connectionHandler) {
            this.clientOptions = clientOptions;

            this.connectionHandler = ar -> {
                if (ar.failed()) {
                    clearState();
                }
                currentConnectionAttempt.compareAndSet(this, null);
                connectionHandler.handle(ar);
                deferredConnectionCheckHandler.setConnectionAttemptFinished(ar);
            };
        }

        /**
         * Starts the connection attempt.
         * Must be called on the vert.x context created for the connection attempt.
         *
         * @param isReconnect if {@code true}, the connection attempt is started after the configured minimum
         *            reconnect delay.
         * @return {@code true} if the attempt was started, {@code false} if there already is an ongoing attempt.
         */
        public boolean start(final boolean isReconnect) {
            if (!currentConnectionAttempt.compareAndSet(null, this)) {
                // there already is another ongoing attempt
                return false;
            }
            deferredConnectionCheckHandler.setConnectionAttemptInProgress();
            if (isReconnect) {
                reconnect(null);
            } else {
                connect();
            }
            return true;
        }

        /**
         * Cancels the connection attempt.
         *
         * @param errorMessage The error message with which to fail the connection handler.
         */
        public void cancel(final String errorMessage) {
            if (currentConnectionAttempt.get() != this || !cancelled.compareAndSet(false, true)) {
                // attempt already finished or cancelled
                return;
            }
            final boolean timerCancelled = Optional.ofNullable(reconnectTimerId)
                    .map(vertx::cancelTimer).orElse(false);
            LOG.debug("cancelled {} connection attempt [#{}] to server [{}:{}, role: {}]",
                    timerCancelled ? "upcoming" : "ongoing",
                    connectAttempts.get() + 1,
                    connectionFactory.getHost(),
                    connectionFactory.getPort(),
                    connectionFactory.getServerRole());
            final ClientErrorException ex = new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, errorMessage);
            connectionHandler.handle(Future.failedFuture(ex));
        }

        private void connect() {
            if (cancelled.get()) {
                return;
            }
            LOG.debug("starting attempt [#{}] to connect to server [{}:{}, role: {}]",
                    connectAttempts.get() + 1,
                    connectionFactory.getHost(),
                    connectionFactory.getPort(),
                    connectionFactory.getServerRole());

            connectionFactory.connect(
                    clientOptions,
                    null,
                    null,
                    containerId,
                    HonoConnectionImpl.this::onRemoteClose,
                    HonoConnectionImpl.this::onRemoteDisconnect)
                .onFailure(this::reconnect)
                .onSuccess(newConnection -> {
                    if (cancelled.get()) {
                        LOG.debug("attempt [#{}]: connected but will directly be closed because attempt got cancelled; server [{}:{}, role: {}]",
                                connectAttempts.get() + 1,
                                connectionFactory.getHost(),
                                connectionFactory.getPort(),
                                connectionFactory.getServerRole());
                        newConnection.closeHandler(null);
                        newConnection.disconnectHandler(null);
                        newConnection.close();
                    } else {
                        LOG.debug("attempt [#{}]: connected to server [{}:{}, role: {}]; remote container: {}",
                                connectAttempts.get() + 1,
                                connectionFactory.getHost(),
                                connectionFactory.getPort(),
                                connectionFactory.getServerRole(),
                                newConnection.getRemoteContainer());
                        final ProtonSession defaultSession = createDefaultSession(newConnection);
                        setConnection(newConnection, defaultSession);
                        connectionHandler.handle(Future.succeededFuture(HonoConnectionImpl.this));
                    }
                });
        }

        private ProtonSession createDefaultSession(final ProtonConnection connection) {

            if (connection == null) {
                throw new IllegalStateException("no connection to create session for");
            } else {
                LOG.debug("establishing AMQP session with server [{}:{}, role: {}]",
                        connectionFactory.getHost(),
                        connectionFactory.getPort(),
                        connectionFactory.getServerRole());
                final ProtonSession newSession = connection.createSession();
                newSession.closeHandler(remoteClose -> {
                    final StringBuilder msgBuilder = new StringBuilder("the connection's session closed unexpectedly");
                    Optional.ofNullable(newSession.getRemoteCondition())
                        .ifPresent(error -> msgBuilder.append(String.format(
                                " [condition: %s, description: %s]",
                                error.getCondition(), error.getDescription())));
                    newSession.close();
                    onRemoteClose(Future.failedFuture(msgBuilder.toString()));
                });
                newSession.setIncomingCapacity(clientConfigProperties.getMaxSessionWindowSize());
                newSession.open();
                return newSession;
            }
        }

        @SuppressFBWarnings(
                value = "PREDICTABLE_RANDOM",
                justification = """
                        The values returned by the ThreadLocalRandom are only used for calculating a
                        random amount of time to wait before trying to reconnect.
                        """)
        private void reconnect(final Throwable connectionFailureCause) {
            if (cancelled.get()) {
                return;
            }
            if (connectionFailureCause != null) {
                logConnectionError(connectionFailureCause);
            }
            if (vertx instanceof VertxInternal vertxInternal && vertxInternal.closeFuture().isClosed()) {
                LOG.info("stopping attempts to re-connect to server [{}:{}, role: {}], vertx instance is closed",
                        connectionFactory.getHost(),
                        connectionFactory.getPort(),
                        connectionFactory.getServerRole());
                connectionHandler.handle(Future.failedFuture(mapConnectionAttemptFailure(connectionFailureCause)));
            } else if (clientConfigProperties.getReconnectAttempts() - connectAttempts.get() == 0) {
                LOG.info("max number of attempts [{}] to re-connect to server [{}:{}, role: {}] have been made, giving up",
                        clientConfigProperties.getReconnectAttempts(),
                        connectionFactory.getHost(),
                        connectionFactory.getPort(),
                        connectionFactory.getServerRole());
                connectionHandler.handle(Future.failedFuture(mapConnectionAttemptFailure(connectionFailureCause)));
            } else {
                final int reconnectAttempt = connectAttempts.getAndIncrement();
                // apply exponential backoff with jitter
                final long reconnectMaxDelay = getReconnectMaxDelay(reconnectAttempt);
                // let the actual reconnect delay be a random between the minDelay and the current maxDelay
                final long reconnectDelay = reconnectMaxDelay > clientConfigProperties.getReconnectMinDelay()
                        ? ThreadLocalRandom.current().nextLong(clientConfigProperties.getReconnectMinDelay(), reconnectMaxDelay)
                        : clientConfigProperties.getReconnectMinDelay();
                if (reconnectDelay > 0) {
                    LOG.trace("scheduling new connection attempt in {}ms ...", reconnectDelay);
                    reconnectTimerId = vertx.setTimer(reconnectDelay, tid -> {
                        reconnectTimerId = null;
                        connect();
                    });
                } else {
                    connect();
                }
            }
        }

        private ServiceInvocationException mapConnectionAttemptFailure(final Throwable connectionFailureCause) {
            final ServiceInvocationException serviceInvocationException;
            if (connectionFailureCause == null) {
                serviceInvocationException = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                        "failed to connect");
            } else if (connectionFailureCause instanceof AuthenticationException) {
                // wrong credentials?
                serviceInvocationException = new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED,
                        "failed to authenticate with server");
            } else if (connectionFailureCause instanceof MechanismMismatchException) {
                serviceInvocationException = new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED,
                        "no suitable SASL mechanism found for authentication with server");
            } else if (connectionFailureCause instanceof SSLException) {
                serviceInvocationException = new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                        "TLS handshake with server failed: " + connectionFailureCause.getMessage(), connectionFailureCause);
            } else {
                serviceInvocationException = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                        "failed to connect", connectionFailureCause);
            }
            return serviceInvocationException;
        }

        private void logConnectionError(final Throwable connectionFailureCause) {
            if (isNoteworthyConnectionError(connectionFailureCause)) {
                LOG.warn("attempt [#{}] to connect to server [{}:{}, role: {}] failed",
                        connectAttempts.get() + 1,
                        clientConfigProperties.getHost(),
                        clientConfigProperties.getPort(),
                        connectionFactory.getServerRole(),
                        connectionFailureCause);
            } else {
                LOG.debug("attempt [#{}] to connect to server [{}:{}, role: {}] failed",
                        connectAttempts.get() + 1,
                        clientConfigProperties.getHost(),
                        clientConfigProperties.getPort(),
                        connectionFactory.getServerRole(),
                        connectionFailureCause);
            }
        }

        private boolean isNoteworthyConnectionError(final Throwable connectionFailureCause) {
            return connectionFailureCause instanceof SSLException ||
                    connectionFailureCause instanceof AuthenticationException ||
                    connectionFailureCause instanceof MechanismMismatchException ||
                    (connectionFailureCause instanceof SaslSystemException saslSystemEx && saslSystemEx.isPermanent());
        }
    }
}
