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
package org.eclipse.hono.messaging;

import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.Source;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.event.EventConstants;
import org.eclipse.hono.service.amqp.AmqpServiceBase;
import org.eclipse.hono.service.amqp.Endpoint;
import org.eclipse.hono.service.auth.AuthenticationConstants;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonSession;

/**
 * Hono Messaging is an AMQP 1.0 container that provides nodes for uploading <em>Telemetry</em> and
 * <em>Event</em> messages.
 */
@Component
@Scope("prototype")
public final class HonoMessaging extends AmqpServiceBase<HonoMessagingConfigProperties> {

    private ConnectionFactory authenticationService;

    /**
     * Sets the factory to use for creating an AMQP 1.0 connection to
     * the Authentication service.
     * 
     * @param factory The factory.
     * @throws NullPointerException if factory is {@code null}.
     */
    @Autowired
    @Qualifier(AuthenticationConstants.QUALIFIER_AUTHENTICATION)
    public void setAuthenticationServiceConnectionFactory(final ConnectionFactory factory) {
        authenticationService = Objects.requireNonNull(factory);
    }

    @Override
    protected Future<Void> preStartServers() {

        checkStandardEndpointsAreRegistered();
        logStartupMessage();
        return Future.succeededFuture();
    }

    private void checkStandardEndpointsAreRegistered() {
        if (getEndpoint(TelemetryConstants.TELEMETRY_ENDPOINT) == null) {
            LOG.warn("no Telemetry endpoint has been configured, Hono Messaging will not support Telemetry API");
        }
        if (getEndpoint(EventConstants.EVENT_ENDPOINT) == null) {
            LOG.warn("no Event endpoint has been configured, Hono Messaging will not support Event API");
        }
    }

    private void logStartupMessage() {
        if (LOG.isWarnEnabled()) {
            StringBuilder b = new StringBuilder()
                    .append("Hono Messaging does not yet support limiting the incoming message size ")
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
            LOG.info("client [container: {}, user: {}] connected", connection.getRemoteContainer(), Constants.getClientPrincipal(connection).getName());
            connection.open();
            // attach an ID so that we can later inform downstream components when connection is closed
            Constants.setConnectionId(connection, UUID.randomUUID().toString());
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

    private void publishConnectionClosedEvent(final ProtonConnection con) {

        String conId = con.attachments().get(Constants.KEY_CONNECTION_ID, String.class);
        if (conId != null) {
            vertx.eventBus().publish(
                    Constants.EVENT_BUS_ADDRESS_CONNECTION_CLOSED,
                    conId);
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

    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        for (Endpoint ep : endpoints()) {
            ep.registerReadinessChecks(handler);
        }
        handler.register("authentication-service-connection", status -> {
            if (authenticationService == null) {
                status.complete(Status.KO(new JsonObject().put("error", "no connection factory set for Authentication service")));
            } else {
                LOG.debug("checking connection to Authentication service");
                authenticationService.connect(null, null, null, s -> {
                    if (s.succeeded()) {
                        s.result().close();
                        status.complete(Status.OK());
                    } else {
                        status.complete(Status.KO(new JsonObject().put("error", "cannot connect to Authentication service")));
                    }
                });
            }
        });
    }
}
