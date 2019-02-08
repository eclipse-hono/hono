/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.auth;

import static org.eclipse.hono.util.AuthenticationConstants.EVENT_BUS_ADDRESS_AUTHENTICATION_IN;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.ConfigurationSupportingVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

/**
 * Base class for implementing an authentication service that can be deployed to a Vert.x container.
 * <p>
 * Provides support for receiving and processing authentication requests received via Vert.x event bus.
 * <p>
 * This class registers a listener for address {@link AuthenticationConstants#EVENT_BUS_ADDRESS_AUTHENTICATION_IN}.
 * Messages are expected to be {@code JsonObject} typed. Apart from that this class makes no assumption regarding
 * the content of the message. The listener simply invokes the {@link #authenticate(JsonObject, io.vertx.core.Handler)}
 * method with the received message. If the handler succeeds, the result is sent back via the event bus as a reply
 * to the original authentication request. Otherwise, a failure reply is sent using the error code from the handler
 * exception.
 * 
 * @param <T> The type of configuration properties this service supports.
 */
public abstract class BaseAuthenticationService<T> extends ConfigurationSupportingVerticle<T> implements AuthenticationService {

    private static final Logger LOG = LoggerFactory.getLogger(BaseAuthenticationService.class);
    private MessageConsumer<JsonObject> authRequestConsumer;

    @Override
    public final void start(final Future<Void> startFuture) {
        authRequestConsumer = vertx.eventBus().consumer(EVENT_BUS_ADDRESS_AUTHENTICATION_IN, this::processMessage);
        LOG.info("listening on event bus [address: {}] for authentication requests", EVENT_BUS_ADDRESS_AUTHENTICATION_IN);
        doStart(startFuture);
    }

    /**
     * Subclasses should override this method to create required resources
     * during startup.
     * <p>
     * This implementation always completes the start future.
     * 
     * @param startFuture Completes if startup succeeded.
     */
    protected void doStart(final Future<Void> startFuture) {
        // should be overridden by subclasses
        startFuture.complete();
    }

    @Override
    public final void stop(final Future<Void> stopFuture) {
        LOG.info("unregistering event bus listener [address: {}]", EVENT_BUS_ADDRESS_AUTHENTICATION_IN);
        authRequestConsumer.unregister();
        doStop(stopFuture);
    }

    /**
     * Subclasses should override this method to release resources
     * during shutdown.
     * <p>
     * This implementation always completes the stop future.
     * 
     * @param stopFuture Completes if shutdown succeeded.
     */
    protected void doStop(final Future<Void> stopFuture) {

        // to be overridden by subclasses
        stopFuture.complete();
    }

    private void processMessage(final Message<JsonObject> message) {
        final JsonObject body = message.body();
        authenticate(body, validation -> {
            if (validation.succeeded()) {
                message.reply(AuthenticationConstants.getAuthenticationReply(validation.result().getToken()));
            } else {
                if (validation.cause() instanceof ServiceInvocationException) {
                    message.fail(((ServiceInvocationException) validation.cause()).getErrorCode(), validation.cause().getMessage());
                } else {
                    LOG.debug("unable to get status code from non-ServiceInvocationException", validation.cause());
                    message.fail(HttpURLConnection.HTTP_INTERNAL_ERROR, validation.cause().getMessage());
                }
            }
        });
    }

}
