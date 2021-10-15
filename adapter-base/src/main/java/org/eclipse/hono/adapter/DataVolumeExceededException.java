/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
 * with a protocol adapter has failed because the tenant's data volume limit
 * has been exceeded.
 */
public class DataVolumeExceededException extends AuthorizationException {

    /**
     * Resource key for the error message.
     */
    public static final String MESSAGE_KEY = "CLIENT_ERROR_DATA_VOLUME_EXCEEDED";

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception for a tenant and a root cause, using a default detail message.
     *
     * @param tenant The tenant that the device belongs to or {@code null} if unknown.
     * @param cause The root cause or {@code null}.
     */
    public DataVolumeExceededException(final String tenant, final Throwable cause) {
        super(tenant, getLocalizedMessage(MESSAGE_KEY), cause);
    }
}
