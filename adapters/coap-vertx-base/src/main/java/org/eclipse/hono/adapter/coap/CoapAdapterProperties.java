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

package org.eclipse.hono.adapter.coap;

import java.util.Objects;

import org.eclipse.hono.config.ProtocolAdapterProperties;

/**
 * Properties for configuring an COAP adapter.
 */
public class CoapAdapterProperties extends ProtocolAdapterProperties {

    /**
     * The default keystore alias to load server credentials.
     */
    public static final String DEFAULT_KEYSTORE_ALIAS = "server";
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

    private String keyStoreAlias = DEFAULT_KEYSTORE_ALIAS;
    private String idSplitRegex = DEFAULT_ID_SPLIT_REGEX;
    private String networkConfig = null;
    private String secureNetworkConfig = null;
    private String insecureNetworkConfig = null;

    private int deviceCacheMinSize = DEFAULT_DEVICE_CACHE_MIN_SIZE;
    private long deviceCacheMaxSize = DEFAULT_DEVICE_CACHE_MAX_SIZE;

    private boolean preferRawPublicKey = true;

    /**
     * Alias the read server credentials from key store.
     * 
     * @return alias
     */
    public final String getKeyStoreAlias() {
        return keyStoreAlias;
    }

    public final void setKeyStoreAlias(final String keyStoreAlias) {
        this.keyStoreAlias = Objects.requireNonNull(keyStoreAlias);
    }

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
     * <p>
     * The cache will be initialized with this size upon creation.
     * <p>
     * The default value is {@link #DEFAULT_DEVICE_CACHE_MIN_SIZE}.
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
     * The default value is {@link #DEFAULT_DEVICE_CACHE_MAX_SIZE}.
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

    public final boolean isPreferRawPublicKey() {
        return preferRawPublicKey;
    }

    public final void setPreferRawPublicKey(final boolean flag) {
        this.preferRawPublicKey = flag;
    }

}
