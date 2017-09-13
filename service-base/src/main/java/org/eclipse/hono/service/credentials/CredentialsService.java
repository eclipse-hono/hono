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
     * Gets credentials data by authId of a device ID with a specific type.
     *
     * @param tenantId The tenant the device belongs to.
     * @param type The type of credentials to get.
     * @param authId The authID of the device to get credentials data for.
     * @param resultHandler The handler to invoke with the result of the operation. If credentials with the
     *         given authID and type are registered for the tenant, the <em>status</em> will be {@link HttpURLConnection#HTTP_OK}
     *         and the <em>payload</em> will contain the details of the credentials.
     *         Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void getCredentials(String tenantId, String type, String authId, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

    /**
     * Add credentials to credentials registry.
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
    void addCredentials(String tenantId, JsonObject credentialsObject, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

    /**
     * Updates device credentials data.
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
    void updateCredentials(String tenantId, JsonObject credentialsObject, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

    /**
     * Removes device credentials data.
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID under which the device is registered.
     * @param type The type of credentials to remove. If set to '*' then all credentials of the device are removed and
     *             authId is ignored, otherwise only the credentials matching the type and auth-id are removed..
     * @param authId The authID of the device to remove credentials data for. Maybe null - in that case all credentials
     *               of the specified type are removed..
     * @param resultHandler The handler to invoke with the result of the operation. If a device with the given ID does not
     *         exist for the tenant, or there are no credentials currently registered for this device that match the type and authId,
     *                     the <em>status</em> will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     *         Otherwise the operation is successful and the status will be {@link HttpURLConnection#HTTP_NO_CONTENT}.
     * @throws NullPointerException if any of the parameters - except authId - is {@code null}.
     */
    void removeCredentials(String tenantId, String deviceId, String type, String authId, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);
}
