/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.credentials;

import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.RequestResponseEndpoint;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Vertx;

/**
 * An {@code AmqpEndpoint} for managing device credential information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
 * It receives AMQP 1.0 messages representing requests and sends them to an address on the vertx
 * event bus for processing. The outcome is then returned to the peer in a response message.
 */
public class CredentialsAmqpEndpoint extends RequestResponseEndpoint<ServiceConfigProperties> {

    /**
     * Creates a new credentials endpoint for a vertx instance.
     *
     * @param vertx The vertx instance to use.
     */
    @Autowired
    public CredentialsAmqpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }


    @Override
    public final String getName() {
        return CredentialsConstants.CREDENTIALS_ENDPOINT;
    }


    @Override
    public final void processRequest(final Message msg, final ResourceIdentifier targetAddress, final HonoUser clientPrincipal) {

        final EventBusMessage credentialsMsg = EventBusMessage.forOperation(msg)
                .setReplyToAddress(msg)
                .setAppCorrelationId(msg)
                .setCorrelationId(msg)
                .setTenant(targetAddress.getTenantId())
                .setJsonPayload(msg);

        vertx.eventBus().send(CredentialsConstants.EVENT_BUS_ADDRESS_CREDENTIALS_IN, credentialsMsg.toJson());
    }

    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier linkTarget, final Message msg) {
        return CredentialsMessageFilter.verify(linkTarget, msg);
    }

    @Override
    protected final Message getAmqpReply(final EventBusMessage message) {
        return CredentialsConstants.getAmqpReply(CredentialsConstants.CREDENTIALS_ENDPOINT, message);
    }
}
