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
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.service.amqp.AbstractAmqpEndpoint;
import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

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
@Component
@Scope("prototype")
public class AuthenticationEndpoint extends AbstractAmqpEndpoint<AuthenticationServerConfigProperties> {

    /**
     * Creates a new endpoint for a Vertx instance.
     * 
     * @param vertx The Vertx instance.
     */
    @Autowired
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
            HonoUser user = Constants.getClientPrincipal(con);
            sender.setQoS(ProtonQoS.AT_LEAST_ONCE).open();
            logger.debug("transferring token to client...");
            Message tokenMsg = ProtonHelper.message(user.getToken());
            MessageHelper.addProperty(tokenMsg, AuthenticationConstants.APPLICATION_PROPERTY_TYPE, AuthenticationConstants.TYPE_AMQP_JWT);
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

    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier targetAddress, final Message message) {
        return false;
    }
}
