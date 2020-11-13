/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.service.commandrouter;

import java.time.Duration;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * A service for accepting information from protocol adapters which are used to route command &amp; control messages
 * to the protocol adapters that the target devices are connected to.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/command-router/">Command Router API</a>
 */
public interface CommandRouterService {

    /**
     * Sets the given gateway as the last gateway that acted on behalf of the given device.
     * <p>
     * If a device connects directly instead of through a gateway, the device identifier itself is to be used as value
     * for the <em>gatewayId</em> parameter.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param gatewayId The gateway id (or the device id if the last message came from the device directly).
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *             The <em>status</em> will be <em>204 No Content</em> if the operation completed successfully.
     *             <br>
     *             An implementation may return a <em>404 Not Found</em> status in order to indicate that 
     *             no device and/or gateway with the given identifier exists for the given tenant.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<CommandRouterResult> setLastKnownGatewayForDevice(String tenantId, String deviceId, String gatewayId,
            Span span);

    /**
     * Registers a protocol adapter instance as the consumer of command &amp; control messages
     * for a device.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param adapterInstanceId The protocol adapter instance id.
     * @param lifespan The lifespan of the mapping entry. Using a negative duration or {@code null} here is
     *                 interpreted as an unlimited lifespan. The guaranteed granularity taken into account
     *                 here is seconds.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         The <em>status</em> will be <em>204 No Content</em> if the operation completed successfully.
     * @throws NullPointerException if any of the parameters except lifespan is {@code null}.
     */
    Future<CommandRouterResult> registerCommandConsumer(String tenantId, String deviceId,
            String adapterInstanceId, Duration lifespan, Span span);

    /**
     * Unregisters a command consumer for a device.
     * <p>
     * The registration entry is only deleted if the device is currently mapped to the given adapter instance.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param adapterInstanceId The protocol adapter instance id that the entry to be removed has to contain.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         The <em>status</em> will be <em>204 No Content</em> if the entry was successfully removed.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<CommandRouterResult> unregisterCommandConsumer(String tenantId, String deviceId, String adapterInstanceId, Span span);

}
