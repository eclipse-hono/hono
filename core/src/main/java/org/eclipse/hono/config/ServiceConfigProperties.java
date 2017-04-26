/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
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
public final class ServiceConfigProperties extends AbstractConfig {

    /**
     * The loopback device address.
     */
    public static final String LOOPBACK_DEVICE_ADDRESS = "127.0.0.1";
    private static final int MIN_PAYLOAD_SIZE  = 128; // bytes

    private int maxInstances = 0;
    private int startupTimeout = 20;
    private boolean singleTenant = false;
    private boolean networkDebugLogging = false;
    private boolean waitForDownstreamConnection = false;
    private String bindAddress = LOOPBACK_DEVICE_ADDRESS;
    private int port = Constants.PORT_UNCONFIGURED;
    private boolean insecurePortEnabled = false;
    private String insecurePortBindAddress = LOOPBACK_DEVICE_ADDRESS;
    private int insecurePort = Constants.PORT_UNCONFIGURED;
    private int maxPayloadSize = 2048;

    /**
     * Gets the host name or literal IP address of the network interface that this server's secure port is
     * configured to be bound to.
     *
     * @return The host name.
     */
    public String getBindAddress() {
        return bindAddress;
    }

    /**
     * Sets the host name or literal IP address of the network interface that this server's secure port should be bound to.
     * <p>
     * The default value of this property is {@link #LOOPBACK_DEVICE_ADDRESS} on IPv4 stacks.
     *
     * @param address  The host name or IP address.
     * @throws NullPointerException if host is {@code null}.
     */
    public void setBindAddress(final String address) {
        this.bindAddress = Objects.requireNonNull(address);
    }

    /**
     * Gets the secure port this server is configured to listen on.
     *
     * @return The port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the secure port this server is configured to listen on.
     *
     * @param defaultPort The port to use if this property has not been set explicitly.
     * @return The configured port number or the <em>defaultPort</em> if <em>port</em> is not set.
     * @see #getPort() for more information.
     */
    public int getPort(final int defaultPort) {

        return port == Constants.PORT_UNCONFIGURED ? defaultPort : port;
    }

    /**
     * Sets the secure port that this server should listen on.
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
     * Checks if this server is configured to listen on an insecure port (i.e. without TLS) at all.
     * If false, it is guaranteed by the server that no opened port is insecure.
     * If true, it enables the definition of an insecure port (as the only port <u>or</u> additionally to the secure port).
     *
     * @return {@code true} if the server guarantees that no opened port is insecure.
     */
    public boolean isInsecurePortEnabled() {
        return insecurePortEnabled;
    }

    /**
     * Sets if this server should support insecure AMQP 1.0 ports (i.e. without TLS) at all.
     * If false, it is guaranteed by the server that no opened port is insecure.
     * If true, it enables the definition of an insecure port (as the only port <u>or</u> additionally to the secure port).
     *
     * @param insecurePortEnabled {@code true} if the server shall guarantee that no opened port is insecure.
     */
    public void setInsecurePortEnabled(final boolean insecurePortEnabled) {
        this.insecurePortEnabled = insecurePortEnabled;
    }

    /**
     * Gets the host name or literal IP address of the network interface that this server's insecure port is
     * configured to be bound to.
     *
     * @return The host name.
     */
    public String getInsecurePortBindAddress() {
        return insecurePortBindAddress;
    }

    /**
     * Sets the host name or literal IP address of the network interface that this server's insecure port should be bound to.
     * <p>
     * The default value of this property is {@link #LOOPBACK_DEVICE_ADDRESS} on IPv4 stacks.
     *
     * @param address The host name or IP address.
     * @throws NullPointerException if address is {@code null}.
     */
    public void setInsecurePortBindAddress(final String address) {
        this.insecurePortBindAddress = Objects.requireNonNull(address);
    }

    /**
     * Gets the insecure port this server is configured to listen on.
     *
     * @return The port number.
     */
    public int getInsecurePort() {
        return insecurePort;
    }

