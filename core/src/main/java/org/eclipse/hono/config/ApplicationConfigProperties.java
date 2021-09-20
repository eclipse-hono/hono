/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.config;

import org.eclipse.hono.config.quarkus.ApplicationOptions;

/**
 * Configuration of common properties that are valid for an application (and not only a specific server).
 *
 */
public class ApplicationConfigProperties {

    private int maxInstances = 0;
    private int startupTimeout = 20;

    /**
     * Creates new properties using default values.
     */
    public ApplicationConfigProperties() {
        super();
    }

    /**
     * Creates a new instance from existing options.
     *
     * @param options The options. All of the options are copied to the newly created instance.
     */
    public ApplicationConfigProperties(final ApplicationOptions options) {
        super();
        setMaxInstances(options.maxInstances());
        setStartupTimeout(options.startupTimeout());
    }

    /**
     * Gets the maximum time to wait for the server to start up.
     * <p>
     * The default value of this property is 20 (seconds).
     *
     * @return The number of seconds to wait.
     */
    public final int getStartupTimeout() {
        return startupTimeout;
    }

    /**
     * Sets the maximum time to wait for the server to start up.
     * <p>
     * The default value of this property is 20 (seconds).
     *
     * @param seconds The maximum number of seconds to wait.
     * @throws IllegalArgumentException if <em>seconds</em> &lt; 1.
     */
    public final void setStartupTimeout(final int seconds) {
        if (seconds < 1) {
            throw new IllegalArgumentException("startup timeout must be at least 1 second");
        }
        this.startupTimeout = seconds;
    }

    /**
     * Gets the number of verticle instances to deploy.
     * <p>
     * The number is calculated as follows:
     * <ol>
     * <li>if 0 &lt; <em>maxInstances</em> &lt; #processors, then return <em>maxInstances</em></li>
     * <li>else return {@code Runtime.getRuntime().availableProcessors()}</li>
     * </ol>
     *
     * @return the number of verticles to deploy.
     */
    public final int getMaxInstances() {
        if (maxInstances > 0 && maxInstances < Runtime.getRuntime().availableProcessors()) {
            return maxInstances;
        } else {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    /**
     * Sets the number of verticle instances to deploy.
     * <p>
     * The default value of this property is 0.
     *
     * @param maxVerticleInstances The number of verticles to deploy.
     * @throws IllegalArgumentException if the number is &lt; 0.
     */
    public final void setMaxInstances(final int maxVerticleInstances) {
        if (maxVerticleInstances < 0) {
            throw new IllegalArgumentException("maxInstances must be >= 0");
        }
        this.maxInstances = maxVerticleInstances;
    }
}
