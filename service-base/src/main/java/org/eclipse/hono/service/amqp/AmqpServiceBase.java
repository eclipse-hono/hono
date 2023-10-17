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

package org.eclipse.hono.service.amqp;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.Source;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoProtonHelper;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.AbstractServiceBase;
import org.eclipse.hono.service.auth.AuthorizationService;
import org.eclipse.hono.service.auth.ClaimsBasedAuthorizationService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonServer;
import io.vertx.proton.ProtonServerOptions;
import io.vertx.proton.ProtonSession;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;

/**
 * A base class for implementing services using AMQP 1.0 as the transport protocol.
 * <p>
 * This class provides support for implementing an AMQP 1.0 container hosting arbitrary
 * API {@link AmqpEndpoint}s. An endpoint can be used to handle messages being sent to and/or
 * received from an AMQP <em>node</em> represented by an address prefix.
 *
 * @param <T> The type of configuration properties this service uses.
 */
public abstract class AmqpServiceBase<T extends ServiceConfigProperties> extends AbstractServiceBase<T> {

    private final Map<String, AmqpEndpoint> endpoints = new HashMap<>();

    private ProtonServer server;
    private ProtonServer insecureServer;
    private ProtonSaslAuthenticatorFactory saslAuthenticatorFactory;
    private AuthorizationService authorizationService;

    /**
     * Gets the name of the service, that may be used for the container name on amqp connections e.g.
     * @return The name of the service.
     */
    protected abstract String getServiceName();

    /**
     * Gets the default port number of the secure AMQP port.
     *
     * @return {@link Constants#PORT_AMQPS}
     */
    @Override
    public int getPortDefaultValue() {
        return Constants.PORT_AMQPS;
    }

    /**
     * Gets the default port number of the non-secure AMQP port.
     *
     * @return {@link Constants#PORT_AMQP}
     */
    @Override
    public int getInsecurePortDefaultValue() {
        return Constants.PORT_AMQP;
    }

    /**
     * Adds multiple endpoints to this server.
     * <p>
     * Subclasses may override this method to only include {@link AmqpEndpoint}
     * instances with a certain qualifier.
     *
     * @param definedEndpoints The endpoints.
     */
    public void addEndpoints(final List<AmqpEndpoint> definedEndpoints) {
        Objects.requireNonNull(definedEndpoints);
        for (final AmqpEndpoint ep : definedEndpoints) {
            addEndpoint(ep);
        }
    }

    /**
     * Adds an endpoint to this server.
     *
     * @param ep The endpoint.
     */
    public final void addEndpoint(final AmqpEndpoint ep) {
        if (endpoints.putIfAbsent(ep.getName(), ep) != null) {
            log.warn("multiple endpoints defined with name [{}]", ep.getName());
        } else {
            log.debug("registering endpoint [{}]", ep.getName());
        }
    }

    /**
     * Gets the endpoint registered for handling a specific target address.
     *
     * @param targetAddress The address.
     * @return The endpoint for handling the address or {@code null} if no endpoint is registered
     *         for the target address.
     */
    protected final AmqpEndpoint getEndpoint(final ResourceIdentifier targetAddress) {
        return getEndpoint(targetAddress.getEndpoint());
    }

    /**
     * Gets the endpoint registered for a given name.
     *
     * @param endpointName The name.
     * @return The endpoint registered under the given name or {@code null} if no endpoint has been registered
     *         under the name.
     */
    protected final AmqpEndpoint getEndpoint(final String endpointName) {
        return endpoints.get(endpointName);
    }

    /**
     * Iterates over the endpoints registered with this service.
     *
     * @return The endpoints.
     */
    protected final Iterable<AmqpEndpoint> endpoints() {
        return endpoints.values();
    }

    /**
     * Sets the factory to use for creating objects performing SASL based authentication of clients.
     *
     * @param factory The factory.
     * @throws NullPointerException if factory is {@code null}.
     */
    public void setSaslAuthenticatorFactory(final ProtonSaslAuthenticatorFactory factory) {
        this.saslAuthenticatorFactory = Objects.requireNonNull(factory);
    }

