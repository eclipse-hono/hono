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


package org.eclipse.hono.cli;

import org.eclipse.hono.cli.util.CommandUtils;
import org.eclipse.hono.client.ServiceInvocationException;

import io.quarkus.picocli.runtime.PicocliCommandLineFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import picocli.CommandLine;

/**
 * A producer for Hono specific picocli objects.
 *
 */
@ApplicationScoped
public class CustomConfiguration {

    /**
     * Creates a command line with an exception handler that understands Hono's
     * {@link ServiceInvocationException}s.
     *
     * @param factory The factory to use for creating the command line.
     * @return The command line.
     */
    @Produces
    CommandLine customCommandLine(final PicocliCommandLineFactory factory) {
        return factory.create()
                .setCommandName("hono")
                .setExecutionExceptionHandler(CommandUtils::handleExecutionException);
    }
}
