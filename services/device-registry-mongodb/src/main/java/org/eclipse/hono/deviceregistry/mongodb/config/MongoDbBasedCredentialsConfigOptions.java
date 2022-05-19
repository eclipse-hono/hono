/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.util.Optional;
import java.util.Set;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Configuration options for Hono's credentials service and management APIs.
 */
@ConfigMapping(prefix = "hono.credentials.svc", namingStrategy = NamingStrategy.VERBATIM)
public interface MongoDbBasedCredentialsConfigOptions {

    /**
     * Gets the common registry options.
     *
     * @return The options.
     */
    @WithParentName
    MongoDbBasedRegistryConfigOptions commonOptions();

    /**
     * Gets the name of the collection to store tenant information in.
     *
     * @return The name.
     */
    @WithDefault("credentials")
    String collectionName();

    /**
     * Gets the maximum cost factor to use for bcrypt
     * password hashes.
     * <p>
     * The default value of this property is 10.
     *
     * @return The maximum cost factor.
     */
    @WithDefault("10")
    int maxBcryptCostFactor();

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
    Optional<Set<String>> hashAlgorithmsWhitelist();

    /**
     * Gets the path to the YAML file that encryption keys are read from.
     *
     * @return The path to the file.
     */
    Optional<String> encryptionKeyFile();
}
