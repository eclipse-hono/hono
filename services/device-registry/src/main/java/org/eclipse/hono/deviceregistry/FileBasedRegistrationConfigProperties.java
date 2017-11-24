/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
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
import org.eclipse.hono.service.registration.RegistrationService;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;


/**
 * Configuration properties for the Hono's device registry as own server.
 *
 */
public final class FileBasedRegistrationConfigProperties {

    /**
     * The default number of devices that can be registered for each tenant.
     */
    public static final int DEFAULT_MAX_DEVICES_PER_TENANT = 100;
    private static final String DEFAULT_DEVICES_FILENAME = "/var/lib/hono/device-registry/device-identities.json";
    private final SignatureSupportingConfigProperties registrationAssertionProperties = new SignatureSupportingConfigProperties();

    // the name of the file used to persist the registry content
    private String filename = DEFAULT_DEVICES_FILENAME;
    private boolean saveToFile = false;
    private boolean modificationEnabled = true;
    private int maxDevicesPerTenant = DEFAULT_MAX_DEVICES_PER_TENANT;

    /**
     * Gets the path to the file that the content of the device registry should be persisted to
     * periodically.
     * <p>
     * Default value is <em>/home/hono/device-registry/device-identities.json</em>.
     * 
     * @return The file name.
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Sets the path to the file that the content of the device registry should be persisted to
     * periodically.
     * <p>
     * Default value is <em>/home/hono/device-registry/device-identities.json</em>.
     * 
     * @param filename The name of the file to persist to (can be a relative or absolute path).
     */
    public void setFilename(final String filename) {
        this.filename = filename;
    }

    /**
     * Checks whether the content of the registry should be persisted to the file system
     * periodically.
     * <p>
     * Default value is {@code false}.
     * 
     * @return {@code true} if registry content should be persisted.
     */
    public boolean isSaveToFile() {
        return saveToFile;
    }

    /**
     * Sets whether the content of the registry should be persisted to the file system
     * periodically.
     * <p>
     * Default value is {@code false}.
     * 
     * @param enabled {@code true} if registry content should be persisted.
     * @throws IllegalStateException if this registry is already running.
     */
    public void setSaveToFile(final boolean enabled) {
        this.saveToFile = enabled;
    }

    /**
     * Checks whether this registry allows modification and removal of registered devices.
     * <p>
     * If set to {@code false} then the methods {@link RegistrationService#updateDevice(String, String, JsonObject, Handler)}
     * and {@link RegistrationService#removeDevice(String, String, Handler)} always return a <em>403 Forbidden</em> response.
     * <p>
     * The default value of this property is {@code true}.
     * 
     * @return The flag.
     */
    public boolean isModificationEnabled() {
        return modificationEnabled;
    }

    /**
     * Sets whether this registry allows modification and removal of registered devices.
     * <p>
     * If set to {@code false} then the methods {@link RegistrationService#updateDevice(String, String, JsonObject, Handler)}
     * and {@link RegistrationService#removeDevice(String, String, Handler)} always return a <em>403 Forbidden</em> response.
     * <p>
     * The default value of this property is {@code true}.
     * 
     * @param flag The flag.
     */
    public void setModificationEnabled(final boolean flag) {
        modificationEnabled = flag;
    }

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
}
