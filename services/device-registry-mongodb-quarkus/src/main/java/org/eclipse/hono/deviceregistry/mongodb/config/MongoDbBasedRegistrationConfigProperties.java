/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import java.util.Objects;
import java.util.regex.Pattern;

import org.eclipse.hono.util.RegistryManagementConstants;

/**
 * Configuration properties for Hono's device registration and management APIs.
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
     * Creates default properties.
     */
    public MongoDbBasedRegistrationConfigProperties() {
        // do nothing
    }

    /**
     * Creates properties from existing options.
     *
     * @param options The options.
     * @throws NullPointerException if options are {@code null}.
     */
    public MongoDbBasedRegistrationConfigProperties(final MongoDbBasedRegistrationConfigOptions options) {
        super(options.commonOptions());
        this.setCollectionName(options.collectionName());
        this.setMaxDevicesPerTenant(options.maxDevicesPerTenant());
        options.usernamePattern().ifPresent(this::setUsernamePattern);
    }

    /**
     * Checks if the number of devices per tenant is limited.
     *
     * @return {@code true} if the configured number of devices per tenant is &gt;
     *         {@value #UNLIMITED_DEVICES_PER_TENANT}.
     */
    public boolean isNumberOfDevicesPerTenantLimited() {
        return maxDevicesPerTenant > UNLIMITED_DEVICES_PER_TENANT;
    }

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

    /**
     * Sets the regular expression that should be used to validate authentication identifiers (user names) of
     * hashed-password credentials.
     * <p>
     * After successful validation of the expression's syntax, the regex is set as the value
     * of system property {@value RegistryManagementConstants#SYSTEM_PROPERTY_USERNAME_REGEX}.
     *
     * @param regex The regular expression to use.
     * @throws NullPointerException if regex is {@code null}.
     * @throws java.util.regex.PatternSyntaxException if regex is not a valid regular expression.
     */
    public void setUsernamePattern(final String regex) {
        Objects.requireNonNull(regex);
        // verify regex syntax
        Pattern.compile(regex);
        System.setProperty(RegistryManagementConstants.SYSTEM_PROPERTY_USERNAME_REGEX, regex);
    }
}
