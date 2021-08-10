/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.deviceconnection.infinispan.client;

import org.eclipse.hono.util.CommandRouterConstants;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;

/**
 * Common options for configuring a cache.
 *
 */
@ConfigMapping(prefix = "hono.cache.common", namingStrategy = NamingStrategy.VERBATIM)
public interface CommonCacheOptions {

    /**
     * Gets the name of the cache.
     *
     * @return The name.
     */
    @WithDefault(CommandRouterConstants.DEFAULT_CACHE_NAME)
    String cacheName();

    /**
     * Gets the key to use for checking the cache's availability.
     *
     * @return The key.
     */
    @WithDefault("KEY_CONNECTION_CHECK")
    String checkKey();

    /**
     * The value to use for checking the cache's availability.
     *
     * @return The value.
     */
    @WithDefault("VALUE_CONNECTION_CHECK")
    String checkValue();
}
