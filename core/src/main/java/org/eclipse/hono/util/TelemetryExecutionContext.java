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

package org.eclipse.hono.util;

import org.eclipse.hono.auth.Device;

/**
 * A container for information relevant for processing a message sent by a device
 * which contains telemetry data or an event.
 */
public interface TelemetryExecutionContext extends ExecutionContext {

    /**
     * Gets the verified identity of the device that the message has been
     * received from which is processed in this context.
     *
     * @return The device or {@code null} if the device has not been authenticated.
     */
    Device getAuthenticatedDevice();

    /**
     * Determines if the message that is processed in this context has been received from
     * a device whose identity has been verified.
     *
     * @return {@code true} if the device has been authenticated or {@code false} otherwise.
     */
    default boolean isDeviceAuthenticated() {
        return getAuthenticatedDevice() != null;
    }

    /**
     * Gets the QoS level as set in the request by the device.
     *
     * @return The QoS level requested by the device or {@code null} if the level could not be determined.
     */
    QoS getRequestedQos();
}
