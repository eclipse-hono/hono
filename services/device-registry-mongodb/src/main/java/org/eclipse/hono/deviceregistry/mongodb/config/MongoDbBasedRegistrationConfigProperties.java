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
package org.eclipse.hono.deviceregistry.mongodb.config;

/**
 * Configuration properties for Hono's device registry tenant API.
 */
public final class MongoDbBasedRegistrationConfigProperties extends AbstractMongoDbBasedRegistryConfigProperties {

    /**
     * The value indicating an <em>unlimited</em> number of devices to be allowed for a tenant.
     */
    public static final int UNLIMITED_DEVICES_PER_TENANT = -1;

    /**
     * The name of the mongodb collection where devices information are stored.
     */
    private static final String DEFAULT_DEVICE_COLLECTION_NAME = "devices";

    private int maxDevicesPerTenant = UNLIMITED_DEVICES_PER_TENANT;

    /**
     * Gets the maximum number of devices that can be registered for each tenant.
     * <p>
     * The default value of this property is {@link #UNLIMITED_DEVICES_PER_TENANT}.
     *
     * @return The maximum number of devices.
     */
    public int getMaxDevicesPerTenant() {
        return maxDevicesPerTenant;
    }

    /**
     * Sets the maximum number of devices that can be registered for each tenant.
     * <p>
     * The default value of this property is {@link #UNLIMITED_DEVICES_PER_TENANT}.
     *
     * @param maxDevices The maximum number of devices.
     * @throws IllegalArgumentException if the number of devices is is set to less
     *                                  than {@link #UNLIMITED_DEVICES_PER_TENANT}.
     */
    public void setMaxDevicesPerTenant(final int maxDevices) {
        if (maxDevices < UNLIMITED_DEVICES_PER_TENANT) {
            throw new IllegalArgumentException(
                    String.format("Maximum devices must be set to value >= %s", UNLIMITED_DEVICES_PER_TENANT));
        }
        this.maxDevicesPerTenant = maxDevices;
    }

    @Override
    protected String getDefaultCollectionName() {
        return DEFAULT_DEVICE_COLLECTION_NAME;
    }


}
