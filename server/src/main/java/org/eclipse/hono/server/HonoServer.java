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

import static org.eclipse.hono.authorization.AuthorizationConstants.EVENT_BUS_ADDRESS_AUTHORIZATION_IN;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.Source;
import org.eclipse.hono.authorization.AuthorizationConstants;
import org.eclipse.hono.authorization.Permission;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonServer;
import io.vertx.proton.ProtonServerOptions;

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
        if (!isTelemetryEndpointConfigured()) {
            LOG.warn("No Telemetry endpoint has been configured, aborting start up ...");
            startupHandler.fail("Telemetry endpoint must be configured");
        } else if (!startEndpoints()) {
            startupHandler.fail("Some registered endpoints failed to start");
        } else {
            final ProtonServerOptions options = createServerOptions();
            server = ProtonServer.create(vertx, options)
                    .connectHandler(this::helloProcessConnection)
                    .listen(port, bindAddress, bindAttempt -> {
                        if (bindAttempt.succeeded()) {
                            this.port = bindAttempt.result().actualPort();
                            LOG.info("HonoServer running at [{}:{}]", bindAddress, this.port);
                            startupHandler.complete();
                        } else {
                            LOG.error("Cannot start up HonoServer", bindAttempt.cause());
                            startupHandler.fail(bindAttempt.cause());
                        }
                    });
        }
    }

    private boolean isTelemetryEndpointConfigured() {
        return endpoints.containsKey(TelemetryConstants.TELEMETRY_ENDPOINT);
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
        options.setReuseAddress(false);
        return options;
    }

    @Override
    public void stop(Future<Void> shutdownHandler) {
        if (server != null) {
            server.close(done -> shutdownHandler.complete());
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

    /**
     * @return the singleTenant
     */
    public boolean isSingleTenant() {
        return singleTenant;
    }

    void helloProcessConnection(final ProtonConnection connection) {
        connection.setContainer(String.format("Hono-%s:%d-%d", this.bindAddress, server.actualPort(), instanceNo));
        connection.sessionOpenHandler(session -> session.open());
        connection.receiverOpenHandler(openedReceiver -> handleReceiverOpen(connection, openedReceiver));
        connection.senderOpenHandler(openedSender -> handleSenderOpen(connection, openedSender));
        connection.disconnectHandler(HonoServer::handleDisconnected);
        connection.closeHandler(HonoServer::handleConnectionClosed);
        connection.openHandler(result -> {
            LOG.debug("Client [{}:{}] connected", connection.getRemoteHostname(), connection.getRemoteContainer());
        });
        connection.open();
    }

    private static void handleConnectionClosed(AsyncResult<ProtonConnection> res) {
        if (res.succeeded()) {
            ProtonConnection con = res.result();
            LOG.debug("Client [{}:{}] closed connection", con.getRemoteHostname(), con.getRemoteContainer());
            con.close();
        }
    }

    private static void handleDisconnected(ProtonConnection connection) {
        LOG.debug("Client [{}:{}] disconnected", connection.getRemoteHostname(), connection.getRemoteContainer());
    }

    /**
     * Handles a request from a client to establish a link for sending messages to this server.
     * 
     * @param con the connection to the client.
     * @param receiver the receiver created for the link.
     */
    void handleReceiverOpen(final ProtonConnection con, final ProtonReceiver receiver) {
        LOG.debug("client wants to open a link for sending messages [address: {}]", receiver.getRemoteTarget());
        try {
            final ResourceIdentifier targetResource = getResourceIdentifier(receiver.getRemoteTarget().getAddress());
            final Endpoint endpoint = getEndpoint(targetResource);
            if (endpoint == null) {
                LOG.info("no matching endpoint registered for address [{}]", receiver.getRemoteTarget().getAddress());
                receiver.close();
            } else {
                checkAuthorizationToAttach(targetResource, Permission.WRITE, isAuthorized -> {
                    if (isAuthorized) {
                        receiver.setTarget(receiver.getRemoteTarget());
                        endpoint.onLinkAttach(receiver, targetResource);
                    } else {
                        LOG.debug("client is not authorized to attach to endpoint [{}], closing link", targetResource);
                        receiver.close();
                    }
                });
            }
        } catch (IllegalArgumentException e) {
            LOG.debug("client has provided invalid resource identifier as target address", e);
            receiver.close();
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
        LOG.debug("client wants to open a link for receiving messages [address: {}]", remoteSource);
        try {
            final String source = remoteSource.getAddress();
            final ResourceIdentifier targetResource = getResourceIdentifier(source);
            final Endpoint endpoint = getEndpoint(targetResource);
            if (endpoint == null) {
                LOG.info("no matching endpoint registered for address [{}]", source);
                sender.close();
            } else {
                checkAuthorizationToAttach(targetResource, Permission.READ, isAuthorized -> {
                    if (isAuthorized) {
                        LOG.debug("client is authorized to attach to endpoint [{}]", targetResource);
                        sender.setSource(sender.getRemoteSource());
                        endpoint.onLinkAttach(sender, targetResource);
                    } else {
                        LOG.debug("client is not authorized to attach to endpoint [{}], closing link", targetResource);
                        sender.close();
                    }
                });
            }
        } catch (final IllegalArgumentException e) {
            LOG.debug("client has provided invalid resource identifier as target address", e);
            sender.close();
        }
    }

    private Endpoint getEndpoint(final ResourceIdentifier targetAddress) {
        return endpoints.get(targetAddress.getEndpoint());
    }

    private void checkAuthorizationToAttach(final ResourceIdentifier targetResource, final Permission permission,
       final Handler<Boolean> authResultHandler) {

        final JsonObject authRequest = AuthorizationConstants.getAuthorizationMsg(Constants.DEFAULT_SUBJECT, targetResource.toString(),
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
