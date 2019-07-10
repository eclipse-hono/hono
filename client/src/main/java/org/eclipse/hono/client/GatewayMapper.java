/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import org.eclipse.hono.client.impl.GatewayMapperImpl;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;

/**
 * A component that maps a given device to the gateway through which data was last published for the given device.
 *
 */
public interface GatewayMapper extends ConnectionLifecycle<HonoConnection> {

    /**
     * Creates a new {@link GatewayMapper} using the default implementation.
     *
     * @param registrationClientFactory The factory to create a registration client instance.
     * @param deviceConnectionClientFactory The factory to create a device connection client instance.
     * @param tracer The tracer instance.
     * @return The GatewayMapper instance.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    static GatewayMapper create(final RegistrationClientFactory registrationClientFactory,
            final DeviceConnectionClientFactory deviceConnectionClientFactory, final Tracer tracer) {
        return new GatewayMapperImpl(registrationClientFactory, deviceConnectionClientFactory, tracer);
    }

    /**
     * Determines the gateway device id for the given device id (if applicable).
     * <p>
     * The value of the returned Future can be either
     * <ul>
     * <li>the id of the gateway that last acted on behalf of the given device</li>
     * <li>the 'via' gateway id of the device registration information if no last known gateway is set for the device
     * and the 'via' entry only contains a single entry</li>
     * <li>the given device id if the device is not configured to be accessed via a gateway</li>
     * </ul>
     *
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @param context The currently active OpenTracing span context or {@code null}.
     * @return A succeeded Future containing the mapped gateway device id or the device id itself;
     *         or a failed Future with:
     *         <ul>
     *         <li>a {@link ClientErrorException} with status <em>Not Found</em> if no last known gateway was set
     *         for the device and the 'via' entry of the device registration contains more than one entry</li>
     *         <li>a {@link ClientErrorException} with status <em>Not Found</em> if the last known gateway for the
     *         device was not found in the 'via' entry of the device registration</li>
     *         <li>a {@link ServiceInvocationException} with an error code indicating the cause of the failure
     *         determining the device registration information or the mapped gateway device</li>
     *         </ul>
     */
    Future<String> getMappedGatewayDevice(String tenantId, String deviceId, SpanContext context);

}
