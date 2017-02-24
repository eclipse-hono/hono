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

/**
 * A POJO for configuring common properties of server components.
 *
 */
public final class HonoConfigProperties extends AbstractHonoConfig {

    private static final int MIN_PAYLOAD_SIZE = 128; // bytes

    private int maxInstances = 0;
    private int startupTimeout = 20;
    private boolean singleTenant = false;
    private boolean networkDebugLogging = false;
    private boolean waitForDownstreamConnection = false;
    private String bindAddress = "127.0.0.1";
    private int port = 0;
    private int maxPayloadSize = 2048;

    /**
     * Gets the host name or literal IP address of the network interface that this server is bound to.
     * 
     * @return The host name.
     */
    public String getBindAddress() {
        return bindAddress;
    }

    /**
     * Sets the host name or literal IP address of the network interface that this server should bind to.
     * <p>
     * The default value of this property is <em>127.0.0.1</em> (the loop back device) on IPv4 stacks.
     * 
     * @param address  The host name or IP address.
     * @throws NullPointerException if host is {@code null}.
     */
    public void setBindAddress(final String address) {
        this.bindAddress = Objects.requireNonNull(address);
    }

    /**
     * Gets the port this server is bound to/listens on.
     * <p>
     * If the port has been set to 0 this server will bind to an arbitrary free port chosen by the
     * operating system during startup. Once Hono is up and running this method returns the
     * <em>actual port</em> the server has bound to.
     * 
     * @return The port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the port that this server should bind to/listen on.
     * <p>
     * If the port is set to 0 (the default value), then this server will bind to an arbitrary free
     * port chosen by the operating system during startup.
     * 
     * @param port The port number.
     * @throws IllegalArgumentException if port &lt; 0 or port &gt; 65535.
     */
    public void setPort(final int port) {
        if (isValidPort(port)) {
            this.port = port;
        } else {
            throw new IllegalArgumentException("invalid port number");
        }
    }

    /**
     * Sets the maximum size of a message payload this server accepts from clients.
     * 
     * @param bytes The maximum number of bytes.
     * @throws IllegalArgumentException if bytes is &lt; 128.
     */
    public void setMaxPayloadSize(final int bytes) {
        if (bytes <= MIN_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("minimum message payload size is 128 bytes");
        }
        this.maxPayloadSize = bytes;
    }

    /**
     * Gets the maximum size of a message payload this server accepts from clients.
     * 
     * @return The maximum number of bytes.
     */
    public int getMaxPayloadSize() {
        return maxPayloadSize;
    }

    /**
     * Gets the maximum time to wait for Hono to start up.
     * 
     * @return The number of seconds to wait.
     */
    public int getStartupTimeout() {
        return startupTimeout;
    }

    /**
     * Sets the maximum time to wait for Hono to start up.
     * <p>
     * The default value of this property is 20 (seconds).
     * 
     * @param seconds The maximum number of seconds to wait.
     * @return This instance for setter chaining.
     * @throws IllegalArgumentException if <em>seconds</em> &lt; 1.
     */
    public HonoConfigProperties setStartupTimeout(final int seconds) {
        if (seconds < 1) {
            throw new IllegalArgumentException("startup timeout must be at least 1 second");
        }
        this.startupTimeout = seconds;
        return this;
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
    public int getMaxInstances() {
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
     * @return This instance for setter chaining.
     * @throws IllegalArgumentException if the number is &lt; 0.
     */
    public HonoConfigProperties setMaxInstances(final int maxVerticleInstances) {
        if (maxVerticleInstances < 0) {
            throw new IllegalArgumentException("maxInstances must be >= 0");
        }
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
