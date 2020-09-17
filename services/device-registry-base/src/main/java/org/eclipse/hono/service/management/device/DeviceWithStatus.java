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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Device Information.
 */
@JsonIgnoreProperties()
@JsonInclude(value = Include.NON_NULL)
public class DeviceWithStatus extends Device {

    @JsonProperty(RegistryManagementConstants.FIELD_STATUS)
    private Status status = new Status();

    /**
     * Creates a new Device instance.
     */
    public DeviceWithStatus() {
    }

    /**
     * Creates a new instance cloned from an existing instance.
     *
     * @param other The device to copy from.
     *
     * @throws NullPointerException if other device is {@code null}.
     */
    public DeviceWithStatus(final DeviceWithStatus other) {
        super(other);

        setStatus(other.getStatus());
    }

    /**
     * Creates a new instance cloned from an existing instance.
     *
     * @param other The device to copy from.
     *
     * @throws NullPointerException if other device is {@code null}.
     */
    public DeviceWithStatus(final Device other) {
        super(other);
    }

    /**
     * Gets the status of this device.
     *
     * @return The status.
     */
    public Status getStatus() {
        return this.status;
    }

    /**
     * Sets the status of this device which contains system-internal information.
     *
     * @param status The status to be set.
     */
    public void setStatus(final Status status) {
        this.status = status;
    }

}
