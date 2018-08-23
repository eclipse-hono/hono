/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.coap;

import java.util.Objects;

import org.eclipse.hono.config.ProtocolAdapterProperties;

/**
 * Properties for configuring an COAP adapter.
 */
public class CoapAdapterProperties extends ProtocolAdapterProperties {

    /**
     * The default regular expression to split the identity into authority and tenant.
     */
    public static final String DEFAULT_ID_SPLIT_REGEX = "@";
    /**
     * The default minimum size of device cache.
     */
    public static final int DEFAULT_DEVICE_CACHE_MIN_SIZE = 2000;
    /**
     * The default maximum size of device cache.
     */
    public static final long DEFAULT_DEVICE_CACHE_MAX_SIZE = 1000000L;

    private String idSplitRegex = DEFAULT_ID_SPLIT_REGEX;
    private String networkConfig = null;
    private String secureNetworkConfig = null;
    private String insecureNetworkConfig = null;
    private int connectorThreads = 1;
    private int coapThreads = 2;
    private int deviceCacheMinSize = DEFAULT_DEVICE_CACHE_MIN_SIZE;
    private long deviceCacheMaxSize = DEFAULT_DEVICE_CACHE_MAX_SIZE;

    public final String getIdSplitRegex() {
        return idSplitRegex;
    }

    public final void setIdSplitRegex(final String idSplitRegex) {
        this.idSplitRegex = Objects.requireNonNull(idSplitRegex);
    }

    public final String getNetworkConfig() {
        return networkConfig;
    }

    public final void setNetworkConfig(final String networkConfig) {
        this.networkConfig = Objects.requireNonNull(networkConfig);
    }

    public final String getSecureNetworkConfig() {
        return secureNetworkConfig;
    }

    public final void setSecureNetworkConfig(final String secureNetworkConfig) {
        this.secureNetworkConfig = Objects.requireNonNull(secureNetworkConfig);
    }

    public final String getInsecureNetworkConfig() {
        return insecureNetworkConfig;
    }

    public final void setInsecureNetworkConfig(final String insecureNetworkConfig) {
        this.insecureNetworkConfig = Objects.requireNonNull(insecureNetworkConfig);
    }

    /**
     * Gets the minimum size of the device cache.
     * <p>
     * The cache will be initialized with this size upon creation.
     * <p>
     * The default value is {@link #DEFAULT_DEVICE_CACHE_MIN_SIZE}.
     * 
     * @return The minimum number of results to keep in the cache.
     */
    public final int getDeviceCacheMinSize() {
        return deviceCacheMinSize;
    }

    /**
     * Sets the minimum size of the device cache.
     * 
     * @param size The minimum number of results to keep in the cache.
     * @throws IllegalArgumentException if size is &lt; 0.
     */
    public final void setDeviceCacheMinSize(final int size) {
        if (size < 0) {
            throw new IllegalArgumentException("minimum cache size must not be negative");
        }
        this.deviceCacheMinSize = size;
    }

    /**
     * Gets the maximum size of the device cache.
     * <p>
     * Once the maximum number of entries is reached, the cache applies an implementation specific policy for handling
     * new entries that are put to the cache.
     * <p>
     * The default value is {@link #DEFAULT_DEVICE_CACHE_MAX_SIZE}.
     * 
     * @return The maximum number of results to keep in the cache.
     */
    public final long getDeviceCacheMaxSize() {
        return deviceCacheMaxSize;
    }

    /**
     * Sets the maximum size of the device cache.
     * <p>
     * Once the maximum number of entries is reached, the cache applies an implementation specific policy for handling
     * new entries that are put to the cache.
     * <p>
     * Setting this property to 0 disables caching.
     * <p>
     * 
     * @param size The maximum number of results to keep in the cache.
     * @throws IllegalArgumentException if size is &lt; 0.
     */
    public final void setDeviceCacheMaxSize(final long size) {
        if (size < 0) {
            throw new IllegalArgumentException("maximum cache size must not be negative");
        }
        this.deviceCacheMaxSize = size;
    }

    /**
     * Gets the number of connector threads.
     * 
     * The connector will start sender and receiver threads, so the resulting number will be doubled!
     * 
     * @return The number of threads per connector.
     */
    public final int getConnectorThreads() {
        return connectorThreads;
    }

    /**
     * Sets the number of connector threads.
     * 
     * @param threads The number of sender and receiver threads per connector.
     * @throws IllegalArgumentException if threads is &lt; 1.
     */
    public final void setConnectorThreads(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("connector threads must not be less than one");
        }
        this.connectorThreads = threads;
    }

    /**
     * Gets the number of coap threads.
     * 
     * @return The number of threads for coap stack.
     */
    public final int getCoapThreads() {
        return coapThreads;
    }

    /**
     * Sets the number of coap protocol threads.
     * 
     * @param threads The number of threads for the coap protocol stack.
     * @throws IllegalArgumentException if threads is &lt; 1.
     */
    public final void setCoapThreads(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("protocol threads must not be less than one");
        }
        this.coapThreads = threads;
    }

}
