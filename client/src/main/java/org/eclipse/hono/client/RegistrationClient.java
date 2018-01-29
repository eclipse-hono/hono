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
 *    Bosch Software Innovations GmbH - remove handler based methods
 */

package org.eclipse.hono.client;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A client for accessing Hono's Registration API.
 * <p>
 * An instance of this interface is always scoped to a specific tenant.
 * <p>
 * See Hono's <a href="https://www.eclipse.org/hono/api/device-registration-api/">
 * Registration API specification</a> for a description of the result codes returned.
 */
public interface RegistrationClient extends RequestResponseClient {

    /**
     * Asserts that a device is registered and <em>enabled</em>.
     * 
     * @param deviceId The ID of the device to get the assertion for.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 200 has been received from the
     *         registration service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/device-registration-api/#assert-device-registration">
     *         Assert Device Registration</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<JsonObject> assertRegistration(String deviceId);

    /**
     * Asserts that a device is registered and <em>enabled</em>.
     * 
     * @param deviceId The ID of the device to get the assertion for.
     * @param gatewayId The gateway that wants to act on behalf of the device.
     *                  <p>
     *                  If not {@code null}, the service will verify that the gateway
     *                  is enabled and authorized to <em>act on behalf of</em> the
     *                  given device before asserting the device's registration status.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 200 has been received from the
     *         registration service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/device-registration-api/#assert-device-registration">
     *         Assert Device Registration</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<JsonObject> assertRegistration(String deviceId, String gatewayId);

    /**
     * Gets registration information for a device.
     *
     * @param deviceId The id of the device to check.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 200 has been received from the
     *         registration service. The JSON object will then contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/device-registration-api/#get-registration-information">
     *         Get Registration Information</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<JsonObject> get(String deviceId);

    /**
     * Registers a device with Hono.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it.
     *
     * @param deviceId The id of the device to register.
     * @param data The data to register with the device.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 201 has been received from the
     *         registration service.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<Void> register(String deviceId, JsonObject data);

    /**
     * Updates the data a device has been registered with.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it.
     *
     * @param deviceId The id of the device to register.
     * @param data The data to update the registration with (may be {@code null}).
     *             The original data will be <em>replaced</em> with this data, i.e.
     *             the data will not be merged with the existing data.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 204 has been received from the
     *         registration service.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<Void> update(String deviceId, JsonObject data);

    /**
     * Deregisters a device from Hono.
     * <p>
     * Once a device has been (successfully) deregistered, no more telemtry data can be uploaded
     * for it nor can commands be sent to it anymore.
     *
     * @param deviceId The id of the device to deregister.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 204 has been received from the
     *         registration service.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<Void> deregister(String deviceId);
}
