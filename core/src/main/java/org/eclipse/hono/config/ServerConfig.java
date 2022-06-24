/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

import java.util.Objects;

import org.eclipse.hono.util.Constants;

/**
 * Configuration of properties that are common for components accepting requests/connections on a network socket.
 */
public class ServerConfig extends AbstractConfig {

    private int port = Constants.PORT_UNCONFIGURED;
    private String bindAddress = Constants.LOOPBACK_DEVICE_ADDRESS;
    private boolean nativeTlsRequired = false;
    private boolean insecurePortEnabled = false;
    private String insecurePortBindAddress = Constants.LOOPBACK_DEVICE_ADDRESS;
    private int insecurePort = Constants.PORT_UNCONFIGURED;
    private boolean sni = false;
    /**
     * Creates new properties using default values.
     */
    public ServerConfig() {
        super();
    }

    /**
     * Creates a new instance from existing options.
     *
     * @param options The options. All of the options are copied to the newly created instance.
     */
    public ServerConfig(final ServerOptions options) {
        super(options.genericOptions());
        this.bindAddress = options.bindAddress();
        if (options.insecurePort() > Constants.PORT_UNCONFIGURED) {
            setInsecurePort(options.insecurePort());
        }
        this.insecurePortBindAddress = options.insecurePortBindAddress();
        this.insecurePortEnabled = options.insecurePortEnabled();
        this.nativeTlsRequired = options.nativeTlsRequired();
        if (options.port() > Constants.PORT_UNCONFIGURED) {
            setPort(options.port());
        }
        this.sni = options.sni();
    }

    /**
     * Checks if the current thread is running on the Graal Substrate VM.
     *
     * @return {@code true} if the <em>java.vm.name</em> system property value start with {@code Substrate}.
     */
    public final boolean isSubstrateVm() {
        return System.getProperty("java.vm.name", "unknown").startsWith("Substrate");
    }

    /**
     * Gets the host name or literal IP address of the network interface that this server's secure port is configured to
     * be bound to.
     *
     * @return The host name.
     */
    public final String getBindAddress() {
        return bindAddress;
    }

    /**
     * Sets the host name or literal IP address of the network interface that this server's secure port should be bound
     * to.
     * <p>
     * The default value of this property is {@link Constants#LOOPBACK_DEVICE_ADDRESS} on IPv4 stacks.
     *
     * @param address The host name or IP address.
     * @throws NullPointerException if host is {@code null}.
     */
    public final void setBindAddress(final String address) {
        this.bindAddress = Objects.requireNonNull(address);
    }

    /**
     * Gets the secure port this server is configured to listen on.
     *
     * @return The port number.
     */
    public final int getPort() {
        return port;
    }

    /**
     * Gets the secure port this server is configured to listen on.
     *
     * @param defaultPort The port to use if this property has not been set explicitly.
     * @return The configured port number or the <em>defaultPort</em> if <em>port</em> is not set.
     * @see #getPort() for more information.
     */
    public final int getPort(final int defaultPort) {

        return port == Constants.PORT_UNCONFIGURED ? defaultPort : port;
    }

    /**
     * Sets the secure port that this server should listen on.
     * <p>
     * If the port is set to 0 (the default value), then this server will bind to an arbitrary free port chosen by the
     * operating system during startup.
     *
     * @param port The port number.
     * @throws IllegalArgumentException if port &lt; 0 or port &gt; 65535.
     */
    public final void setPort(final int port) {
        if (isValidPort(port)) {
            this.port = port;
        } else {
            throw new IllegalArgumentException("invalid port number");
        }
    }


    /**
     * Checks whether this service should listen to the secure port.
     * <p>
     * Subclasses may override this method in order to do more sophisticated checks.
     *
     * @return {@code true} if a valid key and certificate have been configured for this service..
     */
    public boolean isSecurePortEnabled() {
        return getKeyCertOptions() != null;
    }

