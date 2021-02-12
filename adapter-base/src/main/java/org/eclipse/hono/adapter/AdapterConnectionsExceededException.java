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

/**
 * An exception indicating that a device's attempt to establish a connection
 * with a protocol adapter has failed because the adapter's connection limit
 * has been exceeded.
 */
public class AdapterConnectionsExceededException extends AuthorizationException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception for a tenant, a detail message and a root cause.
     *
     * @param tenant The tenant that the device belongs to or {@code null} if unknown.
     * @param msg The detail message or {@code null}.
     * @param cause The root cause or {@code null}.
     */
    public AdapterConnectionsExceededException(final String tenant, final String msg, final Throwable cause) {
        super(tenant, msg, cause);
    }
}
