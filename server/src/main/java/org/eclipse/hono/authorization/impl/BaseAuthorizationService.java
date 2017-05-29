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
package org.eclipse.hono.authorization.impl;

import static org.eclipse.hono.service.authorization.AuthorizationConstants.*;

import java.util.Objects;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.authorization.AuthorizationService;
import org.eclipse.hono.service.authorization.Permission;
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
 * Provides support for processing authorization requests via Vert.x event bus.
 * </p>
 */
public abstract class BaseAuthorizationService extends AbstractVerticle implements AuthorizationService
{
    private static final Logger LOG = LoggerFactory.getLogger(BaseAuthorizationService.class);
    private MessageConsumer<JsonObject> authRequestConsumer;
    protected ServiceConfigProperties honoConfig = new ServiceConfigProperties();

    /**
     * Sets the global Hono configuration properties.
     * 
     * @param props The properties.
     * @throws NullPointerException if props is {@code null}.
     */
    @Autowired(required = false)
    public void setHonoConfiguration(final ServiceConfigProperties props) {
        this.honoConfig = Objects.requireNonNull(props);
    }

    @Override
    public final void start(final Future<Void> startFuture) throws Exception {
        String listenAddress = EVENT_BUS_ADDRESS_AUTHORIZATION_IN;
        authRequestConsumer = vertx.eventBus().consumer(listenAddress);
        authRequestConsumer.handler(this::processMessage);
        LOG.info("listening on event bus [address: {}] for incoming auth messages", listenAddress);
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
        final String authSubject = body.getString(AUTH_SUBJECT_FIELD);
        final Permission permission = Permission.valueOf(body.getString(PERMISSION_FIELD));
        final ResourceIdentifier resource = ResourceIdentifier.fromString(body.getString(RESOURCE_FIELD));

        boolean hasPermission = hasPermission(authSubject, resource, permission);
        message.reply(hasPermission ? ALLOWED : DENIED);
        LOG.debug("subject [{}] is {}allowed to {} on resource [{}]", authSubject,
                hasPermission ? "" : "not ", permission, resource);
    }
}
