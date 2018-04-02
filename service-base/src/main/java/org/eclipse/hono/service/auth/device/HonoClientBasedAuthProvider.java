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

package org.eclipse.hono.service.auth.device;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.auth.AuthProvider;


/**
 * An authentication provider for verifying credentials that also supports monitoring by
 * means of health checks.
 *
 */
public interface HonoClientBasedAuthProvider extends AuthProvider {

    /**
     * Validates credentials provided by a device against the credentials on record
     * for the device.
     * <p>
     * The credentials on record are retrieved using Hono's
     *  <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
     *
     * @param credentials The credentials provided by the device.
     * @param resultHandler The handler to notify about the outcome of the validation. If validation succeeds,
     *                      the result contains an object representing the authenticated device.
     * @throws NullPointerException if credentials or result handler are {@code null}.
     */
    void authenticate(DeviceCredentials credentials, Handler<AsyncResult<Device>> resultHandler);
}
