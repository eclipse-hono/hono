/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ClientErrorException;

/**
 * An exception indicating that a client tried to perform an action that
 * it is not authorized for.
 */
public class AuthorizationException extends ClientErrorException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception for a tenant, detail message and a root cause
     * with error code 401.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param msg The detail message (may be {@code null}).
     * @param cause The root cause of the failure to establish the connection  (may be {@code null}).
     */
    public AuthorizationException(final String tenant, final String msg, final Throwable cause) {
        super(tenant, HttpURLConnection.HTTP_UNAUTHORIZED, msg, cause);
    }
}
