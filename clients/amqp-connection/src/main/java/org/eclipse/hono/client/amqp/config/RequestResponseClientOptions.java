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

package org.eclipse.hono.client.amqp.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Options for configuring clients which invoke request/response operations on Hono's service APIs.
 *
 */
@ConfigMapping(prefix = "hono.requestResponseClient", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface RequestResponseClientOptions {

    /**
     * Gets the client options.
     *
     * @return The options.
     */
    @WithParentName
    ClientOptions clientOptions();

    /**
     * Gets the minimum size of the response cache.
     * <p>
     * The cache will be initialized with this size upon creation.
     *
     * @return The maximum number of results to keep in the cache.
     */
    @WithDefault("20")
    int responseCacheMinSize();

    /**
     * Gets the maximum size of the response cache.
     * <p>
     * Once the maximum number of entries is reached, the cache applies
     * an implementation specific policy for handling new entries that
     * are put to the cache.
     *
     * @return The maximum number of results to keep in the cache.
     */
    @WithDefault("1000")
    long responseCacheMaxSize();

    /**
     * Gets the default period of time after which cached responses are considered invalid.
     *
     * @return The timeout in seconds.
     */
    @WithDefault("600")
    long responseCacheDefaultTimeout();
}
