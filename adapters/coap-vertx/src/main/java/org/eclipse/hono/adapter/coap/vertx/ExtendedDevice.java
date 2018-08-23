/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.coap.vertx;

import org.eclipse.hono.service.auth.device.Device;

/**
 * Extended hono device.
 * 
 * Tuple of hono devices, providing an authenticated device and optionally different device. For the gateway based
 * communication the authenticated device would be the gateway and the device will contain the hono device, which is the
 * origin of the associated message data. For direct communication both devices of the tuple are the same and used as
 * origin of the associated message data.
 */
public class ExtendedDevice {

    /**
     * Authenticated device.
     */
    public final Device authenticatedDevice;
    /**
     * Message data origin device.
     */
    public final Device originDevice;

    /**
     * Create extended device.
     * 
     * @param authenticatedDevice authenticated device. Maybe a gateway.
     * @param originDevice origin device. Maybe different or the same as the authenticatedDevice.
     */
    public ExtendedDevice(final Device authenticatedDevice, final Device originDevice) {
        this.authenticatedDevice = authenticatedDevice;
        this.originDevice = originDevice;
    }

    @Override
    public String toString() {
        if (authenticatedDevice == null || originDevice == null) {
            return "unknown";
        }
        if (authenticatedDevice == originDevice) {
            return authenticatedDevice.toString();
        }
        return originDevice.toString() + " via " + authenticatedDevice.toString();
    }
}
