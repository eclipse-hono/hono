/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.auth;

import java.util.Objects;

import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.auth.HonoUserAdapter;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

/**
 * Base class for implementing an {@code AuthorizationService}.
 * <p>
 * Provides support for processing authorization requests received via Vert.x event bus.
 * <p>
 * This service expects requests as JSON messages sent to address {@link AuthorizationConstants#EVENT_BUS_ADDRESS_AUTHORIZATION_IN}.
 * The messages must comply with the following syntax:
 * <pre>
 * {
 *   "auth-subject": "client-id",
 *   "permission": "READ",
 *   "resource": "telemetry/DEFAULT_TENANT"
 * }
 * </pre>
 * <p>
 * The authorization result is returned as a reply to the original request message and contains a single string
 * (either {@link AuthorizationConstants#ALLOWED} or {@link AuthorizationConstants#DENIED}) indicating the
 * result of the authorization request.
 */
public abstract class BaseAuthorizationService extends AbstractVerticle implements AuthorizationService
{
    private static final Logger LOG = LoggerFactory.getLogger(BaseAuthorizationService.class);
    private MessageConsumer<JsonObject> authRequestConsumer;
    private ServiceConfigProperties config = new ServiceConfigProperties();

    /**
     * Sets the service configuration properties.
     * 
     * @param props The properties.
     * @throws NullPointerException if props is {@code null}.
     */
    @Autowired(required = false)
    public final void setConfig(final ServiceConfigProperties props) {
        this.config = Objects.requireNonNull(props);
    }

    /**
     * Gets the service configuration properties.
     * 
     * @return The properties.
     */
    public final ServiceConfigProperties getConfig() {
        return config;
    }

    @Override
    public final void start(final Future<Void> startFuture) {
        String listenAddress = AuthorizationConstants.EVENT_BUS_ADDRESS_AUTHORIZATION_IN;
        authRequestConsumer = vertx.eventBus().consumer(listenAddress);
        authRequestConsumer.handler(this::processMessage);
        LOG.info("listening on event bus [address: {}] for incoming auth messages", listenAddress);
        doStart(startFuture);
    }

    /**
     * Invoked by {@link #start()} as part of the start-up of this service.
     * <p>
     * Subclasses should override this method to perform any work specific to the start-up of this service.
     * <p>
     * This default implementation simply completes the future.
     * 
     * @param startFuture The future to complete on successful start-up.
     */
    protected void doStart(final Future<Void> startFuture) {
        // should be overridden by subclasses
        startFuture.complete();
    }

    @Override
    public final void stop(final Future<Void> stopFuture) {
        authRequestConsumer.unregister();
        doStop(stopFuture);
    }

    /**
     * Invoked by {@link #stop()} as part of the shut down of this service.
     * <p>
     * Subclasses should override this method to perform any work specific to the shut down of this service.
     * <p>
     * This default implementation simply completes the future.
     *
     * @param stopFuture the future to invoke once shutdown is complete.
     */
    protected void doStop(final Future<Void> stopFuture) {
        // to be overridden by subclasses
        stopFuture.complete();
    }

    private void processMessage(final Message<JsonObject> message) {
        final JsonObject body = message.body();
        final String authSubject = body.getString(AuthorizationConstants.FIELD_AUTH_SUBJECT);
        final HonoUser user = new HonoUserAdapter() {
            @Override
            public String getName() {
                return authSubject;
            }
        };
        final Activity permission = Activity.valueOf(body.getString(AuthorizationConstants.FIELD_PERMISSION));
        final ResourceIdentifier resource = ResourceIdentifier.fromString(body.getString(AuthorizationConstants.FIELD_RESOURCE));

        isAuthorized(user, resource, permission).setHandler(authAttempt -> {
            boolean hasPermission = false;
            if (authAttempt.succeeded()) {
                hasPermission = authAttempt.result();
            }
            LOG.debug("subject [{}] is {}allowed to {} on resource [{}]", authSubject,
                    hasPermission ? "" : "not ", permission, resource);
            message.reply(hasPermission ? AuthorizationConstants.ALLOWED : AuthorizationConstants.DENIED);
        });
    }
}
