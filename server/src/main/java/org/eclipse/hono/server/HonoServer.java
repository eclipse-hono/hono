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

import org.eclipse.hono.authorization.AuthorizationConstants;
import org.eclipse.hono.authorization.Permission;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonServer;
import io.vertx.proton.ProtonServerOptions;

/**
 * The Hono server is an AMQP 1.0 container that provides endpoints for the <em>Telemetry</em>,
 * <em>Command &amp; Control</em> and <em>Device Registration</em> APIs that <em>Protocol Adapters</em> and
 * <em>Solutions</em> use to interact with devices.
 */
@Component
public final class HonoServer extends AbstractVerticle {

    private static final Logger   LOG = LoggerFactory.getLogger(HonoServer.class);
    private String                host;
    private int                   port;
    private boolean               singleTenant;
    private ProtonServer          server;
    private Map<String, Endpoint> endpoints = new HashMap<>();

    @Override
    public void start(final Future<Void> startupHandler) {
        if (!isTelemetryEndpointConfigured()) {
            LOG.warn("No Telemetry endpoint has been configured, aborting start up ...");
            startupHandler.fail("Telemetry endpoint must be configured");
        } else {
            final ProtonServerOptions options = createServerOptions();
            server = ProtonServer.create(vertx, options)
                    .connectHandler(this::helloProcessConnection)
                    .listen(port, host, bindAttempt -> {
                        if (bindAttempt.succeeded()) {
                            this.port = bindAttempt.result().actualPort();
                            LOG.info("HonoServer running at [{}:{}]", host, this.port);
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

    @Autowired
    public void setEndpoints(final List<Endpoint> definedEndpoints) {
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
     * Sets the port Hono will listen on for AMQP 1.0 connections.
     * <p>
     * If set to 0 Hono will bind to an arbitrary free port chosen by the operating system.
     * </p>
     *
     * @param port the port to bind to.
     */
    @Value(value = "${hono.server.port}")
    public void setPort(int port) {
        this.port = port;
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
        return this.port;
    }

    @Value(value = "${hono.server.bindaddress}")
    public void setHost(final String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    /**
     * @return the singleTenant
     */
    public boolean isSingleTenant() {
        return singleTenant;
    }

    /**
     * @param singleTenant the singleTenant to set
     */
    @Value(value = "${hono.single.tenant:false}")
    public void setSingleTenant(final boolean singleTenant) {
        this.singleTenant = singleTenant;
    }

    void helloProcessConnection(final ProtonConnection connection) {
        connection.sessionOpenHandler(session -> session.open());
        connection.receiverOpenHandler(openedReceiver -> handleReceiverOpen(connection, openedReceiver));
        connection.disconnectHandler(HonoServer::handleDisconnected);
        connection.closeHandler(HonoServer::handleConnectionClosed);
        connection.openHandler(result -> {
            LOG.debug("Client [{}:{}] connected", connection.getRemoteHostname(), connection.getRemoteContainer());
            connection.setContainer(String.format("Hono-%s:%d", this.host, server.actualPort())).open();
        });
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
            Endpoint endpoint = getEndpoint(targetResource);
            if (endpoint == null) {
                LOG.info("no matching endpoint registered for address [{}]", receiver.getRemoteTarget().getAddress());
                receiver.close();
            } else {
                checkAuthorizationToAttach(targetResource, isAuthorized -> {
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

    private Endpoint getEndpoint(final ResourceIdentifier targetAddress) {
        return endpoints.get(targetAddress.getEndpoint());
    }

    private void checkAuthorizationToAttach(final ResourceIdentifier targetResource, final Handler<Boolean> authResultHandler) {
        final JsonObject authRequest = AuthorizationConstants.getAuthorizationMsg(Constants.DEFAULT_SUBJECT, targetResource.toString(), Permission.WRITE.toString());
        vertx.eventBus().send(
                EVENT_BUS_ADDRESS_AUTHORIZATION_IN,
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
}
