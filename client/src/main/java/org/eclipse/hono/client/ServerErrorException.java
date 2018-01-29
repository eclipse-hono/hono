/**
 * Copyright (c) 2017, 2018 Contributors to the Eclipse Foundation
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
 * Indicates a server error that occurred during a service invocation.
 *
 */
public class ServerErrorException extends ServiceInvocationException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception for a server error code.
     * 
     * @param errorCode The code representing the error that occurred.
     * @throws IllegalArgumentException if the code is not &ge; 500 and &lt; 600.
     */
    public ServerErrorException(final int errorCode) {
        this(errorCode, null, null);
    }

    /**
     * Creates a new exception for a server error code and a detail message.
     * 
     * @param errorCode The code representing the error that occurred.
     * @param msg The detail message.
     * @throws IllegalArgumentException if the code is not &ge; 500 and &lt; 600.
     */
    public ServerErrorException(final int errorCode, final String msg) {
        this(errorCode, msg, null);
    }

    /**
     * Creates a new exception for a server error code and a root cause.
     * 
     * @param errorCode The code representing the error that occurred.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 500 and &lt; 600.
     */
    public ServerErrorException(final int errorCode, final Throwable cause) {
        this(errorCode, null, cause);
    }

    /**
     * Creates a new exception for a server error code, a detail message and a root cause.
     * 
     * @param errorCode The code representing the error that occurred.
     * @param msg The detail message.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 500 and &lt; 600.
     */
    public ServerErrorException(final int errorCode, final String msg, final Throwable cause) {
        super(errorCode, msg, cause);
        if (errorCode < 500 || errorCode >= 600) {
            throw new IllegalArgumentException("client error code must be >= 500 and < 600");
        }
    }
}
