/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.config;

import org.eclipse.hono.util.Constants;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Common options for configuring components accepting requests/connections on a network socket.
 *
 */
@ConfigMapping(prefix = "hono.server", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface ServerOptions {

    /**
     * Gets the generic options.
     *
     * @return The options.
     */
    @WithParentName
    GenericOptions genericOptions();

    /**
     * Gets the host name or literal IP address of the network interface that this server's secure port is configured to
     * be bound to.
     *
     * @return The host name.
     */
    @WithDefault(Constants.LOOPBACK_DEVICE_ADDRESS)
    String bindAddress();

    /**
     * Gets the secure port this server is configured to listen on.
     *
     * @return The port number.
     */
    @WithDefault(Constants.PORT_UNCONFIGURED_STRING)
    int port();

    /**
     * Checks if this service has been configured to bind to the secure port during startup.
     *
     * @return {@code true} if a valid key and certificate have been configured.
     */
    @WithDefault("false")
    boolean securePortEnabled();

    /**
     * Checks if this server requires the usage of a native TLS implementation.
     * <p>
     * Native TLS implementations offer in general a better performance but may not be available on all platforms.
     * If {@code true}, the server will require the usage of a native TLS implementation. The server will not start
     * if native implementation is not available on the current system. If {@code false}, the server will try to use
     * a native TLS implementation but if no native implementation is available, it will fall back to the JVM's default
     * TLS implementation.
     *
     * @return {@code true} if the server requires native TLS implementation.
     */
    @WithDefault("false")
    boolean nativeTlsRequired();

    /**
     * Checks if this server is configured to listen on an insecure port (i.e. without TLS).
     * <p>
     * If {@code false}, it is guaranteed by the server that no opened port is insecure. If {@code true},
     * it enables the definition of an insecure port (as the only port <u>or</u> additionally to the secure port).
     *
     * @return {@code false} if the server guarantees that no opened port is insecure.
     */
    @WithDefault("false")
    boolean insecurePortEnabled();

    /**
     * Gets the host name or literal IP address of the network interface that this server's insecure port is configured
     * to be bound to.
     *
     * @return The host name.
     */
    @WithDefault(Constants.LOOPBACK_DEVICE_ADDRESS)
    String insecurePortBindAddress();

    /**
     * Gets the insecure port this server is configured to listen on.
     *
     * @return The port number.
     */
    @WithDefault(Constants.PORT_UNCONFIGURED_STRING)
    int insecurePort();

    /**
     * Checks if the server supports Server Name Indication for TLS connections.
     *
     * @return {@code true} if the server supports SNI.
     */
    @WithDefault("false")
    boolean sni();
}