    /**
     * Sets the object to use for authorizing access to resources and operations.
     *
     * @param authService The authorization service to use.
     * @throws NullPointerException if the service is {@code null}.
     */
    public final void setAuthorizationService(final AuthorizationService authService) {
        this.authorizationService = authService;
    }

    /**
     * Gets the object used for authorizing access to resources and operations.
     *
     * @return The authorization service to use.
     */
    protected final AuthorizationService getAuthorizationService() {
        return authorizationService;
    }

    @Override
    public Future<Void> startInternal() {

        if (authorizationService == null) {
            authorizationService = new ClaimsBasedAuthorizationService();
        }
        return preStartServers()
            .compose(s -> checkPortConfiguration())
            .compose(s -> startEndpoints())
            .compose(s -> startSecureServer())
            .compose(s -> startInsecureServer());
    }

    /**
     * Closes an expired client connection.
     * <p>
     * A connection is considered expired if the {@link HonoUser#isExpired()} method
     * of the user principal attached to the connection returns {@code true}.
     *
     * @param con The client connection.
     */
    protected final void closeExpiredConnection(final ProtonConnection con) {

        if (!con.isDisconnected()) {
            final HonoUser clientPrincipal = AmqpUtils.getClientPrincipal(con);
            if (clientPrincipal != null) {
                log.debug("client's [{}] access token has expired, closing connection", clientPrincipal.getName());
                con.disconnectHandler(null);
                con.closeHandler(null);
                con.setCondition(ProtonHelper.condition(AmqpError.UNAUTHORIZED_ACCESS, "access token expired"));
                con.close();
                con.disconnect();
                publishConnectionClosedEvent(con);
            }
        }
    }

    /**
     * Invoked before binding listeners to the configured socket addresses.
     * <p>
     * Subclasses may override this method to do any kind of initialization work.
     *
     * @return A future indicating the outcome of the operation. The listeners will not
     *         be bound if the returned future fails.
     */
    protected Future<Void> preStartServers() {
        return Future.succeededFuture();
    }

    private Future<Void> startEndpoints() {

        final
        List<Future<Void>> endpointFutures = new ArrayList<>(endpoints.size());
        for (final AmqpEndpoint ep : endpoints.values()) {
            log.info("starting endpoint [name: {}, class: {}]", ep.getName(), ep.getClass().getName());
            endpointFutures.add(ep.start());
        }
        return Future.all(endpointFutures)
                .map(ok -> (Void) null)
                .recover(Future::failedFuture);
    }

    private Future<Void> stopEndpoints() {

        final
        List<Future<Void>> endpointFutures = new ArrayList<>(endpoints.size());
        for (final AmqpEndpoint ep : endpoints.values()) {
            log.info("stopping endpoint [name: {}, class: {}]", ep.getName(), ep.getClass().getName());
            endpointFutures.add(ep.stop());
        }
        return Future.all(endpointFutures)
                .map(ok -> (Void) null)
                .recover(Future::failedFuture);
    }

    private Future<Void> startInsecureServer() {

        if (isInsecurePortEnabled()) {
            final int insecurePort = determineInsecurePort();
            final Promise<Void> result = Promise.promise();
            final ProtonServerOptions options = createInsecureServerOptions();
            insecureServer = createProtonServer(options)
                    .connectHandler(this::onRemoteConnectionOpenInsecurePort)
                    .listen(insecurePort, getConfig().getInsecurePortBindAddress(), bindAttempt -> {
                        if (bindAttempt.succeeded()) {
                            if (getInsecurePort() == getInsecurePortDefaultValue()) {
                                log.info("server listens on standard insecure port [{}:{}]", getInsecurePortBindAddress(), getInsecurePort());
                            } else {
                                log.warn("server listens on non-standard insecure port [{}:{}], default is {}", getInsecurePortBindAddress(),
                                        getInsecurePort(), getInsecurePortDefaultValue());
                            }
                            result.complete();
                        } else {
                            log.error("cannot bind to insecure port", bindAttempt.cause());
                            result.fail(bindAttempt.cause());
                        }
                    });
            return result.future();
        } else {
            log.info("insecure port is not enabled");
            return Future.succeededFuture();
        }
    }

