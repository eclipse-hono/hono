/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.service.auth.device;

import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.util.ExecutionContext;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;


/**
 * An authentication handler for execution contexts.
 *
 * @param <T> The type of execution context that this handler supports.
 */
public interface AuthHandler<T extends ExecutionContext> {

    /**
     * Parses the credentials from the execution context into a JsonObject.
     * <p>
     * Implementations should be able to extract the required info for the
     * auth provider in the format the provider expects.
     *
     * @param context The execution context.
     * @return The credentials.
     * @throws NullPointerException if the context is {@code null}
     */
    Future<JsonObject> parseCredentials(T context);

    /**
     * Authenticates a device.
     *
     * @param context The execution context.
     * @return The authenticated device.
     * @throws NullPointerException if the context is {@code null}
     */
    Future<DeviceUser> authenticateDevice(T context);

    /**
     * Gets the auth provider that can be used to validate the credentials
     * parsed by this handler.
     *
     * @return The provider.
     */
    HonoClientBasedAuthProvider<?> getAuthProvider();
}
