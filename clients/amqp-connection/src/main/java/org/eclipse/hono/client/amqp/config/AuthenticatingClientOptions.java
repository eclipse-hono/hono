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

package org.eclipse.hono.client.amqp.config;

import java.util.Optional;

import org.eclipse.hono.config.GenericOptions;
import org.eclipse.hono.util.Constants;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Options for configuring clients that need to authenticate to a remote server.
 *
 */
public interface AuthenticatingClientOptions {

    /**
     * Gets the generic options.
     *
     * @return The options.
     */
    @WithParentName
    GenericOptions genericOptions();

    /**
     * Gets the name or literal IP address of the host that the client is configured to connect to.
     *
     * @return The host name.
     */
    Optional<String> host();

    /**
     * Gets the TCP port of the server that this client is configured to connect to.
     * <p>
     * The default value of this property is {@value Constants#PORT_AMQPS_STRING}.
     *
     * @return The port number.
     */
    @WithDefault(Constants.PORT_AMQPS_STRING)
    int port();

    /**
     * Gets the user name to be used when authenticating to the Hono server.
     *
     * @return The user name.
     */
    Optional<String> username();

    /**
     * Gets the password that is used when authenticating to the Hono server.
     *
     * @return The password or {@code null} if not set.
     */
    Optional<String> password();

    /**
     * Gets the file system path to a properties file containing the
     * credentials for authenticating to the server.
     * <p>
     * The file is expected to contain a <em>username</em> and a
     * <em>password</em> property, e.g.
     * <pre>
     * username=foo
     * password=bar
     * </pre>
     *
     * @return The path or {@code null} if not set.
     */
    Optional<String> credentialsPath();

    /**
     * Checks if the <em>host</em> property must match the distinguished or
     * any of the alternative names asserted by the server's certificate when
     * connecting using TLS.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @return {@code true} if the host name should be matched against the
     *         asserted names.
     */
    @WithDefault("true")
    boolean hostnameVerificationRequired();

    /**
     * Checks if the client should use TLS to verify the server's identity
     * and encrypt the connection.
     * <p>
     * TLS is disabled by default. Setting the <em>trustStorePath</em>
     * property enables verification of the server identity implicitly and the
     * value of this property is ignored.
     *
     * @return {@code true} if TLS should be used.
     */
    @WithDefault("false")
    boolean tlsEnabled();

    /**
     * Gets the name of the role that the server plays from the client's perspective.
     * <p>
     * The default value of this property is {@value Constants#SERVER_ROLE_UNKNOWN}.
     *
     * @return The name.
     */
    @WithDefault(Constants.SERVER_ROLE_UNKNOWN)
    String serverRole();
}