    private Future<Void> startSecureServer() {

        if (isSecurePortEnabled()) {
            final int securePort = determineSecurePort();
            final Promise<Void> result = Promise.promise();
            final ProtonServerOptions options = createServerOptions();
            server = createProtonServer(options)
                    .connectHandler(this::onRemoteConnectionOpen)
                    .listen(securePort, getConfig().getBindAddress(), bindAttempt -> {
                        if (bindAttempt.succeeded()) {
                            if (getPort() == getPortDefaultValue()) {
                                log.info("server listens on standard secure port [{}:{}]", getBindAddress(), getPort());
                            } else {
                                log.warn("server listens on non-standard secure port [{}:{}], default is {}", getBindAddress(),
                                        getPort(), getPortDefaultValue());
                            }
                            result.complete();
                        } else {
                            log.error("cannot bind to secure port", bindAttempt.cause());
                            result.fail(bindAttempt.cause());
                        }
                    });
            return result.future();
        } else {
            log.info("secure port is not enabled");
            return Future.succeededFuture();
        }
    }

    private ProtonServer createProtonServer(final ProtonServerOptions options) {
        return ProtonServer.create(vertx, options)
                .saslAuthenticatorFactory(saslAuthenticatorFactory);
    }

    /**
     * Invoked during start up to create options for the proton server instance binding to the secure port.
     * <p>
     * This default implementation creates options using {@link #createInsecureServerOptions()}
     * and sets the {@linkplain ServiceConfigProperties#getKeyCertOptions() server's private key and certificate}
     * and {@linkplain ServiceConfigProperties#getTrustOptions() trust options}.
     * <p>
     * Subclasses may override this method to set custom options.
     *
     * @return The options.
     */
    protected ProtonServerOptions createServerOptions() {
        final ProtonServerOptions options = createInsecureServerOptions();
        addTlsKeyCertOptions(options);
        addTlsTrustOptions(options);
        return options;
    }

    /**
     * Invoked during start up to create options for the proton server instances.
     * <ul>
     * <li>The <em>heartbeat</em> interval is set to 10 seconds, i.e. the server will close the
     * connection to the client if it does not receive any frames for 20 seconds.</li>
     * <li>The maximum frame size is set to 16kb.</li>
     * </ul>
     * <p>
     * Subclasses may override this method to set custom options.
     *
     * @return The options.
     */
    protected ProtonServerOptions createInsecureServerOptions() {

        final ProtonServerOptions options = new ProtonServerOptions();
        options.setHeartbeat(10000); // close idle connections after 20 seconds of inactivity
        options.setMaxFrameSize(16 * 1024); // 16kb
        options.setLogActivity(getConfig().isNetworkDebugLoggingEnabled());

        return options;
    }

    @Override
    public final Future<Void> stopInternal() {

        return preShutdown()
                .compose(s -> Future.all(stopServer(), stopInsecureServer()))
                .compose(s -> stopEndpoints())
                .compose(s -> postShutdown());
    }

    private Future<Void> stopServer() {

        final Promise<Void> secureTracker = Promise.promise();

        if (server != null) {
            log.info("stopping secure AMQP server [{}:{}]", getBindAddress(), getActualPort());
            server.close(secureTracker);
        } else {
            secureTracker.complete();
        }
        return secureTracker.future();
    }

    private Future<Void> stopInsecureServer() {

        final Promise<Void> insecureTracker = Promise.promise();

        if (insecureServer != null) {
            log.info("stopping insecure AMQP server [{}:{}]", getInsecurePortBindAddress(), getActualInsecurePort());
            insecureServer.close(insecureTracker);
        } else {
            insecureTracker.complete();
        }
        return insecureTracker.future();
    }

    /**
     * Invoked before the servers are shut down.
     * <p>
     * Subclasses may override this method.
     *
     * @return A future indicating the outcome of the operation.
     */
    protected Future<Void> preShutdown() {
        return Future.succeededFuture();
    }

    /**
     * Invoked after the servers have been shutdown successfully.
     * <p>
     * May be overridden by sub-classes.
     *
     * @return A future that has to be completed when this operation is finished.
     */
    protected Future<Void> postShutdown() {
        return Future.succeededFuture();
    }

    @Override
    protected final int getActualPort() {
        return server != null ? server.actualPort() : Constants.PORT_UNCONFIGURED;
    }

