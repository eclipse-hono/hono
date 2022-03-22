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

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.service.amqp.AbstractAmqpEndpoint;
import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;


/**
 * An endpoint supporting the retrieval of a token containing security claims.
 *
 */
public class AuthenticationEndpoint extends AbstractAmqpEndpoint<Object> {

    /**
     * Creates a new endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance.
     */
    public AuthenticationEndpoint(final Vertx vertx) {
        super(vertx);
    }

    @Override
    public final String getName() {
        return AuthenticationConstants.ENDPOINT_NAME_AUTHENTICATION;
    }

    @Override
    public final void onLinkAttach(final ProtonConnection con, final ProtonSender sender, final ResourceIdentifier targetResource) {

        if (ProtonQoS.AT_LEAST_ONCE.equals(sender.getRemoteQoS())) {
            final HonoUser user = AmqpUtils.getClientPrincipal(con);
            sender.setQoS(ProtonQoS.AT_LEAST_ONCE).open();
            logger.debug("transferring token to client...");
            final Message tokenMsg = ProtonHelper.message(user.getToken());
            AmqpUtils.addProperty(tokenMsg, AuthenticationConstants.APPLICATION_PROPERTY_TYPE, AuthenticationConstants.TYPE_AMQP_JWT);
            sender.send(tokenMsg, disposition -> {
                if (disposition.remotelySettled()) {
                    logger.debug("successfully transferred auth token to client");
                } else {
                    logger.debug("failed to transfer auth token to client");
                }
                sender.close();
            });
        } else {
            onLinkDetach(sender, ProtonHelper.condition(AmqpError.INVALID_FIELD, "supports AT_LEAST_ONCE delivery mode only"));
        }
    }

    @Override
    public final void onLinkAttach(final ProtonConnection con, final ProtonReceiver receiver, final ResourceIdentifier targetResource) {
        super.onLinkAttach(con, receiver, targetResource);
    }

    /**
     * Handles a closed connection.
     * <p>
     * This implementation does nothing.
     * </p>
     *
     * @param connection The connection which got closed.
     */
    @Override
    public void onConnectionClosed(final ProtonConnection connection) {
        // we have nothing to clean up
    }
}
