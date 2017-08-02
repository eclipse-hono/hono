/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.amqp;

import java.util.*;

import io.vertx.core.AsyncResult;
import io.vertx.proton.*;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.Source;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.AbstractServiceBase;
import org.eclipse.hono.service.auth.AuthorizationService;
import org.eclipse.hono.service.auth.ClaimsBasedAuthorizationService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;

/**
 * A base class for implementing services using AMQP 1.0 as the transport protocol.
 * <p>
 * This class provides support for implementing an AMQP 1.0 container hosting arbitrary
 * API {@link Endpoint}s. An endpoint can be used to handle messages being sent to and/or
 * received from an AMQP <em>node</em> represented by an address prefix.
 * 
 * @param <T> The type of configuration properties this service uses.
 */
public abstract class AmqpServiceBase<T extends ServiceConfigProperties> extends AbstractServiceBase<T> {

    // <name, node implementation>
    private final Map<String, Endpoint> endpoints = new HashMap<>();
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
     *
     * @param definedEndpoints The endpoints.
     */
    @Autowired(required = false)
    public final void addEndpoints(final List<Endpoint> definedEndpoints) {
        Objects.requireNonNull(definedEndpoints);
        for (Endpoint ep : definedEndpoints) {
            addEndpoint(ep);
        }
    }

    /**
     * Adds an endpoint to this server.
     *
     * @param ep The endpoint.
     */
    public final void addEndpoint(final Endpoint ep) {
        if (endpoints.putIfAbsent(ep.getName(), ep) != null) {
            LOG.warn("multiple endpoints defined with name [{}]", ep.getName());
        } else {
            LOG.debug("registering endpoint [{}]", ep.getName());
        }
    }

    /**
     * Gets the endpoint registered for handling a specific target address.
     * 
     * @param targetAddress The address.
     * @return The endpoint for handling the address or {@code null} if no endpoint is registered
     *         for the target address.
     */
    protected final Endpoint getEndpoint(final ResourceIdentifier targetAddress) {
        return getEndpoint(targetAddress.getEndpoint());
    }

    /**
     * Gets the endpoint registered for a given name.
     * 
     * @param endpointName The name.
     * @return The endpoint registered under the given name or {@code null} if no endpoint has been registered
     *         under the name.
     */
    protected final Endpoint getEndpoint(final String endpointName) {
        return endpoints.get(endpointName);
    }

    /**
     * Iterates over the endpoints registered with this service.
     * 
     * @return The endpoints.
     */
    protected final Iterable<Endpoint> endpoints() {
        return endpoints.values();
    }

    /**
     * Sets the factory to use for creating objects performing SASL based authentication of clients.
     *
     * @param factory The factory.
     * @throws NullPointerException if factory is {@code null}.
     */
    @Autowired(required = false)
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

