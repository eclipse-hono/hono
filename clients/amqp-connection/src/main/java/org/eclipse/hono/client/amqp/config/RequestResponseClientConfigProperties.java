/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.amqp.config;

/**
 * Configuration properties for clients invoking request/response operations
 * on Hono's service APIs.
 */
public class RequestResponseClientConfigProperties extends ClientConfigProperties {

    /**
     * The default minimum size of response caches.
     */
    public static final int DEFAULT_RESPONSE_CACHE_MIN_SIZE = 20;
    /**
     * The default maximum size of response caches.
     */
    public static final long DEFAULT_RESPONSE_CACHE_MAX_SIZE = 1000L;
    /**
     * The default timeout for cached responses in seconds until they are considered invalid.
     */
    public static final long DEFAULT_RESPONSE_CACHE_TIMEOUT = 600L;
    /**
     * The maximum period of time in seconds after which cached responses are considered invalid.
     */
    public static final long MAX_RESPONSE_CACHE_TIMEOUT = 24 * 60 * 60L; // 24h

    private int responseCacheMinSize = DEFAULT_RESPONSE_CACHE_MIN_SIZE;
    private long responseCacheMaxSize = DEFAULT_RESPONSE_CACHE_MAX_SIZE;
    private long responseCacheDefaultTimeout = DEFAULT_RESPONSE_CACHE_TIMEOUT;

    /**
     * Creates new properties using default values.
     */
    public RequestResponseClientConfigProperties() {
        super();
    }

    /**
     * Creates properties based on existing options.
     *
     * @param options The options to copy.
     */
    public RequestResponseClientConfigProperties(final RequestResponseClientOptions options) {
        super(options.clientOptions());
        setResponseCacheDefaultTimeout(options.responseCacheDefaultTimeout());
        setResponseCacheMaxSize(options.responseCacheMaxSize());
        setResponseCacheMinSize(options.responseCacheMinSize());
    }

    /**
     * Gets the minimum size of the response cache.
     * <p>
     * The cache will be initialized with this size upon creation.
     * <p>
     * The default value is {@link #DEFAULT_RESPONSE_CACHE_MIN_SIZE}.
     *
     * @return The maximum number of results to keep in the cache.
     */
    public final int getResponseCacheMinSize() {
        return responseCacheMinSize;
    }

    /**
     * Sets the minimum size of the response cache.
     * <p>
     * The cache will be initialized with this size upon creation.
     * <p>
     * The default value is {@link #DEFAULT_RESPONSE_CACHE_MIN_SIZE}.
     *
     * @param size The maximum number of results to keep in the cache.
     * @throws IllegalArgumentException if size is &lt; 0.
     */
    public final void setResponseCacheMinSize(final int size) {
        if (size < 0) {
            throw new IllegalArgumentException("minimum cache size must not be negative");
        }
        this.responseCacheMinSize = size;
    }

    /**
     * Gets the maximum size of the response cache.
     * <p>
     * Once the maximum number of entries is reached, the cache applies
     * an implementation specific policy for handling new entries that
     * are put to the cache.
     * <p>
     * The default value is {@link #DEFAULT_RESPONSE_CACHE_MAX_SIZE}.
     *
     * @return The maximum number of results to keep in the cache.
     */
    public final long getResponseCacheMaxSize() {
        return responseCacheMaxSize;
    }

    /**
     * Sets the maximum size of the response cache.
     * <p>
     * Once the maximum number of entries is reached, the cache applies
     * an implementation specific policy for handling new entries that
     * are put to the cache.
     * <p>
     * Setting this property to 0 disables caching.
     * <p>
     * The default value is {@link #DEFAULT_RESPONSE_CACHE_MAX_SIZE}.
     *
     * @param size The maximum number of results to keep in the cache.
     * @throws IllegalArgumentException if size is &lt; 0.
     */
    public final void setResponseCacheMaxSize(final long size) {
        if (size < 0) {
            throw new IllegalArgumentException("maximum cache size must not be negative");
        }
        this.responseCacheMaxSize = size;
    }

    /**
     * Gets the default period of time after which cached responses are considered invalid.
     * <p>
     * The default value of this property is {@value #DEFAULT_RESPONSE_CACHE_TIMEOUT}.
     *
     * @return The timeout in seconds.
     */
    public final long getResponseCacheDefaultTimeout() {
        return responseCacheDefaultTimeout;
    }

    /**
     * Sets the default period of time after which cached responses should be considered invalid.
     * <p>
     * The default value of this property is {@value #DEFAULT_RESPONSE_CACHE_TIMEOUT}.
     * The value of this property is capped at {@value #MAX_RESPONSE_CACHE_TIMEOUT}.
     *
     * @param timeout The timeout in seconds.
     * @throws IllegalArgumentException if timeout is &lt;= 0.
     */
    public final void setResponseCacheDefaultTimeout(final long timeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("default cache timeout must be greater than zero");
        }
        this.responseCacheDefaultTimeout = Math.min(timeout, MAX_RESPONSE_CACHE_TIMEOUT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new StringBuilder(getClass().getName()).append("[")
                .append("host: ").append(getHost())
                .append(", linkEstablishmentTimeout: ").append(getLinkEstablishmentTimeout())
                .append("]")
                .toString();
    }
}
