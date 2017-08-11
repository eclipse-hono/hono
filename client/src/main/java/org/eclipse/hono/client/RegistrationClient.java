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

import java.net.HttpURLConnection;

import org.eclipse.hono.util.RegistrationResult;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * A client for accessing Hono's Registration API.
 * <p>
 * An instance of this interface is always scoped to a specific tenant.
 * </p>
 * <p>
 * See Hono's <a href="https://www.eclipse.org/hono/api/Device-Registration-API">
 * Registration API specification</a> for a description of the result codes returned.
 * </p>
 */
public interface RegistrationClient extends RequestResponseClient {

    /**
     * Asserts that a device is registered with a given tenant and is enabled.
     *
     * @param deviceId The ID of the device to get the assertion for.
     * @param resultHandler The handler to invoke with the result of the operation. If a device with the
     *         given ID is registered for the tenant and its <em>enabled</em> property is {@code true},
     *         the <em>status</em> will be {@link HttpURLConnection#HTTP_OK}
     *         and the <em>payload</em> will contain a JWT token asserting the registration status.
     *         Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    void assertRegistration(String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Checks whether a given device is registered.
     *
     * @param deviceId The id of the device to check.
     * @param resultHandler The handler to invoke with the result of the operation.
     */
    void get(String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Registers a device with Hono.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * telemetry data for it.
     * </p>
     *
     * @param deviceId The id of the device to register.
     * @param data The data to register with the device.
     * @param resultHandler The handler to invoke with the result of the operation.
     */
    void register(String deviceId, JsonObject data, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Updates the data a device has been registered with.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * telemetry data for it.
     * </p>
     *
     * @param deviceId The id of the device to register.
     * @param data The data to update the registration with (may be {@code null}).
     *             The original data will be <em>replaced</em> with this data, i.e.
     *             the data will not be merged with the existing data.
     * @param resultHandler The handler to invoke with the result of the operation.
     */
    void update(String deviceId, JsonObject data, Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Deregisters a device from Hono.
     * <p>
     * Once a device has been (successfully) deregistered, no more telemtry data can be uploaded
     * for it nor can commands be sent to it anymore.
     * </p>
     *
     * @param deviceId The id of the device to deregister.
     * @param resultHandler The handler to invoke with the result of the operation.
     */
    void deregister(String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler);
}
