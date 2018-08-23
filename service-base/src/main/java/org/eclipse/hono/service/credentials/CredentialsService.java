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

package org.eclipse.hono.service.credentials;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.CredentialsResult;

/**
 * A service for keeping record of device credentials.
 * This interface only covers mandatory operations.
 *
 * @see <a href="https://www.eclipse.org/hono/api/credentials-api/">Credentials API</a>
 */
public interface CredentialsService extends Verticle {

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
     * @see <a href="https://www.eclipse.org/hono/api/credentials-api/#get-credentials">
     *      Credentials API - Get Credentials</a>
     */
    void get(String tenantId, String type, String authId, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

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
     * @see <a href="https://www.eclipse.org/hono/api/credentials-api/#get-credentials">
     *      Credentials API - Get Credentials</a>
     */
    void get(String tenantId, String type, String authId, JsonObject clientContext, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);
}
