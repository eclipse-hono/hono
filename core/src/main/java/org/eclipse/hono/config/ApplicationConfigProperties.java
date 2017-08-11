/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
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
import org.eclipse.hono.util.PortConfigurationHelper;

/**
 * Configuration of common properties that are valid for an application (and not only a specific server).
 *
 */
public class ApplicationConfigProperties {

    private int maxInstances = 0;
    private int startupTimeout = 20;

    private int healthCheckPort = Constants.PORT_UNCONFIGURED;
    private String healthCheckBindAddress = Constants.LOOPBACK_DEVICE_ADDRESS;

    /**
     * Gets the maximum time to wait for the server to start up.
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

    /**
     * Gets the port that the HTTP server hosting the health check resource is configured to listen on.
     *
     * @return The port number.
     */
    public final int getHealthCheckPort() {
        return healthCheckPort;
    }

    /**
     * Sets the port that the HTTP server hosting the health check resource should listen on.
     *
     * @param port The port number.
     * @throws IllegalArgumentException if the port number is &lt; 0 or &gt; 2^16 - 1
     */
    public final void setHealthCheckPort(final int port) {
        if (PortConfigurationHelper.isValidPort(port)) {
            this.healthCheckPort = port;
        } else {
            throw new IllegalArgumentException("invalid port number");
        }
    }

    /**
     * Gets the host name or literal IP address of the network interface that the HTTP server hosting the health check
     * resource is configured to be bound to.
     *
     * @return The host name.
     */
    public final String getHealthCheckBindAddress() {
        return healthCheckBindAddress;
    }

    /**
     * Sets the host name or literal IP address of the network interface that the HTTP server hosting the health check
     * resource should be bound to.
     * <p>
     * The default value of this property is {@link Constants#LOOPBACK_DEVICE_ADDRESS} on IPv4 stacks.
     *
     * @param address The host name or IP address.
     * @throws NullPointerException if host is {@code null}.
     */
    public final void setHealthCheckBindAddress(final String address) {
        this.healthCheckBindAddress = Objects.requireNonNull(address);
    }

}
