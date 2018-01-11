/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.client;

import org.eclipse.hono.config.ClientConfigProperties;


/**
 * Configuration properties for clients invoking request/response operations
 * on Hono's service APIs.
 *
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

    private int responseCacheMinSize = DEFAULT_RESPONSE_CACHE_MIN_SIZE;
    private long responseCacheMaxSize = DEFAULT_RESPONSE_CACHE_MAX_SIZE;

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
    public final void setResponseCacheMinSize(int size) {
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
    public final void setResponseCacheMaxSize(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("maximum cache size must not be negative");
        }
        this.responseCacheMaxSize = size;
    }
}
