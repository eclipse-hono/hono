/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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
        super(HttpURLConnection.HTTP_CONFLICT, msg);
    }

    /**
     * Creates a new exception for a root cause.
     * 
     * @param cause The root cause.
     */
    public ResourceConflictException(final Throwable cause) {
        super(HttpURLConnection.HTTP_CONFLICT, cause);
    }

    /**
     * Creates a new exception for a detail message and a root cause.
     * 
     * @param msg The detail message.
     * @param cause The root cause.
     */
    public ResourceConflictException(final String msg, final Throwable cause) {
        super(HttpURLConnection.HTTP_CONFLICT, msg, cause);
    }
}
