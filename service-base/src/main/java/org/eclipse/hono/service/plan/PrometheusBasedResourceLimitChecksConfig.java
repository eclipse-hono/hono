/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.plan;

import java.util.Objects;

import org.eclipse.hono.util.PortConfigurationHelper;

/**
 * The configuration properties required for the PrometheusBasedResourceLimitChecks.
 */
public final class PrometheusBasedResourceLimitChecksConfig {

    /**
     * The default minimum size of caches.
     */
    public static final int DEFAULT_CACHE_MIN_SIZE = 20;
    /**
     * The default maximum size of caches.
     */
    public static final long DEFAULT_CACHE_MAX_SIZE = 1000L;
    /**
     * The default timeout for cached data in seconds until they are considered invalid.
     */
    public static final long DEFAULT_CACHE_TIMEOUT = 600L;

    private String host;
    private int port = 9090;
    private int cacheMinSize = DEFAULT_CACHE_MIN_SIZE;
    private long cacheMaxSize = DEFAULT_CACHE_MAX_SIZE;
    private long cacheTimeout = DEFAULT_CACHE_TIMEOUT;

    /**
     * Gets the host of the Prometheus server to retrieve metrics from.
     *
     * @return host The host name or IP address.
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the host of the Prometheus server to retrieve metrics from.
     * <p>
     * The default value of this property is {@code null}.
     *
     * @param host The host name or IP address.
     */
    public void setHost(final String host) {
        this.host = Objects.requireNonNull(host);
    }

    /**
     * Gets the port of the Prometheus server to retrieve metrics from.
     *
     * @return port The port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the port of the Prometheus server to retrieve metrics from.
     * <p>
     * The default value of this property is 9090.
     *
     * @param port The port number.
     * @throws IllegalArgumentException if the port number is &lt; 0 or &gt; 2^16 - 1
     */
    public void setPort(final int port) {
        if (PortConfigurationHelper.isValidPort(port)) {
            this.port = port;
        } else {
            throw new IllegalArgumentException("invalid port number");
        }
    }

    /**
     * Gets the minimum size of the cache.
     * <p>
     * The cache will be initialized with this size upon creation.
     * <p>
     * The default value is {@link #DEFAULT_CACHE_MIN_SIZE}.
     *
     * @return The maximum number of results to keep in the cache.
     */
    public int getCacheMinSize() {
        return cacheMinSize;
    }

    /**
     * Sets the minimum size of the cache.
     * <p>
     * The cache will be initialized with this size upon creation.
     * <p>
     * The default value is {@link #DEFAULT_CACHE_MIN_SIZE}.
     *
     * @param size The maximum number of results to keep in the cache.
     * @throws IllegalArgumentException if size is &lt; 0.
     */
    public void setCacheMinSize(final int size) {
        if (size < 0) {
            throw new IllegalArgumentException("minimum cache size must not be negative");
        }
        this.cacheMinSize = size;
    }

    /**
     * Gets the maximum size of the cache.
     * <p>
     * Once the maximum number of entries is reached, the cache applies an implementation specific policy for handling
     * new entries that are put to the cache.
     * <p>
     * The default value is {@link #DEFAULT_CACHE_MAX_SIZE}.
     *
     * @return The maximum number of results to keep in the cache.
     */
    public long getCacheMaxSize() {
        return cacheMaxSize;
    }

    /**
     * Sets the maximum size of the cache.
     * <p>
     * Once the maximum number of entries is reached, the cache applies an implementation specific policy for handling
     * new entries that are put to the cache.
     * <p>
     * Setting this property to 0 disables caching.
     * <p>
     * The default value is {@link #DEFAULT_CACHE_MAX_SIZE}.
     *
     * @param size The maximum number of results to keep in the cache.
     * @throws IllegalArgumentException if size is &lt; 0.
     */
    public void setCacheMaxSize(final long size) {
        if (size < 0) {
            throw new IllegalArgumentException("maximum cache size must not be negative");
        }
        this.cacheMaxSize = size;
    }

    /**
     * Gets the period of time after which cached data are considered invalid.
     * <p>
     * The default value of this property is {@link #DEFAULT_CACHE_TIMEOUT}.
     *
     * @return The timeout for cached values in seconds.
     */
    public long getCacheTimeout() {
        return cacheTimeout;
    }

    /**
     * Sets the period of time after which cached responses should be considered invalid.
     * <p>
     * The default value of this property is {@link #DEFAULT_CACHE_TIMEOUT}.
     *
     * @param timeout The timeout in seconds.
     * @throws IllegalArgumentException if size is &lt;= 0.
     */
    public void setCacheTimeout(final long timeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("default cache timeout must be greater than zero");
        }
        this.cacheTimeout = timeout;
    }

}
