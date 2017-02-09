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
package org.eclipse.hono.authentication.impl;

import static org.eclipse.hono.authentication.AuthenticationConstants.*;

import org.eclipse.hono.authentication.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

/**
 * Base class for implementing an {@code AuthenticationService}.
 * <p>
 * Provides support for receiving and processing authentication requests received via Vert.x event bus.
 */
public abstract class BaseAuthenticationService extends AbstractVerticle implements AuthenticationService
{
    private static final Logger LOG = LoggerFactory.getLogger(BaseAuthenticationService.class);
    private MessageConsumer<JsonObject> authRequestConsumer;

    @Override
    public final void start(final Future<Void> startFuture) throws Exception {
        String listenAddress = EVENT_BUS_ADDRESS_AUTHENTICATION_IN;
        authRequestConsumer = vertx.eventBus().consumer(listenAddress);
        authRequestConsumer.handler(this::processMessage);
        LOG.info("listening on event bus [address: {}] for incoming authentication messages", listenAddress);
        doStart(startFuture);
    }

    protected void doStart(final Future<Void> startFuture) throws Exception
    {
        // should be overridden by subclasses
        startFuture.complete();
    }

    @Override
    public final void stop(final Future<Void> stopFuture) throws Exception {
        authRequestConsumer.unregister();
        doStop(stopFuture);
    }

    protected void doStop(final Future<Void> stopFuture) throws Exception
    {
        // to be overridden by subclasses
        stopFuture.complete();
    }

    private void processMessage(final Message<JsonObject> message) {
        final JsonObject body = message.body();
        LOG.debug("received authentication request: {}", body);
        final String mechanism = body.getString(FIELD_MECHANISM);
        if (!isSupported(mechanism)) {
            replyWithError(message, ERROR_CODE_UNSUPPORTED_MECHANISM, "unsupported SASL mechanism");
        } else {
            final byte[] response = body.getBinary(FIELD_RESPONSE);

            validateResponse(mechanism, response, validation -> {
                if (validation.succeeded()) {
                    replyWithAuthorizationId(message, validation.result());
                } else {
                    replyWithError(message, ERROR_CODE_AUTHENTICATION_FAILED, validation.cause().getMessage());
                }
            });
        }
    }

    private void replyWithError(final Message<JsonObject> request, final int errorCode, final String message) {
        request.fail(errorCode, message);
    }

    private void replyWithAuthorizationId(final Message<JsonObject> message, final String authzId) {
        message.reply(new JsonObject().put(FIELD_AUTHORIZATION_ID, authzId));
    }
}
