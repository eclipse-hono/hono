/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.client.registry;

import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.RegistrationAssertion;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A client for accessing Hono's Device Registration API.
 * <p>
 * See Hono's <a href="https://www.eclipse.org/hono/docs/api/device-registration/">
 * Device Registration API</a> for a description of the status codes returned.
 */
public interface DeviceRegistrationClient extends Lifecycle {

    /**
     * Asserts that a device is registered and <em>enabled</em>.
     *
     * @param tenantId The ID of the tenant that the device belongs to.
     * @param deviceId The ID of the device to get the assertion for.
     * @param gatewayId The gateway that tries to act on behalf of the device.
     *                  <p>
     *                  If not {@code null}, the service will verify that the gateway
     *                  is enabled and authorized to <em>act on behalf of</em> the
     *                  given device before asserting the device's registration status.
     * @param context The currently active OpenTracing span. An implementation
     *         should use this as the parent for any span it creates for tracing
     *         the execution of this operation.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with a status code in the [200, 300) range
     *         has been received from the Device Registration service. The contained object will
     *         then have properties according to the response message defined by
     *         <a href="https://www.eclipse.org/hono/docs/api/device-registration/#assert-device-registration">
     *         Assert Device Registration</a>.
     *         <p>
     *         Otherwise, the future will fail with a {@code org.eclipse.hono.client.ServiceInvocationException}
     *         containing the (error) status code returned by the service.
     * @throws NullPointerException if tenant or device ID are {@code null}.
     */
    Future<RegistrationAssertion> assertRegistration(
            String tenantId,
            String deviceId,
            String gatewayId,
            SpanContext context);
}
