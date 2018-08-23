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
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.CredentialsResult;

/**
 * A service for keeping record of device credentials.
 * This interface presents all the available operations on the API.
 *
 * @see <a href="https://www.eclipse.org/hono/api/credentials-api/">Credentials API</a>
 */
public interface CompleteCredentialsService extends CredentialsService {

    /**
     * Adds credentials for a device.
     *
     * @param tenantId The tenant the device belongs to.
     * @param credentialsObject The credentials to add.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>201 Created</em> if the credentials have been added successfully.
     *         <li><em>400 Bad Request</em> if the given credentials do not conform to the
     *         format defined by the Credentials API.</li>
     *         <li><em>409 Conflict</em> if credentials for the given type and auth-id have
     *         already been registered.</li>
     *         <li><em>412 Precondition Failed</em> if a device with the given identifier
     *         has not been registered. Note that implementors are not required to perform
     *         such a check.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/credentials-api/#add-credentials">
     *      Credentials API - Add Credentials</a>
     */
    void add(String tenantId, JsonObject credentialsObject, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

     /**
     * Gets all credentials registered for a device.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The device to get credentials for.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>200 OK</em> if credentials of the given type and authentication identifier have been found. The
     *         <em>payload</em> will contain the credentials.</li>
     *         <li><em>404 Not Found</em> if no credentials matching the criteria exist.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void getAll(String tenantId, String deviceId, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

    /**
     * Updates existing device credentials.
     *
     * @param tenantId The tenant the device belongs to.
     * @param credentialsObject A map containing keys and values that fulfill the credentials format of the credentials api.
     *                  See <a href="https://www.eclipse.org/hono/api/Credentials-API/#credentials-format">Credentials Format</a> for details.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>204 No Content</em> if the credentials have been updated successfully.</li>
     *         <li><em>400 Bad Request</em> if the given credentials do not conform to the
     *         format defined by the Credentials API.</li>
     *         <li><em>404 Not Found</em> if no credentials of the given type and auth-id exist.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/credentials-api/#update-credentials">
     *      Credentials API - Update Credentials</a>
     */
    void update(String tenantId, JsonObject credentialsObject, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

    /**
     * Removes credentials by authentication identifier and type.
     *
     * @param tenantId The tenant the device belongs to.
     * @param type The type of credentials to remove.
     * @param authId The authentication identifier to remove credentials for.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         <p>
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>204 No Content</em> if the credentials matching the criteria have been removed.</li>
     *         <li><em>404 Not Found</em> if no credentials match the given criteria.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/credentials-api/#remove-credentials">
     *      Credentials API - Remove Credentials</a>
     */
    void remove(String tenantId, String type, String authId, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

    /**
     * Removes all credentials for a device.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID under which the device is registered.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         <p>
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>204 No Content</em> if the credentials for the device have been removed.</li>
     *         <li><em>404 Not Found</em> if no credentials are registered for the device.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/credentials-api/#remove-credentials">
     *      Credentials API - Remove Credentials</a>
     */
    void removeAll(String tenantId, String deviceId, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);
}