    /**
     * Checks if this server requires the usage of a native TLS implementation. Native TLS implementations offer in
     * general a better performance but may not be available on all platforms. If {@code true}, the server will require
     * the usage of a native TLS implementation. Server will not start if native implementation is not available on the
     * current system. If {@code false}, the adapter will try to use a native TLS implementation. If no native
     * implementation is available the default Java platform independent TLS implementation will be used.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @return {@code true} if the server requires native TLS implementation.
     */
    public final boolean isNativeTlsRequired() {
        return nativeTlsRequired;
    }

    /**
     * Sets if this server should require the usage of a native TLS implementation. Native TLS implementations offer in
     * general a better performance but may not be available on all platforms. If {@code true}, the server will require
     * the usage of a native TLS implementation. Server will not start if native implementation is not available on the
     * current system. If {@code false}, the adapter will try to use a native TLS implementation. If no native
     * implementation is available the default Java platform independent TLS implementation will be used.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @param nativeTlsRequired {@code true} if the server requires the usage of a native TLS implementation.
     */
    public final void setNativeTlsRequired(final boolean nativeTlsRequired) {
        this.nativeTlsRequired = nativeTlsRequired;
    }

    /**
     * Checks whether this service should listen to the insecure port.
     * <p>
     * The default value of this property is {@code false}.
     * <p>
     * Subclasses may override this method in order to do more sophisticated checks.
     *
     * @return {@code true} if the insecure port has been enabled explicitly using {@link #setInsecurePortEnabled(boolean)}
     *                      or implicitly using {@link #setInsecurePort(int)}.
     */
    public boolean isInsecurePortEnabled() {
        return insecurePortEnabled || insecurePort > Constants.PORT_UNCONFIGURED;
    }

    /**
     * Sets whether this server should listen on the insecure port.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @param insecurePortEnabled {@code true} if the server should listen on the insecure port.
     */
    public final void setInsecurePortEnabled(final boolean insecurePortEnabled) {
        this.insecurePortEnabled = insecurePortEnabled;
    }

    /**
     * Gets the host name or literal IP address of the network interface that this server's insecure port is configured
     * to be bound to.
     *
     * @return The host name.
     */
    public final String getInsecurePortBindAddress() {
        return insecurePortBindAddress;
    }

    /**
     * Sets the host name or literal IP address of the network interface that this server's insecure port should be
     * bound to.
     * <p>
     * The default value of this property is {@link Constants#LOOPBACK_DEVICE_ADDRESS} on IPv4 stacks.
     *
     * @param address The host name or IP address.
     * @throws NullPointerException if address is {@code null}.
     */
    public final void setInsecurePortBindAddress(final String address) {
        this.insecurePortBindAddress = Objects.requireNonNull(address);
    }

    /**
     * Gets the insecure port this server is configured to listen on.
     *
     * @return The port number.
     */
    public final int getInsecurePort() {
        return insecurePort;
    }

    /**
     * Gets the insecure port this server is configured to listen on.
     *
     * @param defaultPort The port to use if this property has not been set explicitly.
     * @return The configured port number or the <em>defaultPort</em> if <em>insecurePort</em> is not set.
     * @see #getInsecurePort() for more information.
     */
    public final int getInsecurePort(final int defaultPort) {

        return insecurePort == Constants.PORT_UNCONFIGURED ? defaultPort : insecurePort;
    }

    /**
     * Sets the insecure port that this server should listen on.
     * <p>
     * If the port is set to 0 (the default value), then this server will bind to an arbitrary free port chosen by the
     * operating system during startup.
     * <p>
     * Setting this property also sets the <em>insecurePortEnabled</em> property to {@code true}.
     *
     * @param port The port number.
     * @throws IllegalArgumentException if port &lt; 0 or port &gt; 65535.
     */
    public final void setInsecurePort(final int port) {
        if (isValidPort(port)) {
            this.insecurePort = port;
            setInsecurePortEnabled(true);
        } else {
            throw new IllegalArgumentException("invalid port number");
        }
    }

    /**
     * Sets whether the server should support Server Name Indication for TLS connections.
     *
     * @param sni {@code true} if the server should support SNI.
     */
    public final void setSni(final boolean sni) {
        this.sni = sni;
    }

    /**
     * Checks if the server supports Server Name Indication for TLS connections.
     *
     * @return {@code true} if the server supports SNI.
     */
    public final boolean isSni() {
        return this.sni;
    }
}
