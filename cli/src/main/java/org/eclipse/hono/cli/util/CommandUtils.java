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

import java.util.concurrent.CompletionException;

import org.eclipse.hono.client.ServiceInvocationException;

import picocli.CommandLine;
import picocli.CommandLine.ParseResult;

/**
 * Helper methods for implementing commands.
 *
 */
public final class CommandUtils {

    /**
     * Property description explaining the support for OS variable references.
     */
    public static final String DESCRIPTION_ENV_VARS = """
            This property supports references to OS environment variables like $${MY_VARIABLE}, with \
            MY_VARIABLE being the name of the OS environment variable that contains the value to use.
            """;

    private CommandUtils() {
        // prevent instantiation
    }

    /**
     * Prints error details to standard error.
     *
     * @param t Th error to print.
     */
    public static void printError(final Throwable t) {
        if (t instanceof ServiceInvocationException cause) {
            System.err.println("Error: %d - %s".formatted(
                    cause.getErrorCode(),
                    ServiceInvocationException.getErrorMessageForExternalClient(cause)));
        } else {
            System.err.println("Error: %s".formatted(t.getMessage()));
        }
    }

    /**
     * Handles an error that occurred while executing a command.
     *
     * @param ex The error.
     * @param commandLine The command's command line.
     * @param parseResult The result of parsing the command line.
     * @return Always -1.
     */
    public static int handleExecutionException(
            final Exception ex,
            final CommandLine commandLine,
            final ParseResult parseResult) {

        final Exception cause = (ex instanceof CompletionException) ? (Exception) ex.getCause() : ex;
        printError(cause);

        return 1;
    }
}
