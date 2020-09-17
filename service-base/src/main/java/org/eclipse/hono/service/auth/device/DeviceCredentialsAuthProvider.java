/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth.device;

import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.util.ExecutionContext;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;

/**
 * An authentication provider for verifying credentials.
 *
 * @param <T> The type of credentials that this provider can validate.
 */
public interface DeviceCredentialsAuthProvider<T extends AbstractDeviceCredentials> extends AuthProvider {

    /**
     * Validates credentials provided by a device against the credentials on record
     * for the device.
     * <p>
     * The credentials on record are retrieved using Hono's
     *  <a href="https://www.eclipse.org/hono/docs/api/credentials/">Credentials API</a>.
     *
     * @param credentials The credentials provided by the device.
     * @param executionContext The execution context concerning the request of the device.
     * @param resultHandler The handler to notify about the outcome of the validation. If validation succeeds,
     *                      the result contains an object representing the authenticated device.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void authenticate(T credentials, ExecutionContext executionContext, Handler<AsyncResult<DeviceUser>> resultHandler);

    /**
     * Authenticates a device.
     * <p>
     * The first argument is a JSON object containing information for authenticating the device. What this actually
     * contains depends on the specific implementation. In the case of a simple username/password based authentication
     * it is likely to contain a JSON object with the following structure:
     * <pre>
     *   {
     *     "username": "tim",
     *     "password": "mypassword"
     *   }
     * </pre>
     * For other types of authentication it contain different information - for example a JWT token or OAuth bearer
     * token.
     * <p>
     * If the device is successfully authenticated a {@link DeviceUser} object is passed to the handler in an
     * {@link io.vertx.core.AsyncResult}.
     *
     * @param authInfo The auth information.
     * @param executionContext The execution context concerning the request of the device.
     * @param resultHandler The result handler.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    default void authenticate(
            final JsonObject authInfo,
            final ExecutionContext executionContext,
            final Handler<AsyncResult<DeviceUser>> resultHandler) {
        authenticate(authInfo, executionContext, resultHandler, null);
    }

    /**
     * Authenticates a device.
     * <p>
     * The first argument is a JSON object containing information for authenticating the device. What this actually
     * contains depends on the specific implementation. In the case of a simple username/password based authentication
     * it is likely to contain a JSON object with the following structure:
     * <pre>
     *   {
     *     "username": "tim",
     *     "password": "mypassword"
     *   }
     * </pre>
     * For other types of authentication it contain different information - for example a JWT token or OAuth bearer
     * token.
     * <p>
     * If the device is successfully authenticated a {@link DeviceUser} object is passed to the handler in an
     * {@link io.vertx.core.AsyncResult}.
     *
     * @param <S> The ExecutionContext type.
     * @param authInfo The auth information.
     * @param executionContext The execution context concerning the request of the device.
     * @param resultHandler The result handler.
     * @param preCredentialsValidationHandler An optional handler to invoke after the credentials got determined and
     *            before they get validated. Can be used to perform checks using the credentials and tenant information
     *            before the potentially expensive credentials validation is done. A failed future returned by the
     *            handler will fail the given resultHandler.
     * @throws NullPointerException if any of the parameters except preCredentialsValidationHandler is {@code null}.
     */
    <S extends ExecutionContext> void authenticate(
            JsonObject authInfo,
            S executionContext,
            Handler<AsyncResult<DeviceUser>> resultHandler,
            PreCredentialsValidationHandler<S> preCredentialsValidationHandler);
}
