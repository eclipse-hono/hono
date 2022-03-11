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
    private String encryptionKeyFile;

    /**
     * Creates default properties.
     */
    protected AbstractMongoDbBasedRegistryConfigProperties() {
        // do nothing
    }

    /**
     * Creates properties from existing options.
     *
     * @param options The options.
     * @throws NullPointerException if options are {@code null}.
     */
    protected AbstractMongoDbBasedRegistryConfigProperties(final MongoDbBasedRegistryConfigOptions options) {
        Objects.requireNonNull(options);
        this.setCacheMaxAge(options.cacheMaxAge());
    }

    /**
     * Gets the default name of the Mongo DB collection that the registry's data should be persisted to.
     *
     * @return The name.
     */
    protected abstract String getDefaultCollectionName();

    /**
     * Gets the name of the Mongo DB collection that the registry's data should be persisted to.
     *
     * @return The name.
     */
    public final String getCollectionName() {
        return collectionName;
    }

    /**
     * Gets the name of the Mongo DB collection that the registry's data gets persisted to.
     *
     * @param collectionName The name.
     * @throws NullPointerException if name is {@code null}.
     */
    public final void setCollectionName(final String collectionName) {
        this.collectionName = Objects.requireNonNull(collectionName);
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

    /**
     * Sets the path to the YAML file that encryption keys should be read from.
     *
     * @param path The path to the file.
     * @throws NullPointerException if path is {@code null}.
     */
    public final void setEncryptionKeyFile(final String path) {
        this.encryptionKeyFile = Objects.requireNonNull(path);
    }

    /**
     * Gets the path to the YAML file that encryption keys are read from.
     *
     * @return The path to the file or {@code null} if not set.
     */
    public final String getEncryptionKeyFile() {
        return this.encryptionKeyFile;
    }
}
