/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import io.opentracing.Span;
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
     * Asserts that a device is registered with a given tenant and is enabled.
     * <p>
     * This default implementation simply returns the result of {@link #assertRegistration(String, String, Handler)}.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to get the assertion for.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
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
    default void assertRegistration(final String tenantId, final String deviceId, final Span span,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        assertRegistration(tenantId, deviceId, resultHandler);
    }

    /**
     * Asserts that a device is authorized to act as a <em>gateway</em> for another device.
     * <p>
     * In particular, this means that the gateway and the device are registered with the tenant, are enabled
     * and that the gateway device is allowed to <em>act on behalf of</em> the other device.
     * <p>
     * Implementing classes should verify, that the gateway is authorized to get an assertion for the device.
     * Such a check might be based on a specific role that the client needs to have or on an
     * explicitly defined relation between the gateway and the device(s).
     * <br>
     * In the case of a device configured with one or more <em>via</em> gateways, implementing classes should
     * update the device's registration information with the given gateway in the form of a <em>last-via</em>
     * property.
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
     * Asserts that a device is authorized to act as a <em>gateway</em> for another device.
     * <p>
     * In particular, this means that the gateway and the device are registered with the tenant, are enabled
     * and that the gateway device is allowed to <em>act on behalf of</em> the other device.
     * <p>
     * Implementing classes should verify, that the gateway is authorized to get an assertion for the device.
     * Such a check might be based on a specific role that the client needs to have or on an
     * explicitly defined relation between the gateway and the device(s).
     * <br>
     * In the case of a device configured with one or more <em>via</em> gateways, implementing classes should
     * update the device's registration information with the given gateway in the form of a <em>last-via</em>
     * property.
     * <p>
     * This default implementation simply returns the result of {@link #assertRegistration(String, String, String, Handler)}.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to get the assertion for.
     * @param gatewayId The gateway that wants to act on behalf of the device.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
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
    default void assertRegistration(final String tenantId, final String deviceId, final String gatewayId,
            final Span span,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        assertRegistration(tenantId, deviceId, gatewayId, resultHandler);
    }

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
     * Sets the given gateway as the last gateway that acted on behalf of the given device.
     * <p>
     * If a device connects directly instead of through a gateway, the device identifier is to be used as value for
     * the <em>gatewayId</em> parameter.
     * <p>
     * This association between device and its last-used gateway is used for scenarios where devices with multiple
     * potential gateways are used, along with gateways subscribing to command messages only using their gateway id.
     * In such scenarios, the value set here is needed to route command messages to the right gateway.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param gatewayId The gateway id (or the device id if the last message came from the device directly).
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @param resultHandler The handler to invoke with the result of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>204 No Content</em> if the operation completed successfully.</li>
     *             <li><em>404 Not Found</em> if no device with the given identifier
     *             is registered for the tenant.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void setLastUsedGateway(String tenantId, String deviceId, String gatewayId, Span span,
            Handler<AsyncResult<RegistrationResult>> resultHandler);

    /**
     * Gets the gateway that last acted on behalf of the given device.
     * <p>
     * If the given device doesn't support usage via a gateway, the result handler is invoked with a JSON containing the
     * given device id itself.
     * <p>
     * If no last-used gateway has been set for the given device yet, the result handler is invoked with a <em>404 Not
     * Found</em> status result.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @param resultHandler The handler to invoke with the result of the operation.
     *            The <em>status</em> will be
     *            <ul>
     *            <li><em>200 OK</em> if a result could be determined. The <em>payload</em>
     *            will contain a <em>device-id</em> property with either the gateway id or with the device id if the
     *            device doesn't support usage via a gateway.</li>
     *            <li><em>404 Not Found</em> if the device supports usage via a gateway but there was no last-used
     *            gateway assigned to the device, or if no device with the given identifier is registered for the
     *            tenant.</li>
     *            </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void getLastUsedGateway(String tenantId, String deviceId, Span span,
            Handler<AsyncResult<RegistrationResult>> resultHandler);
}
