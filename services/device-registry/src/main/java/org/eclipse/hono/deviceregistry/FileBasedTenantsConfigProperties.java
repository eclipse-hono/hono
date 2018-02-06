/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.deviceregistry;

import org.eclipse.hono.service.tenant.TenantService;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;


/**
 * Configuration properties for the Hono's tenant manager as own server.
 *
 */
public final class FileBasedTenantsConfigProperties {

    private static final String DEFAULT_TENANTS_FILENAME = "/var/lib/hono/device-registry/tenants.json";

    // the name of the file used to persist the tenant content
    private String filename = DEFAULT_TENANTS_FILENAME;
    private boolean saveToFile = false;
    private boolean modificationEnabled = true;

    /**
     * Gets the path to the file that the content of the tenant manager should be persisted to
     * periodically.
     * <p>
     * Default value is <em>/home/hono/device-registry/tenants.json</em>.
     * 
     * @return The file name.
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Sets the path to the file that the content of the tenant manager should be persisted to
     * periodically.
     * <p>
     * Default value is <em>/home/hono/device-registry/tenants.json</em>.
     * 
     * @param filename The name of the file to persist to (can be a relative or absolute path).
     */
    public void setFilename(final String filename) {
        this.filename = filename;
    }

    /**
     * Checks whether the content of the tenant manager should be persisted to the file system
     * periodically.
     * <p>
     * Default value is {@code false}.
     * 
     * @return {@code true} if tenant manager content should be persisted.
     */
    public boolean isSaveToFile() {
        return saveToFile;
    }

    /**
     * Sets whether the content of the tenant manager should be persisted to the file system
     * periodically.
     * <p>
     * Default value is {@code false}.
     * 
     * @param enabled {@code true} if content of the tenant manager should be persisted.
     * @throws IllegalStateException if this tenant manager is already running.
     */
    public void setSaveToFile(final boolean enabled) {
        this.saveToFile = enabled;
    }

    /**
     * Checks whether this tenant manager allows modification and removal of registered tenants.
     * <p>
     * If set to {@code false} then the methods {@link TenantService#update(String, JsonObject, Handler)}
     * and {@link TenantService#remove(String, Handler)} always return a <em>403 Forbidden</em> response.
     * <p>
     * The default value of this property is {@code true}.
     * 
     * @return The flag.
     */
    public boolean isModificationEnabled() {
        return modificationEnabled;
    }

    /**
     * Sets whether this tenant manager allows modification and removal of registered tenants.
     * <p>
     * If set to {@code false} then the methods {@link TenantService#update(String, JsonObject, Handler)}
     * and {@link TenantService#remove(String, Handler)} always return a <em>403 Forbidden</em> response.
     * <p>
     * The default value of this property is {@code true}.
     * 
     * @param flag The flag.
     */
    public void setModificationEnabled(final boolean flag) {
        modificationEnabled = flag;
    }
}
