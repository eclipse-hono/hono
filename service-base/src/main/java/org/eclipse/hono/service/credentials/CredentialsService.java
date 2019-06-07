/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.credentials;

import org.eclipse.hono.util.CredentialsResult;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * A service for keeping record of device credentials.
 * This interface only covers mandatory operations.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/latest/api/credentials-api/">Credentials API</a>
 */
public interface CredentialsService {

    /**
     * Gets credentials for a device.
     *
     * @param tenantId The tenant the device belongs to.
     * @param type The type of credentials to get.
     * @param authId The authentication identifier of the device to get credentials for (may be {@code null}.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>200 OK</em> if credentials of the given type and authentication identifier have been found. The
     *         <em>payload</em> will contain the credentials.</li>
     *         <li><em>404 Not Found</em> if no credentials matching the criteria exist.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/latest/api/credentials-api/#get-credentials">
     *      Credentials API - Get Credentials</a>
     */
    default void get(final String tenantId, final String type, final String authId,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        get(tenantId, type, authId, NoopSpan.INSTANCE, resultHandler);
    }

    /**
     * Gets credentials for a device.
     * <p>
     * This default implementation simply returns the result of {@link #get(String, String, String, Handler)}.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param type The type of credentials to get.
     * @param authId The authentication identifier of the device to get credentials for (may be {@code null}).
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>200 OK</em> if credentials of the given type and authentication identifier have been found. The
     *         <em>payload</em> will contain the credentials.</li>
     *         <li><em>404 Not Found</em> if no credentials matching the criteria exist.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/latest/api/credentials-api/#get-credentials">
     *      Credentials API - Get Credentials</a>
     */
    void get(String tenantId, String type, String authId, Span span,
            Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

    /**
     * Gets credentials for a device, providing additional client connection context.
     *
     * @param tenantId The tenant the device belongs to.
     * @param type The type of credentials to get.
     * @param authId The authentication identifier of the device to get credentials for (may be {@code null}.
     * @param clientContext Optional bag of properties that can be used to identify the device
     * @param resultHandler The handler to invoke with the result of the operation.
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>200 OK</em> if credentials of the given type and authentication identifier have been found. The
     *         <em>payload</em> will contain the credentials.</li>
     *         <li><em>404 Not Found</em> if no credentials matching the criteria exist.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/latest/api/credentials-api/#get-credentials">
     *      Credentials API - Get Credentials</a>
     */
    default void get(final String tenantId, final String type, final String authId, final JsonObject clientContext,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        get(tenantId, type, authId, clientContext, NoopSpan.INSTANCE, resultHandler);
    }

    /**
     * Gets credentials for a device, providing additional client connection context.
     * <p>
     * This default implementation simply returns the result of {@link #get(String, String, String, JsonObject, Handler)}.
     *
     * @param tenantId The tenant the device belongs to.
     * @param type The type of credentials to get.
     * @param authId The authentication identifier of the device to get credentials for (may be {@code null}).
     * @param clientContext Optional bag of properties that can be used to identify the device
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>200 OK</em> if credentials of the given type and authentication identifier have been found. The
     *         <em>payload</em> will contain the credentials.</li>
     *         <li><em>404 Not Found</em> if no credentials matching the criteria exist.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/latest/api/credentials-api/#get-credentials">
     *      Credentials API - Get Credentials</a>
     */
    void get(String tenantId, String type, String authId, JsonObject clientContext, Span span,
            Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

}
