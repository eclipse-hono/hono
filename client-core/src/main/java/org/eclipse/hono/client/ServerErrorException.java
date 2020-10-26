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
 * Indicates a server error that occurred during a service invocation.
 *
 */
public class ServerErrorException extends ServiceInvocationException {

    private static final long serialVersionUID = 1L;

    private String clientFacingMessage;

    /**
     * Creates a new exception for a server error code.
     *
     * @param errorCode The code representing the error that occurred.
     * @throws IllegalArgumentException if the code is not &ge; 500 and &lt; 600.
     */
    public ServerErrorException(final int errorCode) {
        this(null, errorCode, null, null);
    }

    /**
     * Creates a new exception for a tenant and a server error code.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param errorCode The code representing the error that occurred.
     * @throws IllegalArgumentException if the code is not &ge; 500 and &lt; 600.
     */
    public ServerErrorException(final String tenant, final int errorCode) {
        this(tenant, errorCode, null, null);
    }

    /**
     * Creates a new exception for a server error code and a detail message.
     *
     * @param errorCode The code representing the error that occurred.
     * @param msg The detail message.
     * @throws IllegalArgumentException if the code is not &ge; 500 and &lt; 600.
     */
    public ServerErrorException(final int errorCode, final String msg) {
        this(null, errorCode, msg, null);
    }

    /**
     * Creates a new exception for a tenant, a server error code and a detail message.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param errorCode The code representing the error that occurred.
     * @param msg The detail message.
     * @throws IllegalArgumentException if the code is not &ge; 500 and &lt; 600.
     */
    public ServerErrorException(final String tenant, final int errorCode, final String msg) {
        this(tenant, errorCode, msg, null);
    }

    /**
     * Creates a new exception for a server error code and a root cause.
     *
     * @param errorCode The code representing the error that occurred.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 500 and &lt; 600.
     */
    public ServerErrorException(final int errorCode, final Throwable cause) {
        this(null, errorCode, null, cause);
    }

    /**
     * Creates a new exception for a tenant, a server error code and a root cause.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param errorCode The code representing the error that occurred.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 500 and &lt; 600.
     */
    public ServerErrorException(final String tenant, final int errorCode, final Throwable cause) {
        this(tenant, errorCode, null, cause);
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
        this(null, errorCode, msg, cause);
    }

    /**
     * Creates a new exception for a tenant, a server error code, a detail message and a root cause.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param errorCode The code representing the error that occurred.
     * @param msg The detail message.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 500 and &lt; 600.
     */
    public ServerErrorException(final String tenant, final int errorCode, final String msg, final Throwable cause) {
        super(tenant, errorCode, msg, cause);
        if (errorCode < 500 || errorCode >= 600) {
            throw new IllegalArgumentException("client error code must be >= 500 and < 600");
        }
    }

    /**
     * Gets the error message suitable to be propagated to an external client.
     *
     * @return The message or {@code null}.
     */
    public final String getClientFacingMessage() {
        return clientFacingMessage;
    }

    /**
     * Sets the error message suitable to be propagated to an external client.
     *
     * @param clientFacingMessageKey The key to get the localized message for.
     */
    public final void setClientFacingMessageWithKey(final String clientFacingMessageKey) {
        this.clientFacingMessage = getLocalizedMessage(clientFacingMessageKey);
    }
}
