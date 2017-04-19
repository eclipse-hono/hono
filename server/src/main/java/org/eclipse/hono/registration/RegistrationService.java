/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.registration;

import java.net.HttpURLConnection;

import org.eclipse.hono.util.RegistrationResult;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;

/**
 * A service for keeping record of device identities.
 *
 */
public interface RegistrationService extends Verticle {

    /**
     * Gets device registration data by device ID.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to get registration data for.
     * @param resultHandler The handler to invoke with the result of the operation. If a device with the
     *         given ID is registered for the tenant, the <em>status</em> will be {@link HttpURLConnection#HTTP_OK}
     *         and the <em>payload</em> will contain the keys registered for the device.
     *         Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    void getDevice(String tenantId, String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Checks whether a device is registered and enabled.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to check.
     * @param resultHandler The handler to invoke with the result of the operation. If a device with the
     *         given ID is registered for the tenant, then the <em>status</em> will be {@link HttpURLConnection#HTTP_OK}
     *         and the <em>payload</em> will contain the <em>enabled</em> property of the device.
     *         Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    void isEnabled(String tenantId, String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Finds device registration data by a key registered for the device.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param key The name of the key to look up the device registration by.
     * @param value The value for the key to match on.
     * @param resultHandler The handler to invoke with the result of the operation. If a device with the
     *         given key/value is registered for the tenant, the <em>status</em> will be {@link HttpURLConnection#HTTP_OK}
     *         and the <em>payload</em> will contain the keys registered for the device.
     *         Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    void findDevice(String tenantId, String key, String value, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Registers a device.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID the device should be registered under.
     * @param otherKeys A map containing additional keys and values that the device can be identified by (within the tenant).
     * @param resultHandler The handler to invoke with the result of the operation. If a device with the given ID does not
     *         yet exist for the tenant, the <em>status</em> will be {@link HttpURLConnection#HTTP_CREATED}.
     *         Otherwise the status will be {@link HttpURLConnection#HTTP_CONFLICT}.
     */
    void addDevice(String tenantId, String deviceId, JsonObject otherKeys, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Updates device registration data.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to update the registration for.
     * @param otherKeys A map containing additional keys and values that the device can be identified by (within the tenant).
     *                  The keys provided in this parameter will completely replace the former keys registered for the device.
     * @param resultHandler The handler to invoke with the result of the operation. If a device with the given ID exists for
     *         the tenant, the <em>status</em> will be {@link HttpURLConnection#HTTP_OK} and the <em>payload</em> will contain
     *         the keys that had originally been registered for the device.
     *         Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    void updateDevice(String tenantId, String deviceId, JsonObject otherKeys, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Removes a device.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to remove.
     * @param resultHandler The handler to invoke with the result of the operation. If the device has been removed,
     *         the <em>status</em> will be {@link HttpURLConnection#HTTP_OK} and the <em>payload</em> will contain the keys
     *         that had been registered for the removed device.
     *         Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    void removeDevice(String tenantId, String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler);

}