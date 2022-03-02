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

package org.eclipse.hono.adapter.resourcelimits;

import org.eclipse.hono.client.amqp.config.AuthenticatingClientOptions;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Options for configuring the Prometheus based resource limit checks.
 *
 */
@ConfigMapping(prefix = "hono.resourceLimits.prometheusBased", namingStrategy = NamingStrategy.VERBATIM)
public interface PrometheusBasedResourceLimitCheckOptions {

    /**
     * Gets the client options.
     *
     * @return The options.
     */
    @WithParentName
    AuthenticatingClientOptions clientOptions();

    /**
     * Gets the minimum size of the cache.
     * <p>
     * The cache will be initialized with this size upon creation.
     *
     * @return The maximum number of results to keep in the cache.
     */
    @WithDefault("20")
    int cacheMinSize();

    /**
     * Gets the maximum size of the cache.
     * <p>
     * Once the maximum number of entries is reached, the cache applies an implementation specific policy for handling
     * new entries that are put to the cache.
     *
     * @return The maximum number of results to keep in the cache.
     */
    @WithDefault("1000")
    long cacheMaxSize();

    /**
     * Gets the period of time after which cached data are considered invalid.
     *
     * @return The timeout for cached values in seconds.
     */
    @WithDefault("60")
    long cacheTimeout();

    /**
     * Gets the period of time after which a request to a Prometheus server are closed.
     *
     * @return The timeout for the request to a remote server in milliseconds, zero or negative value is for disabled timeout.
     */
    @WithDefault("500")
    long queryTimeout();

    /**
     * Gets the maximum period of time that the client waits for a connection to a Prometheus server.
     *
     * @return The timeout for the attempt to establish a TCP connection to a server in milliseconds.
     */
    @WithDefault("1000")
    int connectTimeout();
}
