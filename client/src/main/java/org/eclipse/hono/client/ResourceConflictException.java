/**
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client;

import java.net.HttpURLConnection;

/**
 * Indicates a resource conflict that occurred as the outcome of a service invocation.
 * <p>
 * Instances will always have an error code of 409 (Conflict).
 */
public class ResourceConflictException extends ClientErrorException {

    private static final long serialVersionUID = 6746508124520882989L;

    /**
     * Creates a new exception for a detail message.
     *
     * @param msg The detail message.
     */
    public ResourceConflictException(final String msg) {
        super(null, HttpURLConnection.HTTP_CONFLICT, msg);
    }

    /**
     * Creates a new exception for a detail message.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param msg The detail message.
     */
    public ResourceConflictException(final String tenant, final String msg) {
        super(tenant, HttpURLConnection.HTTP_CONFLICT, msg);
    }

    /**
     * Creates a new exception for a root cause.
     *
     * @param cause The root cause.
     */
    public ResourceConflictException(final Throwable cause) {
        super(null, HttpURLConnection.HTTP_CONFLICT, cause);
    }

    /**
     * Creates a new exception for a root cause.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param cause The root cause.
     */
    public ResourceConflictException(final String tenant, final Throwable cause) {
        super(tenant, HttpURLConnection.HTTP_CONFLICT, cause);
    }

    /**
     * Creates a new exception for a detail message and a root cause.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param msg The detail message.
     * @param cause The root cause.
     */
    public ResourceConflictException(final String tenant, final String msg, final Throwable cause) {
        super(tenant, HttpURLConnection.HTTP_CONFLICT, msg, cause);
    }
}
