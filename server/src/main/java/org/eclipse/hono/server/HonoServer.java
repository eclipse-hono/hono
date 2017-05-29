/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
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
import static org.eclipse.hono.service.authorization.AuthorizationConstants.EVENT_BUS_ADDRESS_AUTHORIZATION_IN;

import java.security.Principal;
import java.util.UUID;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.Source;
import org.apache.qpid.proton.engine.Record;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.AmqpServiceBase;
import org.eclipse.hono.service.amqp.Endpoint;
import org.eclipse.hono.service.authorization.AuthorizationConstants;
import org.eclipse.hono.service.authorization.Permission;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonSession;

/**
 * The Hono server is an AMQP 1.0 container that provides endpoints for the <em>Telemetry</em>,
 * <em>Command &amp; Control</em> and <em>Device Registration</em> APIs that <em>Protocol Adapters</em> and
 * <em>Solutions</em> use to interact with devices.
 */
@Component
@Scope("prototype")
public final class HonoServer extends AmqpServiceBase<ServiceConfigProperties> {

    @Override
    protected Future<Void> preStartServers() {

        checkStandardEndpointsAreRegistered();
        logStartupMessage();
        return Future.succeededFuture();
    }

    private void checkStandardEndpointsAreRegistered() {
        if (getEndpoint(TelemetryConstants.TELEMETRY_ENDPOINT) == null) {
            LOG.warn("no Telemetry endpoint has been configured, Hono server will not support Telemetry API");
        }
        if (getEndpoint(RegistrationConstants.REGISTRATION_ENDPOINT) == null) {
            LOG.warn("no Registration endpoint has been configured, Hono server will not support Registration API");
        }
    }

    private void logStartupMessage() {
        if (LOG.isWarnEnabled()) {
            StringBuilder b = new StringBuilder()
                    .append("Hono server does not yet support limiting the incoming message size ")
                    .append("via the maxPayloadSize property");
            LOG.warn(b.toString());
        }
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

    protected void onRemoteConnectionOpen(final ProtonConnection connection) {
        connection.setContainer(String.format("Hono-%s:%d", getBindAddress(), getPort()));
        setRemoteConnectionOpenHandler(connection);
    }

    protected void onRemoteConnectionOpenInsecurePort(final ProtonConnection connection) {
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
    private String getUserFromConnection(final ProtonConnection con) {

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

    private void checkAuthorizationToAttach(final String user, final ResourceIdentifier targetResource, final Permission permission,
       final Handler<Boolean> authResultHandler) {

        final JsonObject authRequest = AuthorizationConstants.getAuthorizationMsg(user, targetResource.toString(),
           permission.toString());
        vertx.eventBus().send(
           EVENT_BUS_ADDRESS_AUTHORIZATION_IN,
           authRequest,
           res -> authResultHandler.handle(res.succeeded() && AuthorizationConstants.ALLOWED.equals(res.result().body())));
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
