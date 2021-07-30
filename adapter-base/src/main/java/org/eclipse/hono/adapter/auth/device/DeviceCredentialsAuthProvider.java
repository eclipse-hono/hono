/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.auth.device;

import org.eclipse.hono.service.auth.DeviceUser;

import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.AuthenticationProvider;

/**
 * An authentication provider for verifying credentials.
 *
 * @param <T> The type of credentials that this provider can validate.
 */
public interface DeviceCredentialsAuthProvider<T extends AbstractDeviceCredentials> extends AuthenticationProvider {

    /**
     * Validates credentials provided by a device against the credentials on record
     * for the device.
     * <p>
     * The credentials on record are retrieved using Hono's
     *  <a href="https://www.eclipse.org/hono/docs/api/credentials/">Credentials API</a>.
     *
     * @param credentials The credentials provided by the device.
     * @param spanContext The SpanContext (may be null).
     * @param resultHandler The handler to notify about the outcome of the validation. If validation succeeds,
     *                      the result contains an object representing the authenticated device.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void authenticate(T credentials, SpanContext spanContext, Handler<AsyncResult<DeviceUser>> resultHandler);

    /**
     * Creates device credentials from authentication information provided by a
     * device.
     * <p>
     * Subclasses need to create a concrete {@code DeviceCredentials} instance based on
     * the information contained in the JSON object.
     *
     * @param authInfo The credentials provided by the device. These usually get assembled via
     *            {@link AuthHandler#parseCredentials(org.eclipse.hono.util.ExecutionContext)}.
     * @return The device credentials or {@code null} if the auth info does not contain the required information.
     * @throws NullPointerException if auth info is {@code null}.
     */
    T getCredentials(JsonObject authInfo);
}
