/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.config;

import java.util.Objects;

import org.eclipse.hono.util.Constants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * A POJO for Hono's global configuration properties.
 *
 */
@Component
@ConfigurationProperties(prefix = "hono")
public class HonoConfigProperties {

    private int maxInstances = 0;
    private int startupTimeout = 20;
    private boolean singleTenant = false;
    private boolean networkDebugLogging = false;
    private boolean waitForDownstreamConnection = false;
    private String pathSeparator = Constants.DEFAULT_PATH_SEPARATOR;

    public String getPathSeparator() {
        return pathSeparator;
    }

    /**
     * @param pathSeparator the character to use for separating the segments
     *                      of message addresses.
     * @throws NullPointerException if the given character is {@code null}.
     */
    public HonoConfigProperties setPathSeparator(final String pathSeparator) {
        this.pathSeparator = Objects.requireNonNull(pathSeparator);
        return this;
    }

    /**
     * Gets the maximum time to wait for Hono to start up.
     * 
     * @return The number of seconds to wait for Hono to start up.
     */
    public int getStartupTimeout() {
        return startupTimeout;
    }

    public HonoConfigProperties setStartupTimeout(final int seconds) {
        this.startupTimeout = seconds;
        return this;
    }

    public int getMaxInstances() {
        if (maxInstances > 0 && maxInstances < Runtime.getRuntime().availableProcessors()) {
            return maxInstances;
        } else {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    public HonoConfigProperties setMaxInstances(final int maxVerticleInstances) {
        this.maxInstances = maxVerticleInstances;
        return this;
    }

    /**
     * Checks whether Hono runs in single-tenant mode.
     * <p>
     * In this mode clients do not need to specify a <em>tenant</em>
     * component in resource addresses. Hono will use the
     * {@link Constants#DEFAULT_TENANT} instead.
     * 
     * @return {@code true} if Hono runs in single-tenant mode.
     */
    public boolean isSingleTenant() {
        return singleTenant;
    }

    /**
     * Sets whether Hono should support a single tenant only.
     * <p>
     * In this mode clients do not need to specify a <em>tenant</em>
     * component in resource addresses. Hono will use the
     * {@link Constants#DEFAULT_TENANT} instead.
     * <p>
     * The default value of this property is {@code false}.
     * 
     * @param singleTenant {@code true} if this Hono server should support a single tenant only.
     * @return This instance for setter chaining.
     */
    public HonoConfigProperties setSingleTenant(final boolean singleTenant) {
        this.singleTenant = singleTenant;
        return this;
    }

    /**
     * Checks whether Hono should log TCP traffic.
     * 
     * @return {@code true} if TCP traffic gets logged.
     */
    public boolean isNetworkDebugLoggingEnabled() {
        return networkDebugLogging;
    }

    /**
     * Sets whether Hono should log TCP traffic.
     * <p>
     * The default value of this property is {@code false}.
     * 
     * @param networkDebugLogging {@code true} if TCP traffic should be logged.
     * @return This instance for setter chaining.
     */
    public HonoConfigProperties setNetworkDebugLoggingEnabled(final boolean networkDebugLogging) {
        this.networkDebugLogging = networkDebugLogging;
        return this;
    }

    /**
     * Checks whether Hono waits for downstream connections to be established
     * during startup.
     * <p>
     * If this property is set to {@code true} then startup may take some time or even
     * time out if the downstream container to connect to is not (yet) available.
     * <p>
     * The default value of this property is {@code false}.
     * 
     * @return {@code true} if Hono waits for downstream connection to be established during startup.
     */
    public boolean isWaitForDownstreamConnectionEnabled() {
        return waitForDownstreamConnection;
    }

    /**
     * Sets whether Hono should wait for downstream connections to be established
     * during startup.
     * <p>
     * If this property is set to {@code true} then startup may take some time or even
     * time out if the downstream container to connect to is not (yet) available.
     * <p>
     * The default value of this property is {@code false}.
     * 
     * @param waitForConnection {@code true} if Hono should wait for downstream connections to be established during startup.
     * @return This instance for setter chaining.
     */
    public HonoConfigProperties setWaitForDownstreamConnectionEnabled(final boolean waitForConnection) {
        this.waitForDownstreamConnection = waitForConnection;
        return this;
    }
}
