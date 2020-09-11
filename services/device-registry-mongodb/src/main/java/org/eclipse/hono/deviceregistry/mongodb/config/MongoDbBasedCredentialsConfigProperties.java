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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration properties for Hono's credentials service and management APIs.
 */
public final class MongoDbBasedCredentialsConfigProperties extends AbstractMongoDbBasedRegistryConfigProperties {

    private static Logger LOG = LoggerFactory.getLogger(MongoDbBasedCredentialsConfigProperties.class);

    /**
     * The name of the mongodb collection where devices information are stored.
     */
    private static final String DEFAULT_CREDENTIALS_COLLECTION_NAME = "credentials";

    private final Set<String> hashAlgorithmsWhitelist = new HashSet<>();

    private int maxBcryptCostFactor = 10;

    @Override
    protected String getDefaultCollectionName() {
        return DEFAULT_CREDENTIALS_COLLECTION_NAME;
    }

    /**
     * Gets the maximum cost factor to use for bcrypt
     * password hashes.
     * <p>
     * The default value of this property is 10.
     *
     * @return The maximum cost factor.
     */
    public int getMaxBcryptCostFactor() {
        return maxBcryptCostFactor;
    }

    /**
     * Gets the maximum cost factor to use for bcrypt
     * password hashes.
     * <p>
     * The default value of this property is 10.
     *
     * @return The maximum cost factor.
     * @deprecated Use {@link #getMaxBcryptCostFactor()} instead.
     */
    @Deprecated(forRemoval = true)
    public int getMaxBcryptIterations() {
        return getMaxBcryptCostFactor();
    }

    /**
     * Sets the maximum cost factor to use for bcrypt
     * password hashes.
     * <p>
     * The default value of this property is 10.
     *
     * @param costFactor The maximum cost factor.
     * @throws IllegalArgumentException if iterations is &lt; 4 or &gt; 31.
     */
    public void setMaxBcryptCostFactor(final int costFactor) {
        if (costFactor < 4 || costFactor > 31) {
            throw new IllegalArgumentException("iterations must be > 3 and < 32");
        } else {
            maxBcryptCostFactor = costFactor;
        }
    }

    /**
     * Sets the maximum cost factor to use for bcrypt
     * password hashes.
     * <p>
     * The default value of this property is 10.
     *
     * @param costFactor The maximum cost factor.
     * @throws IllegalArgumentException if iterations is &lt; 4 or &gt; 31.
     * @deprecated Use {@link #setMaxBcryptCostFactor(int)} instead.
     */
    @Deprecated(forRemoval = true)
    public void setMaxBcryptIterations(final int costFactor) {
        LOG.warn("the maxBcryptIterations property is deprecated, please update your configuration to use maxBcryptCostFactor instead");
        setMaxBcryptCostFactor(costFactor);
    }


    /**
     * Gets the list of supported hashing algorithms for pre-hashed passwords.
     * <p>
     * The device registry will not accept credentials using a hashing
     * algorithm that is not contained in this list.
     * If the list is empty, the device registry will accept any hashing algorithm.
     * <p>
     * Default value is an empty list.
     *
     * @return The supported algorithms.
     */
    public Set<String> getHashAlgorithmsWhitelist() {
        return Collections.unmodifiableSet(hashAlgorithmsWhitelist);
    }

    /**
     * Sets the list of supported hashing algorithms for pre-hashed passwords.
     * <p>
     * The device registry will not accept credentials using a hashing
     * algorithm that is not contained in this list.
     * If the list is empty, the device registry will accept any hashing algorithm.
     * <p>
     * Default value is an empty list.
     *
     * @param hashAlgorithmsWhitelist The algorithms to support.
     * @throws NullPointerException if the list is {@code null}.
     */
    public void setHashAlgorithmsWhitelist(final String[] hashAlgorithmsWhitelist) {

        Objects.requireNonNull(hashAlgorithmsWhitelist);
        this.hashAlgorithmsWhitelist.clear();
        this.hashAlgorithmsWhitelist.addAll(Arrays.asList(hashAlgorithmsWhitelist));
    }
}
