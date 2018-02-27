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

/**
 * Common configuration properties for file based implementations of the APIs of Hono's device registry as own server.
 * <p>
 * This class is intended to be used as base class fpr property classes that configure a specific file based API implementation.
 */
public abstract class AbstractFileBasedRegistryConfigProperties {

    protected String filename = getDefaultFileName();
    protected boolean saveToFile = false;
    protected boolean modificationEnabled = true;

    /**
     * Gets the path to the file that the registry should be persisted to periodically.
     * <p>
     *
     * @return The path to the file.
     */
    protected abstract String getDefaultFileName();

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
     * Checks whether this registry allows the creation, modification and removal of entries.
     * <p>
     * If set to {@code false} then methods for creating, updating or deleting an entry should return a <em>403 Forbidden</em> response.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @return The flag.
     */
    public boolean isModificationEnabled() {
        return modificationEnabled;
    }

    /**
     * Sets whether this registry allows creation, modification and removal of entries.
     * <p>
     * If set to {@code false} then for creating, updating or deleting an entry should return a <em>403 Forbidden</em> response.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @param flag The flag.
     */
    public void setModificationEnabled(final boolean flag) {
        modificationEnabled = flag;
    }

    /**
     * Gets the path to the file that the registry should be persisted to
     * periodically.
     * <p>
     *
     * @return The file name.
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Sets the path to the file that the registry should be persisted to
     * periodically.
     * <p>
     *
     * @param filename The name of the file to persist to (can be a relative or absolute path).
     */
    public void setFilename(final String filename) {
        this.filename = filename;
    }

}