    @Override
    protected final int getActualInsecurePort() {
        return insecureServer != null ? insecureServer.actualPort() : Constants.PORT_UNCONFIGURED;
    }

    /**
     * Invoked when an AMQP <em>open</em> frame is received on the secure port.
     * <p>
     * This method
     * <ol>
     * <li>sets the container name</li>
     * <li>invokes {@link #setRemoteConnectionOpenHandler(ProtonConnection)}</li>
     * </ol>
     * <p>
     * Subclasses may override this method to set custom handlers.
     *
     * @param connection The AMQP connection that the frame is supposed to establish.
     */
    protected void onRemoteConnectionOpen(final ProtonConnection connection) {
        connection.setContainer(String.format("%s-%s:%d", getServiceName(), getBindAddress(), getPort()));
        setRemoteConnectionOpenHandler(connection);
    }

    /**
     * Invoked when an AMQP <em>open</em> frame is received on the insecure port.
     * <p>
     * This method
     * <ol>
     * <li>sets the container name</li>
     * <li>invokes {@link #setRemoteConnectionOpenHandler(ProtonConnection)}</li>
     * </ol>
     * <p>
     * Subclasses may override this method to set custom handlers.
     *
     * @param connection The AMQP connection that the frame is supposed to establish.
     * @throws NullPointerException if connection is {@code null}.
     */
    protected void onRemoteConnectionOpenInsecurePort(final ProtonConnection connection) {
        connection.setContainer(String.format("%s-%s:%d", getServiceName(), getInsecurePortBindAddress(), getInsecurePort()));
        setRemoteConnectionOpenHandler(connection);
    }

    /**
     * Closes a link for an unknown target address.
     * <p>
     * The link is closed with AMQP error code <em>amqp:not-found</em>.
     *
     * @param con The connection that the link belongs to.
     * @param link The link.
     * @param address The unknown target address.
     */
    protected final void handleUnknownEndpoint(final ProtonConnection con, final ProtonLink<?> link, final String address) {
        log.info("client [container: {}] wants to establish link for unknown endpoint [address: {}]",
                con.getRemoteContainer(), address);
        link.setCondition(
                ProtonHelper.condition(
                        AmqpError.NOT_FOUND,
                        String.format("no endpoint registered for address %s", address)));
        link.close();
    }

    /**
     * Handles a request from a client to establish a link for sending messages to this server.
     * The already established connection must have an authenticated user as principal for doing the authorization check.
     *
     * @param con the connection to the client.
     * @param receiver the receiver created for the link.
     */
    protected void handleReceiverOpen(final ProtonConnection con, final ProtonReceiver receiver) {
        if (Strings.isNullOrEmpty(receiver.getRemoteTarget().getAddress())) {
            log.debug("client [container: {}] wants to open an anonymous link for sending messages to arbitrary addresses, closing link ...",
                    con.getRemoteContainer());
            receiver.setCondition(ProtonHelper.condition(AmqpError.NOT_ALLOWED, "anonymous relay not supported"));
            receiver.close();
        } else if (!ResourceIdentifier.isValid(receiver.getRemoteTarget().getAddress())) {
            handleUnknownEndpoint(con, receiver, receiver.getRemoteTarget().getAddress());
        } else {
            log.debug("client [container: {}] wants to open a link [address: {}] for sending messages",
                    con.getRemoteContainer(), receiver.getRemoteTarget());
            final ResourceIdentifier targetResource = ResourceIdentifier
                    .fromString(receiver.getRemoteTarget().getAddress());
            final AmqpEndpoint endpoint = getEndpoint(targetResource);
            if (endpoint == null) {
                handleUnknownEndpoint(con, receiver, targetResource.toString());
            } else {
                final HonoUser user = AmqpUtils.getClientPrincipal(con);
                getAuthorizationService().isAuthorized(user, targetResource, Activity.WRITE).onComplete(authAttempt -> {
                    if (authAttempt.succeeded() && authAttempt.result()) {
                        receiver.setSource(receiver.getRemoteSource());
                        receiver.setTarget(receiver.getRemoteTarget());
                        endpoint.onLinkAttach(con, receiver, targetResource);
                    } else {
                        log.debug("subject [{}] is not authorized to WRITE to [{}]", user.getName(), targetResource);
                        receiver.setCondition(ProtonHelper.condition(AmqpError.UNAUTHORIZED_ACCESS.toString(), "unauthorized"));
                        receiver.close();
                    }
                });
            }
        }
    }

