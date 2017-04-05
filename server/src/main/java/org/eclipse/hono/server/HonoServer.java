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
package org.eclipse.hono.server;

import static io.vertx.proton.ProtonHelper.condition;
import static org.apache.qpid.proton.amqp.transport.AmqpError.UNAUTHORIZED_ACCESS;
import static org.eclipse.hono.authorization.AuthorizationConstants.EVENT_BUS_ADDRESS_AUTHORIZATION_IN;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.Source;
import org.apache.qpid.proton.engine.Record;
import org.eclipse.hono.authorization.AuthorizationConstants;
import org.eclipse.hono.authorization.Permission;
import org.eclipse.hono.config.HonoConfigProperties;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonServer;
import io.vertx.proton.ProtonServerOptions;
import io.vertx.proton.ProtonSession;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;
import org.springframework.util.StringUtils;

/**
 * The Hono server is an AMQP 1.0 container that provides endpoints for the <em>Telemetry</em>,
 * <em>Command &amp; Control</em> and <em>Device Registration</em> APIs that <em>Protocol Adapters</em> and
 * <em>Solutions</em> use to interact with devices.
 */
@Component
@Scope("prototype")
public final class HonoServer extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(HonoServer.class);
    private ProtonServer server;
    private ProtonServer insecureServer;
    private Map<String, Endpoint> endpoints = new HashMap<>();
    private HonoConfigProperties honoConfig = new HonoConfigProperties();
    private ProtonSaslAuthenticatorFactory saslAuthenticatorFactory;

    private int amqpPort         = Constants.PORT_UNCONFIGURED;
    private int amqpInsecurePort = Constants.PORT_UNCONFIGURED;

    /**
     * Sets the global Hono configuration properties.
     *
     * @param props The properties.
     * @return This instance for setter chaining.
     * @throws NullPointerException if props is {@code null}.
     */
    @Autowired(required = false)
    public HonoServer setHonoConfiguration(final HonoConfigProperties props) {
        this.honoConfig = Objects.requireNonNull(props);
        if (LOG.isWarnEnabled()) {
            StringBuilder b = new StringBuilder()
                    .append("Hono server does not yet support limiting the incoming message size ")
                    .append("via the maxPayloadSize property");
            LOG.warn(b.toString());
        }
        return this;
    }

    /**
     * Sets the factory to use for creating objects performing SASL based authentication of clients.
     *
     * @param factory The factory.
     * @return This instance for setter chaining.
     * @throws NullPointerException if factory is {@code null}.
     */
    @Autowired
    public HonoServer setSaslAuthenticatorFactory(final ProtonSaslAuthenticatorFactory factory) {
        this.saslAuthenticatorFactory = Objects.requireNonNull(factory);
        return this;
    }

    @Override
    public void start(final Future<Void> startupHandler) {

        checkStandardEndpointsAreRegistered();

        Future<Void> portConfigurationTracker = Future.future();
        determinePortConfigurations(portConfigurationTracker);

        portConfigurationTracker.compose(s -> startEndpoints()
        ).compose(s -> startSecureServer()
        ).compose(s -> startInsecureServer(startupHandler)
        , startupHandler);
    }

    private void startInsecureServer(Future<Void> startupHandler) {
        if (isOpenInsecurePort()) {
            final ProtonServerOptions options = createServerOptions();
            insecureServer = createProtonServer(options)
                    .connectHandler(this::handleRemoteConnectionOpenInsecurePort)
                    .listen(amqpInsecurePort, honoConfig.getInsecurePortBindAddress(), bindAttempt -> {
                        if (bindAttempt.succeeded()) {
                            LOG.info("HonoServer insecure port listening on [{}:{}]", getInsecurePortBindAddress(), getInsecurePort());
                            if (getInsecurePort() != Constants.PORT_AMQP)
                                LOG.warn("This is NOT the IANA standard insecure port {}!", Constants.PORT_AMQP);
                            startupHandler.complete();
                        } else {
                            LOG.error("cannot start up HonoServer (insecure port failed) ", bindAttempt.cause());
                            startupHandler.fail(bindAttempt.cause());
                        }
                    });
        } else {
            startupHandler.complete();
        }
    }

    private Future<Void> startSecureServer() {
        final Future<Void> startSecurePortTracker = Future.future();
        if (isOpenSecurePort()) {
            final ProtonServerOptions options = createSecureServerOptions();
            server = createProtonServer(options)
                    .connectHandler(this::handleRemoteConnectionOpen)
                    .listen(amqpPort, honoConfig.getBindAddress(), bindAttempt -> {
                        if (bindAttempt.succeeded()) {
                            LOG.info("HonoServer secure port listening on [{}:{}]", getBindAddress(), getPort());
                            if (getPort() != Constants.PORT_AMQPS)
                                LOG.warn("This is NOT the IANA standard port {}!", Constants.PORT_AMQPS);
                            startSecurePortTracker.complete();
                        } else {
                            LOG.error("cannot start up HonoServer (secure port failed) ", bindAttempt.cause());
                            startSecurePortTracker.fail(bindAttempt.cause());
                        }
                    });
        } else {
            startSecurePortTracker.complete();
        }
        return startSecurePortTracker;
    }

    private ProtonServer createProtonServer(ProtonServerOptions options) {
        return ProtonServer.create(vertx, options)
                .saslAuthenticatorFactory(saslAuthenticatorFactory);
    }

    /**
     * Either one secure, one insecure or one secure AND insecure ports are supported in Hono.
     * The secure port should stick to the IANA default 5671, otherwise a warning will be issued.
     * The insecure port should stick to the IANA default 5672, otherwise a warning will be issued.
     *
     * @param portConfigurationTracker The future to continue or fail (in case of configuration errors).
     * @throws NullPointerException if portConfigurationTracker is null.
     */
    void determinePortConfigurations(Future<Void> portConfigurationTracker) {
        Objects.requireNonNull(portConfigurationTracker);

        // secure port first
        determineSecurePortConfiguration();
        determineInsecurePortConfiguration();

        if (isPortUnconfigured(amqpPort) && isPortUnconfigured(amqpInsecurePort)) {
            final String message = "cannot start up HonoServer - no listening ports found.";
            portConfigurationTracker.fail(message);
        } else if (isPortConfigured(amqpPort) && isPortConfigured(amqpInsecurePort) && amqpPort == amqpInsecurePort) {
            final String message = String.format("cannot start up HonoServer - secure and insecure ports configured to same port number %s.",
                    amqpPort);
            portConfigurationTracker.fail(message);
        } else {
            portConfigurationTracker.complete();
        }
    }

    private void determineSecurePortConfiguration() {
        if (honoConfig.getKeyCertOptions() == null) {
            if (honoConfig.getPort() >= 0) {
                LOG.warn("AMQP 1.0 secure port number configured, but the certificate setup is not correct. No secure port will be opened - please check your configuration!");
            }
            return;
        }

        if (honoConfig.getPort(Constants.PORT_AMQPS) == Constants.PORT_AMQPS) {
            LOG.info("HonoServer uses secure IANA standard port {}", Constants.PORT_AMQPS);
        } else if (honoConfig.getPort() == 0) {
            LOG.info("HonoServer found secure port number configured for ephemeral port selection (port chosen automatically).");
        }
        amqpPort = honoConfig.getPort(Constants.PORT_AMQPS);
    }

    private void determineInsecurePortConfiguration() {
        if (!honoConfig.isInsecurePortEnabled())
            return;

        if (honoConfig.getInsecurePort() == 0) {
            LOG.info("HonoServer found insecure port number configured for ephemeral port selection (port chosen automatically).");
        } else if (honoConfig.getInsecurePort(Constants.PORT_AMQP) == Constants.PORT_AMQP) {
            LOG.info("HonoServer uses IANA standard insecure port {}", honoConfig.getInsecurePort());
        } else if (honoConfig.getInsecurePort() == Constants.PORT_AMQPS) {
            LOG.warn("HonoServer found insecure port number configured to IANA standard port for secure connections {}", honoConfig.getInsecurePort());
            LOG.warn("Possibly misconfigured?");
        }
        amqpInsecurePort = honoConfig.getInsecurePort(Constants.PORT_AMQP);
    }

    private void checkStandardEndpointsAreRegistered() {
        if (!isTelemetryEndpointConfigured()) {
            LOG.warn("no Telemetry endpoint has been configured, Hono server will not support Telemetry API");
        }
        if (!isRegistrationEndpointConfigured()) {
            LOG.warn("no Registration endpoint has been configured, Hono server will not support Registration API");
        }
    }

    private boolean isTelemetryEndpointConfigured() {
        return endpoints.containsKey(TelemetryConstants.TELEMETRY_ENDPOINT);
    }

    private boolean isRegistrationEndpointConfigured() {
        return endpoints.containsKey(RegistrationConstants.REGISTRATION_ENDPOINT);
    }

    private Future<Void> startEndpoints() {
        final Future<Void> startFuture = Future.future();
        List<Future> endpointFutures = new ArrayList<>(endpoints.size());
        for (Endpoint ep : endpoints.values()) {
            LOG.info("starting endpoint [name: {}, class: {}]", ep.getName(), ep.getClass().getName());
            Future<Void> endpointFuture = Future.future();
            endpointFutures.add(endpointFuture);
            ep.start(endpointFuture);
        }
        CompositeFuture.all(endpointFutures).setHandler(startup -> {
            if (startup.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(startup.cause());
            }
        });
        return startFuture;
    }

    ProtonServerOptions createSecureServerOptions() {
        ProtonServerOptions options = createServerOptions();

        addTlsKeyCertOptions(options);
        addTlsTrustOptions(options);
        return options;
    }

    ProtonServerOptions createServerOptions() {

        ProtonServerOptions options = new ProtonServerOptions();
        options.setHeartbeat(60000); // // close idle connections after two minutes of inactivity
        options.setReceiveBufferSize(16 * 1024); // 16kb
        options.setSendBufferSize(16 * 1024); // 16kb
        options.setLogActivity(honoConfig.isNetworkDebugLoggingEnabled());

        return options;
    }

    private void addTlsTrustOptions(final ProtonServerOptions serverOptions) {

        if (serverOptions.isSsl() && serverOptions.getTrustOptions() == null) {

            TrustOptions trustOptions = honoConfig.getTrustOptions();
            if (trustOptions != null) {
                serverOptions.setTrustOptions(trustOptions).setClientAuth(ClientAuth.REQUEST);
                LOG.info("enabling TLS for client authentication");
            }
        }
    }

    private void addTlsKeyCertOptions(final ProtonServerOptions serverOptions) {

        KeyCertOptions keyCertOptions = honoConfig.getKeyCertOptions();

        if (keyCertOptions != null) {
            serverOptions.setSsl(true).setKeyCertOptions(keyCertOptions);
        }
    }

    @Override
    public void stop(Future<Void> shutdownHandler) {
        if (server != null) {
            server.close(done -> {
                LOG.info("HonoServer has been shut down");
                shutdownHandler.complete();
            });
        } else {
            LOG.info("HonoServer has been already shut down");
            shutdownHandler.complete();
        }
    }

    /**
     * Adds multiple endpoints to this server.
     *
     * @param definedEndpoints The endpoints.
     */
    @Autowired
    public void addEndpoints(final List<Endpoint> definedEndpoints) {
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
    public void addEndpoint(final Endpoint ep) {
        if (endpoints.putIfAbsent(ep.getName(), ep) != null) {
            LOG.warn("multiple endpoints defined with name [{}]", ep.getName());
        } else {
            LOG.debug("registering endpoint [{}]", ep.getName());
        }
    }

    /**
     * Gets the port Hono listens on for AMQP 1.0 connections.
     * <p>
     * If the port has been set to 0 Hono will bind to an arbitrary free port chosen by the operating system during
     * startup. Once Hono is up and running this method returns the <em>actual port</em> Hono has bound to.
     * </p>
     *
     * @return the port Hono listens on.
     */
    public int getPort() {
        if (server != null) {
            return server.actualPort();
        } else {
            return this.honoConfig.getPort(Constants.PORT_AMQPS);
        }
    }

    /**
     * Gets if Hono opens a secure port.
     *
     * @return The flag value for opening a secure port.
     */
    public boolean isOpenSecurePort() {
        return (amqpPort >= 0);
    }

    /**
     * Gets the insecure port Hono listens on for AMQP 1.0 connections (if configured).
     * <p>
     * If the port has been set to 0 Hono will bind to an arbitrary free port chosen by the operating system during
     * startup. Once Hono is up and running this method returns the <em>actual port</em> Hono has bound to.
     * </p>
     *
     * @return the insecure port Hono listens on.
     */
    public int getInsecurePort() {
        if (!honoConfig.isInsecurePortEnabled())
            return Constants.PORT_UNCONFIGURED;
        if (insecureServer != null) {
            return insecureServer.actualPort();
        } else {
            return this.honoConfig.getInsecurePort(Constants.PORT_AMQP);
        }
    }

    /**
     * Gets if Hono opens an insecure port.
     *
     * @return The flag value for opening an insecure port.
     */
    public boolean isOpenInsecurePort() {
        return (amqpInsecurePort >= 0);
    }
    /**
     * Gets the IP address Hono is bound to.
     *
     * @return The IP address.
     */
    public String getBindAddress() {
        return honoConfig.getBindAddress();
    }

    /**
     * Gets the IP address Hono's insecure port is bound to.
     *
     * @return The IP address.
     */
    public String getInsecurePortBindAddress() {
        return honoConfig.getInsecurePortBindAddress();
    }

    private static boolean isPortUnconfigured(int portToCheck) {
        return (portToCheck == Constants.PORT_UNCONFIGURED);
    }

    private static boolean isPortConfigured(int portToCheck) {
        return (portToCheck != Constants.PORT_UNCONFIGURED && portToCheck != 0);
    }


    private void setRemoteConnectionOpenHandler(final ProtonConnection connection) {
        connection.sessionOpenHandler(remoteOpenSession -> handleSessionOpen(connection, remoteOpenSession));
        connection.receiverOpenHandler(remoteOpenReceiver -> handleReceiverOpen(connection, remoteOpenReceiver));
        connection.senderOpenHandler(remoteOpenSender -> handleSenderOpen(connection, remoteOpenSender));
        connection.disconnectHandler(this::handleRemoteDisconnect);
        connection.closeHandler(remoteClose -> handleRemoteConnectionClose(connection, remoteClose));
        connection.openHandler(remoteOpen -> {
            LOG.info("client [container: {}, user: {}] connected", connection.getRemoteContainer(), getUserFromConnection(connection));
            connection.open();
            // attach an ID so that we can later inform downstream components when connection is closed
            connection.attachments().set(Constants.KEY_CONNECTION_ID, String.class, UUID.randomUUID().toString());
        });
    }

    void handleRemoteConnectionOpen(final ProtonConnection connection) {
        connection.setContainer(String.format("Hono-%s:%d", getBindAddress(), getPort()));
        setRemoteConnectionOpenHandler(connection);
    }

    void handleRemoteConnectionOpenInsecurePort(final ProtonConnection connection) {
        connection.setContainer(String.format("Hono-%s:%d", getInsecurePortBindAddress(), getInsecurePort()));
        setRemoteConnectionOpenHandler(connection);
    }

    private void handleSessionOpen(final ProtonConnection con, final ProtonSession session) {
        LOG.info("opening new session with client [{}]", con.getRemoteContainer());
        session.closeHandler(sessionResult -> {
            if (sessionResult.succeeded()) {
                sessionResult.result().close();
            }
        }).open();
    }

    /**
     * Invoked when a client closes the connection with this server.
     *
     * @param con The connection to close.
     * @param res The client's close frame.
     */
    private void handleRemoteConnectionClose(final ProtonConnection con, final AsyncResult<ProtonConnection> res) {
        if (res.succeeded()) {
            LOG.info("client [{}] closed connection", con.getRemoteContainer());
        } else {
            LOG.info("client [{}] closed connection with error", con.getRemoteContainer(), res.cause());
        }
        con.close();
        con.disconnect();
        publishConnectionClosedEvent(con);
    }

    private void handleRemoteDisconnect(final ProtonConnection connection) {
        LOG.info("client [{}] disconnected", connection.getRemoteContainer());
        connection.disconnect();
        publishConnectionClosedEvent(connection);
    }

    /**
     * Handles a request from a client to establish a link for sending messages to this server.
     *
     * @param con the connection to the client.
     * @param receiver the receiver created for the link.
     */
    void handleReceiverOpen(final ProtonConnection con, final ProtonReceiver receiver) {
        if (receiver.getRemoteTarget().getAddress() == null) {
            LOG.debug("client [{}] wants to open an anonymous link for sending messages to arbitrary addresses, closing link",
                    con.getRemoteContainer());
            receiver.setCondition(condition(AmqpError.NOT_FOUND.toString(), "anonymous relay not supported")).close();
        } else {
            LOG.debug("client [{}] wants to open a link for sending messages [address: {}]",
                    con.getRemoteContainer(), receiver.getRemoteTarget());
            try {
                final ResourceIdentifier targetResource = getResourceIdentifier(receiver.getRemoteTarget().getAddress());
                final Endpoint endpoint = getEndpoint(targetResource);
                if (endpoint == null) {
                    handleUnknownEndpoint(con, receiver, targetResource);
                } else {
                    final String user = getUserFromConnection(con);
                    checkAuthorizationToAttach(user, targetResource, Permission.WRITE, isAuthorized -> {
                        if (isAuthorized) {
                            copyConnectionId(con.attachments(), receiver.attachments());
                            receiver.setTarget(receiver.getRemoteTarget());
                            endpoint.onLinkAttach(receiver, targetResource);
                        } else {
                            final String message = String.format("subject [%s] is not authorized to WRITE to [%s]", user, targetResource);
                            receiver.setCondition(condition(UNAUTHORIZED_ACCESS.toString(), message)).close();
                        }
                    });
                }
            } catch (final IllegalArgumentException e) {
                LOG.debug("client has provided invalid resource identifier as target address", e);
                receiver.close();
            }
        }
    }

    private void publishConnectionClosedEvent(final ProtonConnection con) {

        String conId = con.attachments().get(Constants.KEY_CONNECTION_ID, String.class);
        if (conId != null) {
            vertx.eventBus().publish(
                    Constants.EVENT_BUS_ADDRESS_CONNECTION_CLOSED,
                    conId);
        }
    }

    private static void copyConnectionId(final Record source, final Record target) {
        target.set(Constants.KEY_CONNECTION_ID, String.class, source.get(Constants.KEY_CONNECTION_ID, String.class));
    }

    /**
     * Gets the authenticated client principal name for an AMQP connection.
     *
     * @param con the connection to read the user from
     * @return the user associated with the connection or {@link Constants#SUBJECT_ANONYMOUS} if it cannot be determined.
     */
    private static String getUserFromConnection(final ProtonConnection con) {

        Principal clientId = Constants.getClientPrincipal(con);
        if (clientId == null) {
            LOG.warn("connection from client [{}] is not authenticated properly using SASL, falling back to default subject [{}]",
                    con.getRemoteContainer(), Constants.SUBJECT_ANONYMOUS);
            return Constants.SUBJECT_ANONYMOUS;
        } else {
            return clientId.getName();
        }
    }

    /**
     * Handles a request from a client to establish a link for receiving messages from this server.
     *
     * @param con the connection to the client.
     * @param sender the sender created for the link.
     */
    void handleSenderOpen(final ProtonConnection con, final ProtonSender sender) {
        final Source remoteSource = sender.getRemoteSource();
        LOG.debug("client [{}] wants to open a link for receiving messages [address: {}]",
                con.getRemoteContainer(), remoteSource);
        try {
            final ResourceIdentifier targetResource = getResourceIdentifier(remoteSource.getAddress());
            final Endpoint endpoint = getEndpoint(targetResource);
            if (endpoint == null) {
                handleUnknownEndpoint(con, sender, targetResource);
            } else {
                final String user = getUserFromConnection(con);
                checkAuthorizationToAttach(user, targetResource, Permission.READ, isAuthorized -> {
                    if (isAuthorized) {
                        copyConnectionId(con.attachments(), sender.attachments());
                        sender.setSource(sender.getRemoteSource());
                        endpoint.onLinkAttach(sender, targetResource);
                    } else {
                        final String message = String.format("subject [%s] is not authorized to READ from [%s]", user, targetResource);
                        sender.setCondition(condition(UNAUTHORIZED_ACCESS.toString(), message)).close();
                    }
                });
            }
        } catch (final IllegalArgumentException e) {
            LOG.debug("client has provided invalid resource identifier as target address", e);
            sender.close();
        }
    }

    private static void handleUnknownEndpoint(final ProtonConnection con, final ProtonLink<?> link, final ResourceIdentifier address) {
        LOG.info("client [{}] wants to establish link for unknown endpoint [address: {}]",
                con.getRemoteContainer(), address);
        link.setCondition(
                condition(AmqpError.NOT_FOUND.toString(),
                String.format("no endpoint registered for address %s", address)));
        link.close();
    }

    private Endpoint getEndpoint(final ResourceIdentifier targetAddress) {
        return endpoints.get(targetAddress.getEndpoint());
    }

    private void checkAuthorizationToAttach(final String user, final ResourceIdentifier targetResource, final Permission permission,
       final Handler<Boolean> authResultHandler) {

        final JsonObject authRequest = AuthorizationConstants.getAuthorizationMsg(user, targetResource.toString(),
           permission.toString());
        vertx.eventBus().send(
           EVENT_BUS_ADDRESS_AUTHORIZATION_IN,
           authRequest,
           res -> authResultHandler.handle(res.succeeded() && AuthorizationConstants.ALLOWED.equals(res.result().body())));
    }

    private ResourceIdentifier getResourceIdentifier(final String address) {
        if (honoConfig.isSingleTenant()) {
            return ResourceIdentifier.fromStringAssumingDefaultTenant(address);
        } else {
            return ResourceIdentifier.fromString(address);
        }
    }

    /**
     * Gets the event bus address this Hono server uses for authorizing client requests.
     *
     * @return the address.
     */
    String getAuthServiceAddress() {
        return EVENT_BUS_ADDRESS_AUTHORIZATION_IN;
    }
}