        @SuppressWarnings("rawtypes")
        List<Future> endpointFutures = new ArrayList<>(endpoints.size());
        for (Endpoint ep : endpoints.values()) {
            LOG.info("starting endpoint [name: {}, class: {}]", ep.getName(), ep.getClass().getName());
            Future<Void> endpointFuture = Future.future();
            endpointFutures.add(endpointFuture);
            ep.start(endpointFuture);
        }
        final Future<Void> startFuture = Future.future();
        CompositeFuture.all(endpointFutures).setHandler(startup -> {
            if (startup.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(startup.cause());
            }
        });
        return startFuture;
    }

    private Future<Void> startInsecureServer() {

        if (isInsecurePortEnabled()) {
            int insecurePort = determineInsecurePort();
            final Future<Void> result = Future.future();
            final ProtonServerOptions options = createInsecureServerOptions();
            insecureServer = createProtonServer(options)
                    .connectHandler(this::onRemoteConnectionOpenInsecurePort)
                    .listen(insecurePort, getConfig().getInsecurePortBindAddress(), bindAttempt -> {
                        if (bindAttempt.succeeded()) {
                            if (getInsecurePort() == getInsecurePortDefaultValue()) {
                                LOG.info("server listens on standard insecure port [{}:{}]", getInsecurePortBindAddress(), getInsecurePort());
                            } else {
                                LOG.warn("server listens on non-standard insecure port [{}:{}], default is {}", getInsecurePortBindAddress(),
                                        getInsecurePort(), getInsecurePortDefaultValue());
                            }
                            result.complete();
                        } else {
                            LOG.error("cannot bind to insecure port", bindAttempt.cause());
                            result.fail(bindAttempt.cause());
                        }
                    });
            return result;
        } else {
            LOG.info("insecure port is not enabled");
            return Future.succeededFuture();
        }
    }

    private Future<Void> startSecureServer() {

        if (isSecurePortEnabled()) {
            int securePort = determineSecurePort();
            final Future<Void> result = Future.future();
            final ProtonServerOptions options = createServerOptions();
            server = createProtonServer(options)
                    .connectHandler(this::onRemoteConnectionOpen)
                    .listen(securePort, getConfig().getBindAddress(), bindAttempt -> {
                        if (bindAttempt.succeeded()) {
                            if (getPort() == getPortDefaultValue()) {
                                LOG.info("server listens on standard secure port [{}:{}]", getBindAddress(), getPort());
                            } else {
                                LOG.warn("server listens on non-standard secure port [{}:{}], default is {}", getBindAddress(),
                                        getPort(), getPortDefaultValue());
                            }
                            result.complete();
                        } else {
                            LOG.error("cannot bind to secure port", bindAttempt.cause());
                            result.fail(bindAttempt.cause());
                        }
                    });
            return result;
        } else {
            LOG.info("secure port is not enabled");
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
     * Subclasses may override this method to set custom options.
     * 
     * @return The options.
     */
    protected ProtonServerOptions createServerOptions() {
        ProtonServerOptions options = createInsecureServerOptions();

        addTlsKeyCertOptions(options);
        addTlsTrustOptions(options);
        return options;
    }

    /**
     * Invoked during start up to create options for the proton server instance binding to the insecure port.
     * <p>
     * Subclasses may override this method to set custom options.
     * 
     * @return The options.
     */
    protected ProtonServerOptions createInsecureServerOptions() {

        ProtonServerOptions options = new ProtonServerOptions();
        options.setHeartbeat(60000); // // close idle connections after two minutes of inactivity
        options.setReceiveBufferSize(16 * 1024); // 16kb
        options.setSendBufferSize(16 * 1024); // 16kb
        options.setLogActivity(getConfig().isNetworkDebugLoggingEnabled());

        return options;
    }

    @Override
    public final Future<Void> stopInternal() {

        Future<Void> shutdownHandler = Future.future();
        Future<Void> tracker = Future.future();
        if (server != null) {
            server.close(tracker.completer());
        } else {
            LOG.info("service has been already shut down");
            tracker.complete();
        }
        tracker.compose(t -> {
            if (insecureServer != null) {
                insecureServer.close(shutdownHandler.completer());
            } else {
                shutdownHandler.complete();
            }
        }, shutdownHandler);
        return shutdownHandler;
    }

    @Override
    protected final int getActualPort() {
        return (server != null ? server.actualPort() : Constants.PORT_UNCONFIGURED);
    }

    @Override
    protected final int getActualInsecurePort() {
        return (insecureServer != null ? insecureServer.actualPort() : Constants.PORT_UNCONFIGURED);
    }

    /**
     * Invoked when an AMQP <em>open</em> frame is received on the secure port.
     * <p>
     * Configures the AMQP connection for the secure port and provides a basic connection handling.
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
     * Configures the AMQP connection for the insecure port and provides a basic connection handling.
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
    protected final void handleUnknownEndpoint(final ProtonConnection con, final ProtonLink<?> link, final ResourceIdentifier address) {
        LOG.info("client [{}] wants to establish link for unknown endpoint [address: {}]",
                con.getRemoteContainer(), address);
        link.setCondition(
                ProtonHelper.condition(
                        AmqpError.NOT_FOUND,
                        String.format("no endpoint registered for address %s", address)));
        link.close();
    }

    /**
     * Creates a resource identifier for a given address.
     * 
     * @param address The address. If this service is configured for
     *         a single tenant only then the address is assumed to <em>not</em> contain a tenant
     *         component.
     * @return The identifier representing the address.
     * @throws IllegalArgumentException if the given address does not represent a valid resource identifier.
     */
    protected final ResourceIdentifier getResourceIdentifier(final String address) {
        if (getConfig().isSingleTenant()) {
            return ResourceIdentifier.fromStringAssumingDefaultTenant(address);
        } else {
            return ResourceIdentifier.fromString(address);
        }
    }

    /**
     * Handles a request from a client to establish a link for sending messages to this server.
     * The already established connection must have an authenticated user as principal for doing the authorization check.
     *
     * @param con the connection to the client.
     * @param receiver the receiver created for the link.
     */
    protected void handleReceiverOpen(final ProtonConnection con, final ProtonReceiver receiver) {
        if (receiver.getRemoteTarget().getAddress() == null) {
            LOG.debug("client [{}] wants to open an anonymous link for sending messages to arbitrary addresses, closing link",
                    con.getRemoteContainer());
            receiver.setCondition(ProtonHelper.condition(AmqpError.NOT_FOUND.toString(), "anonymous relay not supported")).close();
        } else {
            LOG.debug("client [{}] wants to open a link for sending messages [address: {}]",
                    con.getRemoteContainer(), receiver.getRemoteTarget());
            try {
                final ResourceIdentifier targetResource = getResourceIdentifier(receiver.getRemoteTarget().getAddress());
                final Endpoint endpoint = getEndpoint(targetResource);
                if (endpoint == null) {
                    handleUnknownEndpoint(con, receiver, targetResource);
                } else {
                    final HonoUser user = Constants.getClientPrincipal(con);
                    getAuthorizationService().isAuthorized(user, targetResource, Activity.WRITE).setHandler(authAttempt -> {
                        if (authAttempt.succeeded() && authAttempt.result()) {
                            Constants.copyProperties(con, receiver);
                            receiver.setTarget(receiver.getRemoteTarget());
                            endpoint.onLinkAttach(con, receiver, targetResource);
                        } else {
                            LOG.debug("subject [{}] is not authorized to WRITE to [{}]", user.getName(), targetResource);
                            receiver.setCondition(ProtonHelper.condition(AmqpError.UNAUTHORIZED_ACCESS.toString(), "unauthorized")).close();
                        }
                    });
                }
            } catch (final IllegalArgumentException e) {
                LOG.debug("client has provided invalid resource identifier as target address", e);
                receiver.close();
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
        LOG.debug("client [{}] wants to open a link for receiving messages [address: {}]",
                con.getRemoteContainer(), remoteSource);
        try {
            final ResourceIdentifier targetResource = getResourceIdentifier(remoteSource.getAddress());
            final Endpoint endpoint = getEndpoint(targetResource);
            if (endpoint == null) {
                handleUnknownEndpoint(con, sender, targetResource);
            } else {
                final HonoUser user = Constants.getClientPrincipal(con);
                getAuthorizationService().isAuthorized(user, targetResource, Activity.READ).setHandler(authAttempt -> {
                    if (authAttempt.succeeded() && authAttempt.result()) {
                        Constants.copyProperties(con, sender);
                        sender.setSource(sender.getRemoteSource());
                        endpoint.onLinkAttach(con, sender, targetResource);
                    } else {
                        LOG.debug("subject [{}] is not authorized to READ from [{}]", user.getName(), targetResource);
                        sender.setCondition(ProtonHelper.condition(AmqpError.UNAUTHORIZED_ACCESS.toString(), "unauthorized")).close();
                    }
                });
            }
        } catch (final IllegalArgumentException e) {
            LOG.debug("client has provided invalid resource identifier as target address", e);
            sender.close();
        }
    }

    private void setRemoteConnectionOpenHandler(final ProtonConnection connection) {
        connection.sessionOpenHandler(remoteOpenSession -> handleSessionOpen(connection, remoteOpenSession));
        connection.receiverOpenHandler(remoteOpenReceiver -> handleReceiverOpen(connection, remoteOpenReceiver));
        connection.senderOpenHandler(remoteOpenSender -> handleSenderOpen(connection, remoteOpenSender));
        connection.disconnectHandler(this::handleRemoteDisconnect);
        connection.closeHandler(remoteClose -> handleRemoteConnectionClose(connection, remoteClose));
        connection.openHandler(remoteOpen -> {
            LOG.info("client [container: {}, user: {}] connected", connection.getRemoteContainer(), Constants.getClientPrincipal(connection).getName());
            connection.open();
            // attach an ID so that we can later inform downstream components when connection is closed
            connection.attachments().set(Constants.KEY_CONNECTION_ID, String.class, UUID.randomUUID().toString());
        });
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
        LOG.info("opening new session with client [{}]", con.getRemoteContainer());
        session.closeHandler(sessionResult -> {
            if (sessionResult.succeeded()) {
                sessionResult.result().close();
            }
        }).open();
    }

    /**
     * Is called whenever a proton connection was closed. The implementation is intentionally empty.
     * <p>
     * Subclasses should override this method to publish this as an event on the vertx bus if desired.
     *
     * @param con The connection that was closed.
     */
    protected void publishConnectionClosedEvent(final ProtonConnection con) {
    }

    /**
     * Invoked when a client closes the connection with this server.
     * <p>
     * The implementation closes and disconnects the connection.
     *
     * @param con The connection to close.
     * @param res The client's close frame.
     */
    protected void handleRemoteConnectionClose(final ProtonConnection con, final AsyncResult<ProtonConnection> res) {
        if (res.succeeded()) {
            LOG.info("client [{}] closed connection", con.getRemoteContainer());
        } else {
            LOG.info("client [{}] closed connection with error", con.getRemoteContainer(), res.cause());
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
        LOG.info("client [{}] disconnected", con.getRemoteContainer());
        con.disconnect();
        publishConnectionClosedEvent(con);
    }

    /**
     * Registers this service's endpoints' readiness checks.
     * <p>
     * This default implementation invokes {@link Endpoint#registerReadinessChecks(HealthCheckHandler)}
     * for all registered endpoints.
     * <p>
     * Subclasses should override this method to register more specific checks.
     * 
     * @param handler The health check handler to register the checks with.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {

        for (Endpoint ep : endpoints()) {
            ep.registerReadinessChecks(handler);
        }
    }

    /**
     * Registers this service's endpoints' liveness checks.
     * <p>
     * This default implementation invokes {@link Endpoint#registerLivenessChecks(HealthCheckHandler)}
     * for all registered endpoints.
     * <p>
     * Subclasses should override this method to register more specific checks.
     * 
     * @param handler The health check handler to register the checks with.
     */
    @Override
    public void registerLivenessChecks(HealthCheckHandler handler) {
        for (Endpoint ep : endpoints()) {
            ep.registerLivenessChecks(handler);
        }
    }
}
