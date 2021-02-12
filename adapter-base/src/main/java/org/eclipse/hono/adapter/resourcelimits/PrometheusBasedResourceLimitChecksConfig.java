/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.resourcelimits;

import org.eclipse.hono.config.AuthenticatingClientConfigProperties;

/**
 * Properties for configuring the Prometheus based resource limit checks.
 */
public class PrometheusBasedResourceLimitChecksConfig extends AuthenticatingClientConfigProperties {

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
    public static final long DEFAULT_CACHE_TIMEOUT = 15L;

    /**
     * The default number of milliseconds after which the client cancels queries to
     * the Prometheus REST API.
     */
    public static final long DEFAULT_QUERY_TIMEOUT = 500L;

    private int cacheMinSize = DEFAULT_CACHE_MIN_SIZE;
    private long cacheMaxSize = DEFAULT_CACHE_MAX_SIZE;
    private long cacheTimeout = DEFAULT_CACHE_TIMEOUT;
    private long queryTimeout = DEFAULT_QUERY_TIMEOUT;

    /**
     * Creates new properties with default values.
     * <p>
     * The default host is <em> localhost</em> and the default
     * port is <em>9090</em>.
     *
     */
    public PrometheusBasedResourceLimitChecksConfig() {
        super();
        setPort(9090);
    }

    /**
     * Gets the minimum size of the cache.
     * <p>
     * The cache will be initialized with this size upon creation.
     * <p>
     * The default value is {@value #DEFAULT_CACHE_MIN_SIZE}.
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
     * The default value is {@value #DEFAULT_CACHE_MIN_SIZE}.
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
     * The default value is {@value #DEFAULT_CACHE_MAX_SIZE}.
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
     * The default value is {@value #DEFAULT_CACHE_MAX_SIZE}.
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
     * The default value of this property is {@value #DEFAULT_CACHE_TIMEOUT}.
     *
     * @return The timeout for cached values in seconds.
     */
    public long getCacheTimeout() {
        return cacheTimeout;
    }

    /**
     * Sets the period of time after which cached responses should be considered invalid.
     * <p>
     * The default value of this property is {@value #DEFAULT_CACHE_TIMEOUT}.
     *
     * @param timeout The timeout in seconds.
     * @throws IllegalArgumentException if timeout is &lt;= 0.
     */
    public void setCacheTimeout(final long timeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("default cache timeout must be greater than zero");
        }
        this.cacheTimeout = timeout;
    }

    /**
     * Gets the period of time after which a request to a Prometheus server are closed.
     * <p>
     * The default value of this property is {@value #DEFAULT_QUERY_TIMEOUT}.
     *
     * @return The timeout for the request to a remote server in milliseconds, zero or negative value is for disabled timeout.
     */
    public long getQueryTimeout() {
        return queryTimeout;
    }

    /**
     * Sets the period of time after which a request to a Prometheus server are closed.
     * <p>
     * Setting zero or a negative {@code timeout} disables the timeout.
     * <p>
     * The default value of this property is {@value #DEFAULT_QUERY_TIMEOUT}.
     *
     * @param timeout The timeout in milliseconds.
     */
    public void setQueryTimeout(final long timeout) {
        this.queryTimeout = timeout;
    }
}