    /**
     * Handles a request from a client to establish a link for receiving messages from this server.
     *
     * @param con the connection to the client.
     * @param sender the sender created for the link.
     */
    protected void handleSenderOpen(final ProtonConnection con, final ProtonSender sender) {
        final Source remoteSource = sender.getRemoteSource();
        log.debug("client [container: {}] wants to open a link [address: {}] for receiving messages",
                con.getRemoteContainer(), remoteSource);
        if (!ResourceIdentifier.isValid(remoteSource.getAddress())) {
            handleUnknownEndpoint(con, sender, remoteSource.getAddress());
            return;
        }
        final ResourceIdentifier targetResource = ResourceIdentifier.fromString(remoteSource.getAddress());
        final AmqpEndpoint endpoint = getEndpoint(targetResource);
        if (endpoint == null) {
            handleUnknownEndpoint(con, sender, targetResource.toString());
        } else {
            final HonoUser user = AmqpUtils.getClientPrincipal(con);
            getAuthorizationService().isAuthorized(user, targetResource, Activity.READ).onComplete(authAttempt -> {
                if (authAttempt.succeeded() && authAttempt.result()) {
                    sender.setSource(sender.getRemoteSource());
                    sender.setTarget(sender.getRemoteTarget());
                    endpoint.onLinkAttach(con, sender, targetResource);
                } else {
                    log.debug("subject [{}] is not authorized to READ from [{}]", user.getName(), targetResource);
                    sender.setCondition(ProtonHelper.condition(AmqpError.UNAUTHORIZED_ACCESS.toString(), "unauthorized"));
                    sender.close();
                }
            });
        }
    }

    /**
     * Sets default handlers on a connection that has been opened
     * by a peer.
     * <p>
     * This method registers the following handlers
     * <ul>
     * <li>sessionOpenHandler - {@link #handleSessionOpen(ProtonConnection, ProtonSession)}</li>
     * <li>receiverOpenHandler - {@link #handleReceiverOpen(ProtonConnection, ProtonReceiver)}</li>
     * <li>senderOpenHandler - {@link #handleSenderOpen(ProtonConnection, ProtonSender)}</li>
     * <li>disconnectHandler - {@link #handleRemoteDisconnect(ProtonConnection)}</li>
     * <li>closeHandler - {@link #handleRemoteConnectionClose(ProtonConnection, AsyncResult)}</li>
     * <li>openHandler - {@link #processRemoteOpen(ProtonConnection)}</li>
     * </ul>
     * <p>
     * Subclasses should override this method in order to register service
     * specific handlers and/or to prevent registration of default handlers.
     *
     * @param connection The connection.
     */
    protected void setRemoteConnectionOpenHandler(final ProtonConnection connection) {

        log.debug("received connection request from client");
        connection.sessionOpenHandler(session -> {
            HonoProtonHelper.setDefaultCloseHandler(session);
            handleSessionOpen(connection, session);
        });
        connection.receiverOpenHandler(receiver -> {
            HonoProtonHelper.setDefaultCloseHandler(receiver);
            handleReceiverOpen(connection, receiver);
        });
        connection.senderOpenHandler(sender -> {
            HonoProtonHelper.setDefaultCloseHandler(sender);
            handleSenderOpen(connection, sender);
        });
        connection.disconnectHandler(this::handleRemoteDisconnect);
        connection.closeHandler(remoteClose -> handleRemoteConnectionClose(connection, remoteClose));
        connection.openHandler(remoteOpen -> {
            if (remoteOpen.failed()) {
                log.debug("ignoring peer's open frame containing error", remoteOpen.cause());
            } else {
                processRemoteOpen(remoteOpen.result());
            }
        });
    }