    /**
     * Gets the insecure port this server is configured to listen on.
     *
     * @param defaultPort The port to use if this property has not been set explicitly.
     * @return The configured port number or the <em>defaultPort</em> if <em>insecurePort</em> is not set.
     * @see #getInsecurePort() for more information.
     */
    public int getInsecurePort(final int defaultPort) {

        return insecurePort == Constants.PORT_UNCONFIGURED ? defaultPort : insecurePort;
    }

    /**
     * Sets the insecure port that this server should listen on.
     * <p>
     * If the port is set to 0 (the default value), then this server will bind to an arbitrary free
     * port chosen by the operating system during startup.
     * <p>
     * Setting this property also sets the <em>insecurePortEnabled</em> property to {@code true}.
     * 
     * @param port The port number.
     * @throws IllegalArgumentException if port &lt; 0 or port &gt; 65535.
     */
    public void setInsecurePort(final int port) {
        if (isValidPort(port)) {
            this.insecurePort = port;
            setInsecurePortEnabled(true);
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
     * Gets the maximum time to wait for the server to start up.
     *
     * @return The number of seconds to wait.
     */
    public int getStartupTimeout() {
        return startupTimeout;
    }

    /**
     * Sets the maximum time to wait for the server to start up.
     * <p>
     * The default value of this property is 20 (seconds).
     *
     * @param seconds The maximum number of seconds to wait.
     * @return This instance for setter chaining.
     * @throws IllegalArgumentException if <em>seconds</em> &lt; 1.
     */
    public ServiceConfigProperties setStartupTimeout(final int seconds) {
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
    public ServiceConfigProperties setMaxInstances(final int maxVerticleInstances) {
        if (maxVerticleInstances < 0) {
            throw new IllegalArgumentException("maxInstances must be >= 0");
        }
        this.maxInstances = maxVerticleInstances;
        return this;
    }

    /**
     * Checks whether the server is configured to run in single-tenant mode.
     * <p>
     * In this mode clients do not need to specify a <em>tenant</em>
     * component in resource addresses. The server will use the
     * {@link Constants#DEFAULT_TENANT} instead.
     *
     * @return {@code true} if the server is configured to run in single-tenant mode.
     */
    public boolean isSingleTenant() {
        return singleTenant;
    }

    /**
     * Sets whether the server should support a single tenant only.
     * <p>
     * In this mode clients do not need to specify a <em>tenant</em>
     * component in resource addresses. The server will use the
     * {@link Constants#DEFAULT_TENANT} instead.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @param singleTenant {@code true} if the server should support a single tenant only.
     * @return This instance for setter chaining.
     */
    public ServiceConfigProperties setSingleTenant(final boolean singleTenant) {
        this.singleTenant = singleTenant;
        return this;
    }

    /**
     * Checks whether the server is configured to log TCP traffic.
     *
     * @return {@code true} if TCP traffic gets logged.
     */
    public boolean isNetworkDebugLoggingEnabled() {
        return networkDebugLogging;
    }

    /**
     * Sets whether the server should log TCP traffic.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @param networkDebugLogging {@code true} if TCP traffic should be logged.
     * @return This instance for setter chaining.
     */
    public ServiceConfigProperties setNetworkDebugLoggingEnabled(final boolean networkDebugLogging) {
        this.networkDebugLogging = networkDebugLogging;
        return this;
    }

    /**
     * Checks whether the server waits for downstream connections to be established
     * during startup.
     * <p>
     * If this property is set to {@code true} then startup may take some time or even
     * time out if the downstream container to connect to is not (yet) available.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @return {@code true} if the server will wait for downstream connections to be established during startup.
     */
    public boolean isWaitForDownstreamConnectionEnabled() {
        return waitForDownstreamConnection;
    }

    /**
     * Sets whether the server should wait for downstream connections to be established
     * during startup.
     * <p>
     * If this property is set to {@code true} then startup may take some time or even
     * time out if the downstream container to connect to is not (yet) available.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @param waitForConnection {@code true} if the server should wait for downstream connections to be established during startup.
     * @return This instance for setter chaining.
     */
    public ServiceConfigProperties setWaitForDownstreamConnectionEnabled(final boolean waitForConnection) {
        this.waitForDownstreamConnection = waitForConnection;
        return this;
    }
}
