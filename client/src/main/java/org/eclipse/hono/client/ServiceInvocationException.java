/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import java.net.HttpURLConnection;
import java.util.Optional;

/**
 * Indicates an unexpected outcome of a (remote) service invocation.
 *
 */
public class ServiceInvocationException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final int errorCode;

    /**
     * Creates a new exception for an error code.
     * 
     * @param errorCode The code representing the erroneous outcome.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 600.
     */
    public ServiceInvocationException(final int errorCode) {
        this(errorCode, null, null);
    }

    /**
     * Creates a new exception for an error code and a detail message.
     * 
     * @param errorCode The code representing the erroneous outcome.
     * @param msg The detail message.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 600.
     */
    public ServiceInvocationException(final int errorCode, final String msg) {
        this(errorCode, msg, null);
    }

    /**
     * Creates a new exception for an error code and a root cause.
     * 
     * @param errorCode The code representing the erroneous outcome.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 600.
     */
    public ServiceInvocationException(final int errorCode, final Throwable cause) {
        this(errorCode, null, cause);
    }

    /**
     * Creates a new exception for an error code, a detail message and a root cause.
     * 
     * @param errorCode The code representing the erroneous outcome.
     * @param msg The detail message.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 600.
     */
    public ServiceInvocationException(final int errorCode, final String msg, final Throwable cause) {
        super(providedOrDefaultMessage(errorCode, msg), cause);
        if (errorCode < 400 || errorCode >= 600) {
            throw new IllegalArgumentException(String.format("illegal error code [%d], must be >= 400 and < 600", errorCode));
        } else {
            this.errorCode = errorCode;
        }
    }

    /**
     * Gets the code representing the erroneous outcome.
     * 
     * @return The code.
     */
    public final int getErrorCode() {
        return errorCode;
    }

    /**
     * Provide a default message if none is provided.
     * 
     * @param errorCode The error code
     * @param msg The detail message. May be {@code null}.
     * @return The provided message or the default message derived from the error code if {@code null} was provided as a
     *         message.
     */
    private static String providedOrDefaultMessage(final int errorCode, final String msg) {

        if (msg != null) {
            return msg;
        } else {
            return "Error Code: " + errorCode;
        }
    }

    /**
     * Extract the HTTP status code from an exception.
     * 
     * @param t The exception to extract the code from.
     * @return The HTTP status code, or 500 if the exception is not of type {@link ServiceInvocationException}.
     */
    public static int extractStatusCode(final Throwable t) {
        return Optional.of(t).map(cause -> {
            if (cause instanceof ServiceInvocationException) {
                return ((ServiceInvocationException) cause).getErrorCode();
            } else {
                return HttpURLConnection.HTTP_INTERNAL_ERROR;
            }
        }).orElse(HttpURLConnection.HTTP_INTERNAL_ERROR);
    }
}
