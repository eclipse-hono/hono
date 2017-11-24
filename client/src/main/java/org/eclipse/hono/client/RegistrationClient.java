/**
 * Copyright (c) 2016,2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.client;

import org.eclipse.hono.util.RegistrationResult;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
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
     * Asserts that a device is registered with a given tenant and is <em>enabled</em>.

     * @param deviceId The ID of the device to get the assertion for.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         <p>
     *         The handler will be invoked with a succeeded future if a response has
     *         been received from the registration service. The <em>status</em> and
     *         <em>payload</em> properties will contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/device-registration-api/#assert-device-registration">
     *         Assert Device Registration</a>.
     *         <p>
     *         If the request could not be sent to the service or the service did not reply
     *         within the timeout period, the handler will be invoked with
     *         a failed future containing a {@link ServerErrorException}.
     * @throws NullPointerException if device ID or result handler are {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    void assertRegistration(String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Gets registration information for a device.
     *
     * @param deviceId The id of the device to check.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         <p>
     *         The handler will be invoked with a succeeded future if a response has
     *         been received from the registration service. The <em>status</em> and
     *         <em>payload</em> properties will contain values as defined in
     *         <a href="https://www.eclipse.org/hono/api/device-registration-api/#get-registration-information">
     *         Get Registration Information</a>.
     *         <p>
     *         If the request could not be sent to the service or the service did not reply
     *         within the timeout period, the handler will be invoked with
     *         a failed future containing a {@link ServerErrorException}.
     * @throws NullPointerException if device ID or result handler are {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    void get(String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Registers a device with Hono.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * telemetry data for it.
     *
     * @param deviceId The id of the device to register.
     * @param data The data to register with the device.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         <p>
     *         The handler will be invoked with a succeeded future if a response has
     *         been received from the registration service. The <em>status</em> property
     *         will contain a value as defined in
     *         <a href="https://www.eclipse.org/hono/api/device-registration-api/#register-device">
     *         Register Device</a>.
     *         <p>
     *         If the request could not be sent to the service or the service did not reply
     *         within the timeout period, the handler will be invoked with
     *         a failed future containing a {@link ServerErrorException}.
     * @throws NullPointerException if device ID or result handler are {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    void register(String deviceId, JsonObject data, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Updates the data a device has been registered with.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * telemetry data for it.
     *
     * @param deviceId The id of the device to register.
     * @param data The data to update the registration with (may be {@code null}).
     *             The original data will be <em>replaced</em> with this data, i.e.
     *             the data will not be merged with the existing data.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         <p>
     *         The handler will be invoked with a succeeded future if a response has
     *         been received from the registration service. The <em>status</em> property
     *         will contain a value as defined in
     *         <a href="https://www.eclipse.org/hono/api/device-registration-api/#update-device-registration">
     *         Update Device Registration</a>.
     *         <p>
     *         If the request could not be sent to the service or the service did not reply
     *         within the timeout period, the handler will be invoked with
     *         a failed future containing a {@link ServerErrorException}.
     * @throws NullPointerException if device ID or result handler are {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    void update(String deviceId, JsonObject data, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Deregisters a device from Hono.
     * <p>
     * Once a device has been (successfully) deregistered, no more telemtry data can be uploaded
     * for it nor can commands be sent to it anymore.
     *
     * @param deviceId The id of the device to deregister.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         <p>
     *         The handler will be invoked with a succeeded future if a response has
     *         been received from the registration service. The <em>status</em> property
     *         will contain a value as defined in
     *         <a href="https://www.eclipse.org/hono/api/device-registration-api/#deregister-device">
     *         Deregister Device</a>.
     *         <p>
     *         If the request could not be sent to the service or the service did not reply
     *         within the timeout period, the handler will be invoked with
     *         a failed future containing a {@link ServerErrorException}.
     * @throws NullPointerException if device ID or result handler are {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    void deregister(String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler);
}
