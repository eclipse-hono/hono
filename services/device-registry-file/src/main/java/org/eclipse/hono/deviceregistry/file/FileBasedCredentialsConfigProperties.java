/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.file;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration properties for Hono's credentials API as own server.
 *
 */
public final class FileBasedCredentialsConfigProperties extends AbstractFileBasedRegistryConfigProperties {

    /**
     * The default name of the file that the registry persists credentials to.
     */
    private static final String DEFAULT_CREDENTIALS_FILENAME = "/var/lib/hono/device-registry/credentials.json";

    private final Set<String> hashAlgorithmsWhitelist = new HashSet<>();

    private int maxBcryptIterations = 10;

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getDefaultFileName() {
        return DEFAULT_CREDENTIALS_FILENAME;
    }

    /**
     * Gets the maximum number of iterations to use for bcrypt
     * password hashes.
     * <p>
     * The default value of this property is 10.
     * 
     * @return The maximum number.
     */
    public int getMaxBcryptIterations() {
        return maxBcryptIterations;
    }

    /**
     * Sets the maximum number of iterations to use for bcrypt
     * password hashes.
     * <p>
     * The default value of this property is 10.
     * 
     * @param iterations The maximum number.
     * @throws IllegalArgumentException if iterations is &lt; 4 or &gt; 31.
     */
    public void setMaxBcryptIterations(final int iterations) {
        if (iterations < 4 || iterations > 31) {
            throw new IllegalArgumentException("iterations must be > 3 and < 32");
        } else {
            maxBcryptIterations = iterations;
        }
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
        for (String s : hashAlgorithmsWhitelist) {
            this.hashAlgorithmsWhitelist.add(s);
        }
    }
}
