/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import picocli.CommandLine;

/**
 * Information about a client certificate.
 */
public class ClientCertInfo {
    @CommandLine.Option(
            names = { "--key" },
            description = {
                """
                The absolute path to the file containing the private key to use for authenticating to the \
                server.
                """ },
            required = true,
            order = 7)
    public String keyPath;

    @CommandLine.Option(
            names = { "--cert" },
            description = {
                """
                The absolute path to the file containing the client certificate to use for authenticating to the \
                server.
                """ },
            required = true,
            order = 8)
    public String certPath;
}
