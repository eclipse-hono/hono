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

import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.auth.AuthProvider;


/**
 * An authentication provider for verifying credentials that also supports monitoring by
 * means of health checks.
 *
 * @param <T> The type of credentials that this provider can validate.
 */
public interface HonoClientBasedAuthProvider<T extends AbstractDeviceCredentials> extends AuthProvider {

    /**
     * Validates credentials provided by a device against the credentials on record
     * for the device.
     * <p>
     * The credentials on record are retrieved using Hono's
     *  <a href="https://www.eclipse.org/hono/docs/api/credentials-api/">Credentials API</a>.
     *
     * @param credentials The credentials provided by the device.
     * @param spanContext The SpanContext (may be null).
     * @param resultHandler The handler to notify about the outcome of the validation. If validation succeeds,
     *                      the result contains an object representing the authenticated device.
     * @throws NullPointerException if credentials or result handler are {@code null}.
     */
    void authenticate(T credentials, SpanContext spanContext, Handler<AsyncResult<DeviceUser>> resultHandler);
}
