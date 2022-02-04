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

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Configuration options for Hono's device registration and management APIs.
 */
@ConfigMapping(prefix = "hono.registry.svc", namingStrategy = NamingStrategy.VERBATIM)
public interface MongoDbBasedRegistrationConfigOptions {

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
    @WithDefault("devices")
    String collectionName();

    /**
     * Gets the maximum number of devices that can be registered for each tenant.
     * <p>
     * The default value of {@code -1} indicates that the number of devices is unlimited.
     *
     * @return The maximum number of devices.
     */
    @WithDefault("-1")
    int maxDevicesPerTenant();

    /**
     * Gets the regular expression that should be used to validate authentication identifiers (user names) of
     * hashed-password credentials.
     *
     * @return The regular expression to use.
     */
    Optional<String> usernamePattern();
}
