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

import org.eclipse.hono.service.registration.RegistrationService;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;


/**
 * Configuration properties for the Hono's device registry as own server.
 *
 */
public final class FileBasedCredentialsConfigProperties {

    private static final String DEFAULT_CREDENTIALS_FILENAME = "/var/lib/hono/device-registry/credentials.json";

    private String credentialsFilename = DEFAULT_CREDENTIALS_FILENAME;
    private boolean saveToFile = false;
    private boolean modificationEnabled = true;

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
     * Gets the path to the file that the credentials registry should be persisted to
     * periodically.
     * <p>
     * Default value is <em>/home/hono/device-registry/credentials.json</em>.
     *
     * @return The file name.
     */
    public String getCredentialsFilename() {
        return credentialsFilename;
    }

    /**
     * Sets the path to the file that the credentials registry should be persisted to
     * periodically.
     * <p>
     * Default value is <em>/home/hono/device-registry/credentials.json</em>.
     *
     * @param filename The name of the file to persist to (can be a relative or absolute path).
     */
    public void setCredentialsFilename(final String filename) {
        this.credentialsFilename = filename;
    }
}
