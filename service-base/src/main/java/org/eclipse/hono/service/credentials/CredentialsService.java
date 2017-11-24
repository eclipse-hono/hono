/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.credentials;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.CredentialsResult;

import java.net.HttpURLConnection;

/**
 * A service for keeping record of credentials for devices.
 *
 */
public interface CredentialsService extends Verticle {

    /**
     * Adds credentials for a device.
     *
     * @param tenantId The tenant the device belongs to.
     * @param credentialsObject A map containing keys and values that fulfill the credentials format of the credentials api.
     *                  See <a href="https://www.eclipse.org/hono/api/Credentials-API/#credentials-format">Credentials Format</a> for details.
     * @param resultHandler The handler to invoke with the result of the operation. If a device with the given ID does not
     *         exist for the tenant, the <em>status</em> will be {@link HttpURLConnection#HTTP_PRECON_FAILED}.
     *         If credentials for the type and authId of the device are already existing, the <em>status</em> will be {@link HttpURLConnection#HTTP_CONFLICT}.
     *         If the credentialsObject does not conform to the credentials format, the <em>status</em> will be  {@link HttpURLConnection#HTTP_BAD_REQUEST}.
     *         Otherwise the operation is successful and the <em>status</em> will be {@link HttpURLConnection#HTTP_CREATED}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void add(String tenantId, JsonObject credentialsObject, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

    /**
     * Gets credentials for a device.
     *
     * @param tenantId The tenant the device belongs to.
     * @param type The type of credentials to get.
     * @param authId The authentication identifier of the device to get credentials for (may be {@code null}.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         <p>
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>200 OK</em> if credentials of the given type and authentication identifier have been found. The
     *         <em>payload</em> will contain the credentials.</li>
     *         <li><em>404 Not Found</em> if no credentials matching the criteria exist.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void get(String tenantId, String type, String authId, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

    /**
     * Gets all credentials registered for a device.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The device to get credentials for.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         <p>
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
     * @param resultHandler The handler to invoke with the result of the operation. If a device with the given ID does not
     *         exist for the tenant, or there are no credentials currently registered for this device that match the type and authId,
     *         he <em>status</em> will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     *         If the credentialsObject does not conform to the credentials format, the <em>status</em> will be  {@link HttpURLConnection#HTTP_BAD_REQUEST}.
     *         Otherwise the operation is successful and the status will be {@link HttpURLConnection#HTTP_NO_CONTENT}.
     * @throws NullPointerException if any of the parameters is {@code null}.
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
     *         <li>204 (No Content) if the credentials matching the criteria have been removed</li> 
     *         <li>404 (Not Found) if no credentials match the given criteria</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
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
     *         <li>204 (No Content) if the credentials for the device</li> 
     *         <li>404 (Not Found) if no credentials are registered for the device</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void removeAll(String tenantId, String deviceId, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

}
