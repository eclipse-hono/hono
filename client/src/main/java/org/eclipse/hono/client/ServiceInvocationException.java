/**
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.client;


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
        super(msg, cause);
        if (errorCode < 400 || errorCode >= 600) {
            throw new IllegalArgumentException("status code must be >= 400 and < 600");
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

}
