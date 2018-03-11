/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.registration;

import org.eclipse.hono.util.RegistrationResult;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;

/**
 * A service for keeping record of device identities.
 * 
 * @see <a href="https://www.eclipse.org/hono/api/device-registration-api/">Device Registration API</a>
 */
public interface RegistrationService extends Verticle {

    /**
     * Registers a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The ID the device should be registered under.
     * @param otherKeys Additional properties to be registered with the device (may be {@code null}).
     * @param resultHandler The handler to invoke with the result of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>201 Created</em> if the device has been registered successfully.</li>
     *             <li><em>409 Conflict</em> if a device with the given identifier already exists
     *             for the tenant.</li>
     *             </ul>
     * @throws NullPointerException if any of tenant, device ID or result handler is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/device-registration-api/#register-device">
     *      Device Registration API - Register Device</a>
     */
    void addDevice(String tenantId, String deviceId, JsonObject otherKeys, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Gets device registration data by device ID.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to get registration data for.
     * @param resultHandler The handler to invoke with the result of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>200 OK</em> if a device with the given ID is registered
     *             for the tenant. The <em>payload</em> will contain the properties
     *             registered for the device.</li>
     *             <li><em>404 Not Found</em> if no device with the given identifier
     *             is registered for the tenant.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/device-registration-api/#get-registration-information">
     *      Device Registration API - Get Registration Information</a>
     */
    void getDevice(String tenantId, String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Asserts that a device is registered with a given tenant and is enabled.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to get the assertion for.
     * @param resultHandler The handler to invoke with the result of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>200 OK</em> if a device with the given ID is registered for
     *             the tenant and its <em>enabled</em> property is {@code true}.
     *             The <em>payload</em> will contain a JWT token asserting the registration status.</li>
     *             <li><em>404 Not Found</em> if no device with the given identifier is
     *             registered for the tenant or its <em>enabled</em> property is {@code false}.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/device-registration-api/#assert-device-registration">
     *      Device Registration API - Assert Device Registration</a>
     */
    void assertRegistration(String tenantId, String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Asserts that a device is authorized to act as a <em>gateway</em> for another device.
     * <p>
     * In particular, this means that the gateway and the device are registered with the tenant, are enabled
     * and that the gateway device is allowed to <em>act on behalf of</em> the other device.
     * <p>
     * Implementing classes should verify, that the gateway is authorized to get an assertion for the device.
     * Such a check might be based on a specific role that the client needs to have or on an
     * explicitly defined relation between the gateway and the device(s).
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to get the assertion for.
     * @param gatewayId The gateway that wants to act on behalf of the device.
     * @param resultHandler The handler to invoke with the result of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>200 OK</em> if a device with the given ID is registered for
     *             the tenant and its <em>enabled</em> property is {@code true}.
     *             The <em>payload</em> will contain a JWT token asserting the registration status.</li>
     *             <li><em>403 Forbidden</em> if the gateway is not authorized to get an assertion
     *             for the device.</li>
     *             <li><em>404 Not Found</em> if no device with the given identifier is
     *             registered for the tenant or its <em>enabled</em> property is {@code false}.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/device-registration-api/#assert-device-registration">
     *      Device Registration API - Assert Device Registration</a>
     */
    void assertRegistration(String tenantId, String deviceId, String gatewayId, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Updates device registration data.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to update the registration for.
     * @param otherKeys A map containing additional properties to be registered with the device (may be {@code null}).
     *                  The properties provided in this map will completely replace any existing properties registered for the device.
     * @param resultHandler The handler to invoke with the result of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>204 No Content</em> if the registration information has been updated successfully.</li>
     *             <li><em>404 Not Found</em> if no device with the given identifier is
     *             registered for the tenant.</li>
     *             </ul>
     * @throws NullPointerException if any of tenant, device ID or result handler is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/device-registration-api/#update-device-registration">
     *      Device Registration API - Update Device Registration</a>
     */
    void updateDevice(String tenantId, String deviceId, JsonObject otherKeys, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Removes a device.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to remove.
     * @param resultHandler The handler to invoke with the result of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>204 No Content</em> if the device has been removed successfully.</li>
     *             <li><em>404 Not Found</em> if no device with the given identifier is
     *             registered for the tenant.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/device-registration-api/#deregister-device">
     *      Device Registration API - Deregister Device</a>
     */
    void removeDevice(String tenantId, String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler);
}
