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
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonServer;
import io.vertx.proton.ProtonServerOptions;
import io.vertx.proton.ProtonSession;

/**
 * The Hono server is an AMQP 1.0 container that provides endpoints for the <em>Telemetry</em>,
 * <em>Command &amp; Control</em> and <em>Device Registration</em> APIs that <em>Protocol Adapters</em> and
 * <em>Solutions</em> use to interact with devices.
 */
public final class HonoServer extends AbstractVerticle {

    private static final Logger   LOG = LoggerFactory.getLogger(HonoServer.class);
    private final String          authServiceAddress;
    private String                bindAddress;
    private int                   port;
    private boolean               singleTenant;
    private boolean               networkDebugLoggingEnabled;
    private final int             instanceNo;
    private ProtonServer          server;
    private Map<String, Endpoint> endpoints = new HashMap<>();

    HonoServer(final String bindAddress, final int port, final boolean singleTenant) {
        this(bindAddress, port, singleTenant, 0);
    }

    HonoServer(final String bindAddress, final int port, final boolean singleTenant, final int instanceNo) {
        this.bindAddress = Objects.requireNonNull(bindAddress);
        this.port = port;
        this.singleTenant = singleTenant;
        this.instanceNo = instanceNo;
        this.authServiceAddress = String.format("%s.%d", EVENT_BUS_ADDRESS_AUTHORIZATION_IN, instanceNo);
    }

    @Override
    public void start(final Future<Void> startupHandler) {

        checkStandardEndpointsAreRegistered();

        if (!startEndpoints()) {
            startupHandler.fail("one or more of the registered endpoints failed to start, aborting ...");
        } else {
            final ProtonServerOptions options = createServerOptions();
            server = ProtonServer.create(vertx, options)
                    .saslAuthenticatorFactory(new PlainSaslAuthenticatorFactory(vertx))
                    .connectHandler(this::handleRemoteConnectionOpen)
                    .listen(port, bindAddress, bindAttempt -> {
                        if (bindAttempt.succeeded()) {
                            this.port = bindAttempt.result().actualPort();
                            LOG.info("HonoServer running at [{}:{}]", bindAddress, this.port);
                            startupHandler.complete();
                        } else {
                            LOG.error("cannot start up HonoServer", bindAttempt.cause());
                            startupHandler.fail(bindAttempt.cause());
                        }
                    });
        }
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

    private boolean startEndpoints() {
        boolean succeeded = true;
        for (Endpoint ep : endpoints.values()) {
            LOG.info("starting endpoint [name: {}, class: {}]", ep.getName(), ep.getClass().getName());
            succeeded &= ep.start();
            if (!succeeded) {
                LOG.error("could not start endpoint [name: {}, class: {}]", ep.getName(), ep.getClass().getName());
                break;
            }
        }
        return succeeded;
    }

    ProtonServerOptions createServerOptions() {

        ProtonServerOptions options = new ProtonServerOptions();
        options.setIdleTimeout(0);
        options.setReceiveBufferSize(32 * 1024); // 32kb
        options.setSendBufferSize(32 * 1024); // 32kb
        options.setLogActivity(networkDebugLoggingEnabled);
        return options;
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

    public void addEndpoints(final List<Endpoint> definedEndpoints) {
        Objects.requireNonNull(definedEndpoints);
        for (Endpoint ep : definedEndpoints) {
            addEndpoint(ep);
        }
    }

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
            return this.port;
        }
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public void setNetworkDebugLoggingEnabled(final boolean enabled) {
        this.networkDebugLoggingEnabled = enabled;
    }

    /**
     * @return the singleTenant
     */
    public boolean isSingleTenant() {
        return singleTenant;
    }

    void handleRemoteConnectionOpen(final ProtonConnection connection) {
        connection.setContainer(String.format("Hono-%s:%d-%d", this.bindAddress, server.actualPort(), instanceNo));
        connection.sessionOpenHandler(remoteOpenSession -> handleSessionOpen(connection, remoteOpenSession));
        connection.receiverOpenHandler(remoteOpenReceiver -> handleReceiverOpen(connection, remoteOpenReceiver));
        connection.senderOpenHandler(remoteOpenSender -> handleSenderOpen(connection, remoteOpenSender));
        connection.disconnectHandler(this::handleRemoteDisconnect);
        connection.closeHandler(remoteClose -> handleRemoteConnectionClose(connection, remoteClose));
        connection.openHandler(remoteOpen -> {
            LOG.info("client [container: {}, user: {}] connected", connection.getRemoteContainer(), getUserFromConnection(connection));
            connection.open();
            // attach an ID so that we can later inform downstream verticles when connection is closed
            connection.attachments().set(Constants.KEY_CONNECTION_ID, String.class, UUID.randomUUID().toString());
        });
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
    private static void handleRemoteConnectionClose(final ProtonConnection con, final AsyncResult<ProtonConnection> res) {
        if (res.succeeded()) {
            LOG.info("client [{}] closed connection", con.getRemoteContainer());
        } else {
            LOG.info("client [{}] closed connection with error", con.getRemoteContainer(), res.cause());
        }
        con.close();
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
     * @return the user associated with the connection or {@link Constants#DEFAULT_SUBJECT} if it cannot be determined.
     */
    private static String getUserFromConnection(final ProtonConnection con) {

        Principal clientId = Constants.getClientPrincipal(con);
        if (clientId == null) {
            LOG.warn("connection from client [{}] is not authenticated properly using SASL, falling back to default subject [{}]",
                    con.getRemoteContainer(), Constants.DEFAULT_SUBJECT);
            return Constants.DEFAULT_SUBJECT;
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
           authServiceAddress,
           authRequest,
           res -> authResultHandler.handle(res.succeeded() && AuthorizationConstants.ALLOWED.equals(res.result().body())));
    }

    private ResourceIdentifier getResourceIdentifier(final String address) {
        if (isSingleTenant()) {
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
        return authServiceAddress;
    }
}
