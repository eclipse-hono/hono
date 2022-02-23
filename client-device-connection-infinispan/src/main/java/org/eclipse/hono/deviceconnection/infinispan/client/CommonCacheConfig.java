/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceconnection.infinispan.client;

import com.google.common.base.MoreObjects;

/**
 * Common cache configuration options.
 */
public class CommonCacheConfig {

    /**
     * The default name of the (remote) cache in the data grid that is used for
     * storing device connection information.
     */
    public static final String DEFAULT_CACHE_NAME = "device-connection";

    private String cacheName = DEFAULT_CACHE_NAME;

    private String checkKey = "KEY_CONNECTION_CHECK";
    private String checkValue = "VALUE_CONNECTION_CHECK";

    /**
     * Creates properties for default values.
     */
    public CommonCacheConfig() {
        super();
    }

    /**
     * Creates properties for existing options.
     *
     * @param options The options to copy.
     */
    public CommonCacheConfig(final CommonCacheOptions options) {
        super();
        this.cacheName = options.cacheName();
        this.checkKey = options.checkKey();
        this.checkValue = options.checkValue();
    }

    public void setCacheName(final String cacheName) {
        this.cacheName = cacheName;
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCheckKey(final String checkKey) {
        this.checkKey = checkKey;
    }

    public String getCheckKey() {
        return checkKey;
    }

    public void setCheckValue(final String checkValue) {
        this.checkValue = checkValue;
    }

    public String getCheckValue() {
        return checkValue;
    }

    @Override
    public String toString() {
        return MoreObjects
                .toStringHelper(this)
                .add("cacheName", this.cacheName)
                .add("checkKey", this.checkKey)
                .add("checkValue", this.checkValue)
                .toString();
    }
}
