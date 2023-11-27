/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.cli.util;

import java.util.Optional;

import jakarta.inject.Singleton;
import picocli.CommandLine;

/**
 * Common options required for establishing a connection.
 *
 */
@Singleton
@CommandLine.Command()
public class ConnectionOptions {

    /**
     * The host name of Hono's Sandbox environment.
     */
    public static final String SANDBOX_HOST_NAME = "hono.eclipseprojects.io";

    @CommandLine.Option(
            names = {"--sandbox"},
            description = {
                "Connect to Hono's Sandbox environment.",
                "See https://www.eclipse.org/hono/sandbox/"
                },
            order = 1)
    public boolean useSandbox;

    @CommandLine.Option(
            names = { "-H", "--host" },
            description = { "The name or literal IP address of the host to connect to." },
            order = 2)
    public Optional<String> hostname;

    @CommandLine.Option(
            names = { "-P", "--port" },
            description = {
                "The port of the host to connect to.",
                "The concrete port number is specific to the API endpoint to connect to."},
            order = 3)
    public Optional<Integer> portNumber;

    @CommandLine.Option(
            names = { "--ca-file" },
            description = {
                "Absolute path to a file containing trusted CA certificates to enable encrypted communication.",
                "Needs to be set if connecting to an endpoint using TLS.",
                "In particular, this needs to be set when connecting to the Hono Sandbox."
                },
            order = 4)
    public Optional<String> trustStorePath;

    @CommandLine.Option(
            names = { "--ca-file-password" },
            description = {
                "The password required for reading the trusted CA certificates file."
                },
            order = 5)
    public Optional<String> trustStorePassword;

    @CommandLine.Option(
            names = { "--disable-hostname-verification" },
            defaultValue = "false",
            description = {
                """
                Disables verification of the server certificate matching the value provided in the \
                '-H=<hostName>' option.
                """,
                "This option might be needed if the host name used to connect to the server is a literal IP address"
                },
            order = 6)
    public boolean disableHostnameVerification;

    @CommandLine.ArgGroup(exclusive = false)
    public Credentials credentials;

    /**
     * A set of username/password credentials.
     */
    public static class Credentials {
        @CommandLine.Option(
                names = { "-u", "--username" },
                description = { "The user name to use for authenticating to the endpoint." },
                required = true,
                order = 7)
        public String username;

        @CommandLine.Option(
                names = { "-p", "--password" },
                        description = { "The password to use for authenticating to the endpoint." },
                required = true,
                order = 8)
        public String password;
    }
}
