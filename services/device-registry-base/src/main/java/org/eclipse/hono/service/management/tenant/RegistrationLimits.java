/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.management.tenant;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A definition of limits for registry entries.
 */
@RegisterForReflection
public class RegistrationLimits {

    /**
     * The value indicating an unlimited resource.
     */
    public static final int UNLIMITED = -1;

    @JsonProperty(RegistryManagementConstants.FIELD_MAX_DEVICES)
    private int maxNumberOfDevices = UNLIMITED;
    @JsonProperty(RegistryManagementConstants.FIELD_MAX_CREDENTIALS_PER_DEVICE)
    private int maxCredentialsPerDevice = UNLIMITED;

    /**
     * Checks if the number of devices per tenant is limited.
     *
     * @return {@code true} if maxNumberOfDevices is &gt; {@value #UNLIMITED}.
     */
    @JsonIgnore
    public final boolean isNumberOfDevicesLimited() {
        return maxNumberOfDevices > UNLIMITED;
    }

    /**
     * Gets the maximum number of devices that can be registered for a tenant.
     *
     * @return The maximum number of devices or {@value #UNLIMITED} if no limit has been set.
     */
    public final int getMaxNumberOfDevices() {
        return maxNumberOfDevices;
    }

    /**
     * Sets the maximum number of devices that can be registered for a tenant.
     *
     * @param maxNumberOfDevices The maximum number of devices or {@value #UNLIMITED} if an unlimited number of devices can
     *                           be registered per tenant.
     * @throws IllegalArgumentException if the value is &lt; {@value #UNLIMITED}.
     * @return A reference to this object for command chaining.
     */
    public final RegistrationLimits setMaxNumberOfDevices(final int maxNumberOfDevices) {
        if (maxNumberOfDevices < UNLIMITED) {
            throw new IllegalArgumentException("max number of devices must be >= " + UNLIMITED);
        }
        this.maxNumberOfDevices = maxNumberOfDevices;
        return this;
    }

    /**
     * Checks if the number of credentials per device is limited.
     *
     * @return {@code true} if maxCredentialsPerDevice is &gt; {@value #UNLIMITED}.
     */
    @JsonIgnore
    public final boolean isNumberOfCredentialsPerDeviceLimited() {
        return maxCredentialsPerDevice > UNLIMITED;
    }

    /**
     * Gets the maximum number of credentials that can be registered per device.
     *
     * @return The maximum number of credentials or {@value #UNLIMITED} if no limit has been set.
     */
    public final int getMaxCredentialsPerDevice() {
        return maxCredentialsPerDevice;
    }

    /**
     * Sets the maximum number of credentials that can be registered per device.
     *
     * @param maxCredentialsPerDevice The maximum number of credentials or {@value #UNLIMITED} if an unlimited number of
     *                                credentials can be registered per device.
     * @throws IllegalArgumentException if the value is &lt; {@value #UNLIMITED}.
     * @return A reference to this object for command chaining.
     */
    public final RegistrationLimits setMaxCredentialsPerDevice(final int maxCredentialsPerDevice) {
        if (maxCredentialsPerDevice < UNLIMITED) {
            throw new IllegalArgumentException("max credentials per device must be >= " + UNLIMITED);
        }
        this.maxCredentialsPerDevice = maxCredentialsPerDevice;
        return this;
    }
}
