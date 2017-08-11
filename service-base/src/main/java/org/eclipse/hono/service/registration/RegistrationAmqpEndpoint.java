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
package org.eclipse.hono.service.registration;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.RequestResponseEndpoint;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * An {@code AmqpEndpoint} for managing device registration information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/api/Device-Registration-API/">Device Registration API</a>.
 * It receives AMQP 1.0 messages representing requests and sends them to an address on the vertx
 * event bus for processing. The outcome is then returned to the peer in a response message.
 */
public final class RegistrationAmqpEndpoint extends RequestResponseEndpoint<ServiceConfigProperties> {

    /**
     * Creates a new registration endpoint for a vertx instance.
     * 
     * @param vertx The vertx instance to use.
     */
    @Autowired
    public RegistrationAmqpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    public String getName() {
        return RegistrationConstants.REGISTRATION_ENDPOINT;
    }

    @Override
    public void processRequest(final Message msg, final ResourceIdentifier targetAddress, final HonoUser clientPrincipal) {

        final JsonObject registrationMsg = RegistrationConstants.getRegistrationMsg(msg);

        vertx.eventBus().send(RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_IN, registrationMsg,
                result -> {
                    JsonObject response = null;
                    if (result.succeeded()) {
                        // TODO check for correct session here...?
                        response = (JsonObject) result.result().body();
                    } else {
                        logger.debug("failed to process request [msg ID: {}] due to {}", msg.getMessageId(), result.cause());
                        // we need to inform client about failure
                        response = RegistrationConstants.getServiceReplyAsJson(
                                HttpURLConnection.HTTP_INTERNAL_ERROR,
                                MessageHelper.getTenantIdAnnotation(msg),
                                MessageHelper.getDeviceIdAnnotation(msg),
                                null);
                    }
                    addHeadersToResponse(msg, response);
                    vertx.eventBus().send(msg.getReplyTo(), response);
                });
    }

    @Override
    protected boolean passesFormalVerification(ResourceIdentifier linkTarget, Message msg) {
        return RegistrationMessageFilter.verify(linkTarget, msg);
    }

    @Override
    protected Message getAmqpReply(io.vertx.core.eventbus.Message<JsonObject> message) {
        return RegistrationConstants.getAmqpReply(RegistrationConstants.REGISTRATION_ENDPOINT, message.body());
    }
}
