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
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

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
     * Checks if this device has a particular authority.
     * <p>
     * In order for the check to succeed, the JWT must
     * <ul>
     * <li>not be expired</li>
     * <li>contain the given authorities in its <em>aut</em> claim</li>
     * </ul>
     *
     * @param authority The authority to check for.
     * @param resultHandler The handler to notify about the outcome of the check.
     * @return Device the device that was checked for authorization.
     */
    @Override
    public User isAuthorized(final String authority, final Handler<AsyncResult<Boolean>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(checkAuthorization(authority)));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public User clearCache() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAuthProvider(final AuthProvider authProvider) {
        // NOOP, JWT is self contained
    }
}
