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

import io.vertx.core.Future;

/**
 * A component that maps a given device to the gateway through which data was last published for the given device.
 *
 */
public interface GatewayMapper extends ConnectionLifecycle {

    /**
     * Determines the gateway device id for the given device id (if applicable).
     * <p>
     * The value of the returned Future can be either
     * <ul>
     * <li>the gateway device id</li>
     * <li>{@code null} if the device is configured to be accessed via a gateway (i.e. one or more 'via' devices are
     * set), but no 'last-via' device is set, meaning that no message has been sent yet for this device.</li>
     * <li>the given device id if the device is not configured to be accessed via a gateway</li>
     * </ul>
     *
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @return A succeeded Future containing the mapped gateway device id or {@code null};
     *         or a failed Future if there was an error determining the mapped gateway device.
     */
    Future<String> getMappedGatewayDevice(String tenantId, String deviceId);

}
