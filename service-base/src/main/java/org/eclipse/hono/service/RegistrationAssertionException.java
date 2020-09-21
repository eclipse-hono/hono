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


package org.eclipse.hono.service;

import org.eclipse.hono.client.ClientErrorException;

/**
 * An exception indicating that the device is either unknown
 * or disabled.
 */
public class RegistrationAssertionException extends ClientErrorException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception for a tenant, an error code and a root cause.
     *
     * @param tenant The tenant that the device belongs to or {@code null} if unknown.
     * @param errorCode The code representing the erroneous outcome.
     * @param cause The root cause.
     */
    public RegistrationAssertionException(final String tenant, final int errorCode, final Throwable cause) {
        super(tenant, errorCode, cause);
    }
}
