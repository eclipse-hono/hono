/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.deviceregistry;

import org.eclipse.hono.config.SignatureSupportingConfigProperties;


/**
 * Configuration properties for Hono's registration API as own server.
 *
 */
public final class FileBasedRegistrationConfigProperties extends AbstractFileBasedRegistryConfigProperties {

    /**
     * The default number of devices that can be registered for each tenant.
     */
    public static final int DEFAULT_MAX_DEVICES_PER_TENANT = 100;
    private static final String DEFAULT_DEVICES_FILENAME = "/var/lib/hono/device-registry/device-identities.json";
    private final SignatureSupportingConfigProperties registrationAssertionProperties = new SignatureSupportingConfigProperties();

    private int maxDevicesPerTenant = DEFAULT_MAX_DEVICES_PER_TENANT;

    /**
     * Gets the maximum number of devices that can be registered for each tenant.
     * <p>
     * The default value of this property is {@link #DEFAULT_MAX_DEVICES_PER_TENANT}.
     * 
     * @return The maximum number of devices.
     */
    public int getMaxDevicesPerTenant() {
        return maxDevicesPerTenant;
    }

    /**
     * Sets the maximum number of devices that can be registered for each tenant.
     * <p>
     * The default value of this property is {@link #DEFAULT_MAX_DEVICES_PER_TENANT}.
     * 
     * @param maxDevices The maximum number of devices.
     * @throws IllegalArgumentException if the number of devices is &lt;= 0.
     */
    public void setMaxDevicesPerTenant(final int maxDevices) {
        if (maxDevices <= 0) {
            throw new IllegalArgumentException("max devices must be > 0");
        }
        this.maxDevicesPerTenant = maxDevices;
    }

    /**
     * Gets the properties for determining key material for creating registration assertion tokens.
     *
     * @return The properties.
     */
    public SignatureSupportingConfigProperties getSigning() {
        return registrationAssertionProperties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getDefaultFileName() {
        return DEFAULT_DEVICES_FILENAME;
    }
}
