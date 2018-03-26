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

package org.eclipse.hono.service.auth;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Future;

/**
 * An authorization service that makes authorization decisions based on <em>asserted claims</em>
 * contained in a {@link HonoUser}.
 *
 */
public final class ClaimsBasedAuthorizationService implements AuthorizationService {

    @Override
    public Future<Boolean> isAuthorized(final HonoUser user, final ResourceIdentifier resource, final Activity intent) {

        Objects.requireNonNull(user);
        Objects.requireNonNull(resource);
        Objects.requireNonNull(intent);

        if (user.isExpired()) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, "user information expired"));
        } else {
            return Future.succeededFuture(user.getAuthorities().isAuthorized(resource, intent));
        }
    }

    @Override
    public Future<Boolean> isAuthorized(final HonoUser user, final ResourceIdentifier resource, final String operation) {

        Objects.requireNonNull(user);
        Objects.requireNonNull(resource);
        Objects.requireNonNull(operation);

        if (user.isExpired()) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, "user information expired"));
        } else {
            return Future.succeededFuture(user.getAuthorities().isAuthorized(resource, operation));
        }
    }
}
