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

package org.eclipse.hono.service.auth;

import java.util.Objects;

import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;


/**
 * An authorization service that delegates authorization to a remote service by sending
 * a message via the Vert.x event bus.
 *
 */
public final class EventBusBasedAuthorizationService implements AuthorizationService {

    private final Vertx vertx;

    /**
     * Creates a new service for a Vert.x instance.
     * 
     * @param vertx The vertx instance to use for sending messages.
     * @throws NullPointerException if vertx is {@code null}.
     */
    public EventBusBasedAuthorizationService(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    @Override
    public Future<Boolean> isAuthorized(final HonoUser user, final ResourceIdentifier resource, final Activity intent) {

        final Future<Boolean> result = Future.future();
        final JsonObject authRequest = AuthorizationConstants.getAuthorizationMsg(user.getName(), resource.toString(),
                intent.toString());
        vertx.eventBus().send(
            AuthorizationConstants.EVENT_BUS_ADDRESS_AUTHORIZATION_IN,
            authRequest,
            res -> {
                result.complete(res.succeeded() && AuthorizationConstants.ALLOWED.equals(res.result().body()));
            });
        return result;
    }

    @Override
    public Future<Boolean> isAuthorized(final HonoUser user, final ResourceIdentifier resource, final String operation) {
        return Future.succeededFuture(Boolean.FALSE);
    }
}