    /**
     * Processes a peer's AMQP <em>open</em> frame.
     * <p>
     * This default implementation
     * <ol>
     * <li>invokes {@link #processDesiredCapabilities(ProtonConnection, Symbol[])}</li>
     * <li>sets a timer that closes the connection once the client's token
     * has expired</li>
     * <li>sends the AMQP <em>open</em> frame to the peer</li>
     * </ol>
     *
     * @param connection The connection to open.
     */
    protected void processRemoteOpen(final ProtonConnection connection) {

        log.debug("processing open frame from client container [{}]", connection.getRemoteContainer());
        final HonoUser clientPrincipal = AmqpUtils.getClientPrincipal(connection);
        processDesiredCapabilities(connection, connection.getRemoteDesiredCapabilities());
        final Duration delay = Duration.between(Instant.now(), clientPrincipal.getExpirationTime());
        final WeakReference<ProtonConnection> conRef = new WeakReference<>(connection);
        vertx.setTimer(delay.toMillis(), timerId -> {
            if (conRef.get() != null) {
                closeExpiredConnection(conRef.get());
            }
        });
        connection.open();
        log.info("client connected [container: {}, user: {}, token valid until: {}]",
                connection.getRemoteContainer(), clientPrincipal.getName(), clientPrincipal.getExpirationTime().toString());
    }

    /**
     * Processes the capabilities desired by a client in its
     * AMQP <em>open</em> frame.
     * <p>
     * This default implementation does nothing.
     *
     * @param connection The connection being opened by the client.
     * @param desiredCapabilities The capabilities.
     */
    protected void processDesiredCapabilities(
            final ProtonConnection connection,
            final Symbol[] desiredCapabilities) {
    }

    /**
     * Invoked when a client initiates a session (which is then opened in this method).
     * <p>
     * Subclasses should override this method if other behaviour shall be implemented on session open.
     *
     * @param con The connection of the session.
     * @param session The session that is initiated.
     */
    protected void handleSessionOpen(final ProtonConnection con, final ProtonSession session) {
        log.debug("opening new session with client [container: {}]", con.getRemoteContainer());
        session.open();
    }

    /**
     * Is called whenever a proton connection was closed.
     * <p>
     * Subclasses may override this method to publish this as an event on the vertx bus if desired. If they choose to
     * override, they must however call this super method as this method forwards the call to
     * {@link AmqpEndpoint#onConnectionClosed(ProtonConnection)} of all endpoints managed by this instance.
     *
     * @param con The connection that was closed.
     */
    protected void publishConnectionClosedEvent(final ProtonConnection con) {
        endpoints.values().forEach(endpoint -> endpoint.onConnectionClosed(con));
    }

    /**
     * Invoked when a client closes the connection with this server.
     * <p>
     * This implementation closes and disconnects the connection.
     *
     * @param con The connection to close.
     * @param res The client's close frame.
     */
    protected void handleRemoteConnectionClose(final ProtonConnection con, final AsyncResult<ProtonConnection> res) {
        if (res.succeeded()) {
            log.debug("client [container: {}] closed connection", con.getRemoteContainer());
        } else {
            log.debug("client [container: {}] closed connection with error", con.getRemoteContainer(), res.cause());
        }
        con.close();
        con.disconnect();
        publishConnectionClosedEvent(con);
    }

    /**
     * Invoked when the client's transport connection is disconnected from this server.
     *
     * @param con The connection that was disconnected.
     */
    protected void handleRemoteDisconnect(final ProtonConnection con) {
        log.debug("client [container: {}] disconnected", con.getRemoteContainer());
        con.disconnect();
        publishConnectionClosedEvent(con);
    }

    /**
     * Registers this service's endpoints' readiness checks.
     * <p>
     * This default implementation invokes {@link AmqpEndpoint#registerReadinessChecks(HealthCheckHandler)}
     * for all registered endpoints.
     * <p>
     * Subclasses should override this method to register more specific checks.
     *
     * @param handler The health check handler to register the checks with.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {

        for (final AmqpEndpoint ep : endpoints()) {
            ep.registerReadinessChecks(handler);
        }
    }

    /**
     * Registers this service's endpoints' liveness checks.
     * <p>
     * This default implementation invokes {@link AmqpEndpoint#registerLivenessChecks(HealthCheckHandler)}
     * for all registered endpoints.
     * <p>
     * Subclasses should override this method to register more specific checks.
     *
     * @param handler The health check handler to register the checks with.
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        for (final AmqpEndpoint ep : endpoints()) {
            ep.registerLivenessChecks(handler);
        }
    }
}
