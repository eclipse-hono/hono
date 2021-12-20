/*******************************************************************************
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.auth.Device;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authorization.Authorization;

/**
 * A class that implements the {@link User} interface for devices that then can be used for authorization validation.
 *
*/
public class DeviceUser extends Device implements User {

    /**
     * Creates a new device (usable as a {@link User}) for a tenant and device identifier.
     *
     * @param tenantId The tenant.
     * @param deviceId The device identifier.
     * @throws NullPointerException if any of the params is {@code null}.
     */
    public DeviceUser(final String tenantId, final String deviceId) {
        super(tenantId, deviceId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JsonObject attributes() {
        return principal();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public User isAuthorized(final Authorization authority, final Handler<AsyncResult<Boolean>> resultHandler) {
        resultHandler.handle(Future.failedFuture("the isAuthorized(Authorization, Handler) method is not implemented"));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAuthProvider(final AuthProvider authProvider) {
        // nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public User merge(final User other) {
        // the device has no attributes that would need to be merged
        return this;
    }
}
