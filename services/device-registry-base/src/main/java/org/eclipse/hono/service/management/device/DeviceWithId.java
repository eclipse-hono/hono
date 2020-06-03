/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management.device;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Device Information.
 */
@JsonInclude(value = Include.NON_NULL)
public class DeviceWithId extends Device {

    @JsonProperty(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID)
    private String deviceId;

    /**
     * Creates a new DeviceWithId instance.
     */
    public DeviceWithId() {
    }

    /**
     * Creates a new DeviceWithId instance from a Device object, adding the Id.
     *
     * @param device The Device object.
     * @param id the device Id as a string.
     * @return a deviceWithId object.
     */
    public static DeviceWithId fromDevice(final Device device, final String id) {

        final DeviceWithId deviceWithId = super.Device(device);
        deviceWithId.setDeviceId(id);

        return deviceWithId;
    }

    /**
     * Set the device Id for this device.
     *
     * @param deviceId The resource ID for this device.
     * @return A reference to this for fluent use.
     */
    public DeviceWithId setDeviceId(final String deviceId) {
        this.deviceId = deviceId;
        return this;
    }

    /**
     * Get the device Id for this device.
     *
     * @return A string containing the deviceID.
     */
    @JsonIgnore
    public String getDeviceId() {
        return this.deviceId;
    }

}
