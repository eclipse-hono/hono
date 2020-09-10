/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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


/**
 * Indicates a client error that occurred as the outcome of a service invocation.
 *
 */
public class ClientErrorException extends ServiceInvocationException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception for a client error code.
     *
     * @param errorCode The code representing the erroneous outcome.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 500.
     */
    public ClientErrorException(final int errorCode) {
        this(null, errorCode, null, null);
    }

    /**
     * Creates a new exception for a tenant and a client error code.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param errorCode The code representing the erroneous outcome.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 500.
     */
    public ClientErrorException(final String tenant, final int errorCode) {
        this(tenant, errorCode, null, null);
    }

    /**
     * Creates a new exception for a client error code and a detail message.
     *
     * @param errorCode The code representing the erroneous outcome.
     * @param msg The detail message.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 500.
     */
    public ClientErrorException(final int errorCode, final String msg) {
        this(null, errorCode, msg, null);
    }

    /**
     * Creates a new exception for a tenant, a client error code and a detail message.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param errorCode The code representing the erroneous outcome.
     * @param msg The detail message.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 500.
     */
    public ClientErrorException(final String tenant, final int errorCode, final String msg) {
        this(tenant, errorCode, msg, null);
    }

    /**
     * Creates a new exception for a client error code and a root cause.
     *
     * @param errorCode The code representing the erroneous outcome.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 500.
     */
    public ClientErrorException(final int errorCode, final Throwable cause) {
        this(null, errorCode, null, cause);
    }

    /**
     * Creates a new exception for a client error code and a root cause.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param errorCode The code representing the erroneous outcome.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 500.
     */
    public ClientErrorException(final String tenant, final int errorCode, final Throwable cause) {
        this(tenant, errorCode, null, cause);
    }

    /**
     * Creates a new exception for a client error code, a detail message and a root cause.
     *
     * @param errorCode The code representing the erroneous outcome.
     * @param msg The detail message.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 500.
     */
    public ClientErrorException(final int errorCode, final String msg, final Throwable cause) {
        this(null, errorCode, msg, cause);
    }

    /**
     * Creates a new exception for a tenant, a client error code, a detail message and a root cause.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param errorCode The code representing the erroneous outcome.
     * @param msg The detail message.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 500.
     */
    public ClientErrorException(final String tenant, final int errorCode, final String msg, final Throwable cause) {
        super(tenant, errorCode, msg, cause);
        if (errorCode < 400 || errorCode >= 500) {
            throw new IllegalArgumentException("client error code must be >= 400 and < 500");
        }
    }
}
