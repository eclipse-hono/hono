/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.authentication;

import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.Source;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.AmqpEndpoint;
import org.eclipse.hono.service.amqp.AmqpServiceBase;
import org.eclipse.hono.service.auth.AddressAuthzHelper;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * An authentication server for clients that have been authenticated using SASL.
 * <p>
 * The server provides support for serving JSON Web Tokens (via the {@link AuthenticationEndpoint} if registered)
 * and for sending back the authenticated clients' authorities in accordance to the Qpid Dispatch Router's
 * <em>ADDRESS_AUTHZ</em> capability (via the {@link AddressAuthzHelper} implementation).
 */
public final class SimpleAuthenticationServer extends AmqpServiceBase<ServiceConfigProperties> {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleAuthenticationServer.class);
    private AuthenticationServerMetrics metrics = NoopAuthenticationServerMetrics.INSTANCE;

    /**
     * Sets the object to use for reporting metrics.
     *
     * @param metrics The metrics.
     * @throws NullPointerException if metrics is {@code null}.
     */
    public void setMetrics(final AuthenticationServerMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    protected String getServiceName() {
        return Constants.SERVICE_NAME_AUTH;
    }

    @Override
    protected void setRemoteConnectionOpenHandler(final ProtonConnection connection) {
        connection.sessionOpenHandler(remoteOpenSession -> handleSessionOpen(connection, remoteOpenSession));
        connection.senderOpenHandler(remoteOpenSender -> handleSenderOpen(connection, remoteOpenSender));
        // no receiverOpenHandler set here
        connection.disconnectHandler(con -> {
            con.close();
            con.disconnect();
        });
        connection.closeHandler(remoteClose -> {
            connection.close();
            connection.disconnect();
        });
        connection.openHandler(remoteOpen -> {
            if (remoteOpen.failed()) {
                LOG.debug("ignoring peer's open frame containing error", remoteOpen.cause());
            } else {
                processRemoteOpen(remoteOpen.result());
            }
        });
    }

    /**
     * Processes the AMQP <em>open</em> frame received from a peer.
     * <p>
     * Checks if the open frame contains a desired <em>ADDRESS_AUTHZ</em> capability and if so,
     * adds the authenticated clients' authorities to the properties of the open frame sent
     * to the peer in response.
     *
     * @param connection The connection opened by the peer.
     */
    @Override
    protected void processRemoteOpen(final ProtonConnection connection) {
        final AuthenticationServerMetrics.ClientType clientType;
        if (AddressAuthzHelper.isAddressAuthzCapabilitySet(connection)) {
            clientType = AuthenticationServerMetrics.ClientType.DISPATCH_ROUTER;
            LOG.debug("client [container: {}] requests transfer of authenticated user's authorities in open frame",
                    connection.getRemoteContainer());
            AddressAuthzHelper.processAddressAuthzCapability(connection);
        } else {
            clientType = AuthenticationServerMetrics.ClientType.AUTH_SERVICE;
        }
        connection.open();
        metrics.reportConnectionAttempt(AuthenticationService.AuthenticationAttemptOutcome.SUCCEEDED, clientType);
        vertx.setTimer(5000, closeCon -> {
            if (!connection.isDisconnected()) {
                LOG.debug("connection with client [{}] timed out after 5 seconds, closing connection",
                        connection.getRemoteContainer());
                connection.setCondition(ProtonHelper.condition(AmqpUtils.AMQP_ERROR_INACTIVITY,
                        "client must retrieve token within 5 secs after opening connection")).close();
            }
        });
    }

    @Override
    protected void handleReceiverOpen(final ProtonConnection con, final ProtonReceiver receiver) {
        receiver.setCondition(ProtonHelper.condition(AmqpError.NOT_ALLOWED, "cannot write to node"));
        receiver.close();
    }

    /**
     * Handles a request from a client to establish a link for receiving messages from this server.
     *
     * @param con the connection to the client.
     * @param sender the sender created for the link.
     */
    @Override
    protected void handleSenderOpen(final ProtonConnection con, final ProtonSender sender) {

        final Source remoteSource = sender.getRemoteSource();
        LOG.debug("client [{}] wants to open a link for receiving messages [address: {}]",
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
            if (AmqpUtils.SUBJECT_ANONYMOUS.equals(user.getName())) {
                con.setCondition(ProtonHelper.condition(
                        AmqpError.UNAUTHORIZED_ACCESS,
                        "client must authenticate using SASL"))
                    .close();
            } else {
                sender.setSource(sender.getRemoteSource());
                endpoint.onLinkAttach(con, sender, targetResource);
            }
        }
    }
}
