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
 * Common configuration properties for Mongodb based implementations of the APIs of Hono's device registry as own server.
 * <p>
 * This class is intended to be used as base class for property classes that configure a specific mongodb based API implementation.
 */
public abstract class AbstractMongoDbBasedRegistryConfigProperties {

    /**
     * The default number of seconds that information returned by the service's
     * operations may be cached for.
     */
    public static final int DEFAULT_MAX_AGE_SECONDS = 180;
    private int cacheMaxAge = DEFAULT_MAX_AGE_SECONDS;
    /**
     * Mongodb collection name for individual device registry service entity type.
     */
    private String collectionName = getDefaultCollectionName();
    /**
     * Option if modification on device registry is permitted.
     */
    private boolean modificationEnabled = true;

    /**
     * Gets the Mongodb collection name that the registry should be persisted to periodically.
     * <p>
     *
     * @return The Mongodb collection name .
     */
    protected abstract String getDefaultCollectionName();

    /**
     * Gets the Mongodb collection name that the registry should be persisted to
     * periodically.
     * <p>
     *
     * @return The Mongodb collection name.
     */
    public final String getCollectionName() {
        return collectionName;
    }

    /**
     * Sets the Mongodb collection name that the registry should be persisted to
     * periodically.
     * <p>
     *
     * @param collectionName The Mongodb collection name to persist to.
     */
    public final void setCollectionName(final String collectionName) {
        this.collectionName = collectionName;
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
    public final boolean isModificationEnabled() {
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
    public final void setModificationEnabled(final boolean flag) {
        modificationEnabled = flag;
    }

    /**
     * Gets the maximum period of time that information returned by the service's
     * operations may be cached for.
     * <p>
     * The default value of this property is {@link #DEFAULT_MAX_AGE_SECONDS} seconds.
     *
     * @return The period of time in seconds.
     */
    public final int getCacheMaxAge() {
        return cacheMaxAge;
    }

    /**
     * Sets the maximum period of time that information returned by the service's
     * operations may be cached for.
     * <p>
     * The default value of this property is {@link #DEFAULT_MAX_AGE_SECONDS} seconds.
     *
     * @param maxAge The period of time in seconds.
     * @throws IllegalArgumentException if max age is &lt; 0.
     */
    public final void setCacheMaxAge(final int maxAge) {
        if (maxAge < 0) {
            throw new IllegalArgumentException("max age must be >= 0");
        }
        this.cacheMaxAge = maxAge;
    }

}
