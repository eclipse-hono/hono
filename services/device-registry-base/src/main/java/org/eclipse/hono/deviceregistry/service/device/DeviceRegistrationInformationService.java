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
package org.eclipse.hono.deviceregistry.service.device;

import org.eclipse.hono.util.RegistrationResult;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * A service which provides device registration information to internal service implementations.
 */
public interface DeviceRegistrationInformationService {

    /**
     * Gets device registration data by device ID.
     *
     * @param deviceKey The ID of the device to get registration data for.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation. The <em>status</em> will be
     *            <ul>
     *            <li><em>200 OK</em>, if a device with the given ID is registered for the tenant.<br>
     *            The <em>payload</em> will contain a JSON object with the following properties:
     *              <ul>
     *              <li><em>device-id</em> - the device identifier</li>
     *              <li><em>data</em> - the information registered for the device, i.e. the registered
     *                  {@link org.eclipse.hono.service.management.device.Device}</li>
     *              </ul>
     *            </li>
     *            <li><em>404 Not Found</em>, if no device with the given identifier is registered for the tenant.</li>
     *            </ul>
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    Future<RegistrationResult> getRegistrationInformation(DeviceKey deviceKey, Span span);
}
