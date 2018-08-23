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

package org.eclipse.hono.service.registration;

import org.eclipse.hono.util.RegistrationResult;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;

/**
 * A minimal service for keeping record of device identities.
 * This interface covers only the mandatory operations.
 *
 * @see <a href="https://www.eclipse.org/hono/api/device-registration-api/">Device Registration API</a>
 */
public interface RegistrationService extends Verticle {

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
}
