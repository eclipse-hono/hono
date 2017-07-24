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

package org.eclipse.hono.service.auth.impl;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.Source;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.service.amqp.AmqpServiceBase;
import org.eclipse.hono.service.amqp.Endpoint;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.vertx.core.AsyncResult;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonSession;


/**
 * An authentication server serving JSON Web Tokens to clients that have been authenticated using SASL.
 *
 */
@Component
@Scope("prototype")
public class SimpleAuthenticationServer extends AmqpServiceBase<AuthenticationServerConfigProperties> {

    @Override
    protected String getServiceName() {
        return "Hono-Auth";
    }

    protected void onRemoteConnectionOpenInsecurePort(final ProtonConnection connection) {
        connection.setContainer(String.format("%s-%s:%d", getServiceName(), getInsecurePortBindAddress(), getInsecurePort()));
        setRemoteConnectionOpenHandler(connection);
    };

    private void setRemoteConnectionOpenHandler(final ProtonConnection connection) {

        connection.sessionOpenHandler(remoteOpenSession -> handleSessionOpen(connection, remoteOpenSession));
        connection.receiverOpenHandler(remoteOpenReceiver -> {
            remoteOpenReceiver.setCondition(ProtonHelper.condition(AmqpError.NOT_ALLOWED, "no such node"));
        });
        connection.senderOpenHandler(remoteOpenSender -> handleSenderOpen(connection, remoteOpenSender));
        connection.disconnectHandler(this::handleRemoteDisconnect);
        connection.closeHandler(remoteClose -> handleRemoteConnectionClose(connection, remoteClose));
        connection.openHandler(remoteOpen -> {
            LOG.info("client [container: {}, user: {}] connected", connection.getRemoteContainer(), Constants.getClientPrincipal(connection).getName());
            connection.open();
        });
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
                LOG.debug("no endpoint registered for node [{}]", targetResource);
                con.setCondition(ProtonHelper.condition(AmqpError.NOT_FOUND, "no such node")).close();
            } else {
                HonoUser user = Constants.getClientPrincipal(con);
                if (Constants.SUBJECT_ANONYMOUS.equals(user.getName())) {
                    con.setCondition(ProtonHelper.condition(AmqpError.UNAUTHORIZED_ACCESS, "client must authenticate using SASL")).close();
                } else {
                    Constants.copyProperties(con, sender);
                    sender.setSource(sender.getRemoteSource());
                    endpoint.onLinkAttach(con, sender, targetResource);
                    vertx.setTimer(5000, closeCon -> {
                        if (!con.isDisconnected()) {
                            LOG.debug("connection with client [{}] timed out after 5 seconds, closing connection", con.getRemoteContainer());
                            con.setCondition(ProtonHelper.condition("hono: inactivity", "client must retrieve token within 5 secs after opening connection")).close();
                        }
                    });
                }
            }
        } catch (final IllegalArgumentException e) {
            LOG.debug("client has provided invalid resource identifier as source address", e);
            con.setCondition(ProtonHelper.condition(AmqpError.INVALID_FIELD, "malformed source address")).close();
        }
    }
}
