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

package org.eclipse.hono.deviceregistry.jdbc.config;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Device service configuration properties.
 */
@ConfigMapping(prefix = "hono.registry.svc", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface DeviceServiceOptions {

    /**
     * Gets the maximum number of devices that can be registered for each tenant.
     * <p>
     * The default value indicates that no limit is defined.
     *
     * @return The maximum number of devices.
     */
    @WithDefault("-1")
    int maxDevicesPerTenant();

    /**
     * Gets the duration after which retrieved credentials information must be considered stale.
     *
     * @return The duration.
     */
    @WithDefault("PT1M")
    Duration credentialsTtl();

    /**
     * Gets the duration after which retrieved device registration information must be considered stale.
     * <p>
     * The default value of this property is one minute.
     *
     * @return The duration.
     */
    @WithDefault("PT1M")
    Duration registrationTtl();

    /**
     * Gets the maximum number of iterations to use for bcrypt
     * password hashes.
     * <p>
     * The default value of this property is 10.
     *
     * @return The maximum number.
     */
    @WithDefault("10")
    int maxBcryptCostFactor();

    /**
     * Gets the list of supported hashing algorithms for pre-hashed passwords.
     * <p>
     * The device registry will not accept credentials using a hashing
     * algorithm that is not contained in this list.
     * If the list is empty, the device registry will accept any hashing algorithm.
     *
     * @return The supported algorithms.
     */
    Optional<Set<String>> hashAlgorithmsAllowList();

    /**
     * Gets the regular expression that should be used to validate authentication identifiers (user names) of
     * hashed-password credentials.
     *
     * @return The regular expression to use.
     */
    Optional<String> usernamePattern();
}
