/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
 * An exception indicating that the adapter is not enabled.
 */
public class AdapterDisabledException extends ClientErrorException {

    /**
     * Resource key for the error message.
     */
    public static final String MESSAGE_KEY = "CLIENT_ERROR_ADAPTER_DISABLED";

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception for a tenant using status code 403.
     *
     * @param tenant The tenant that the device belongs to or {@code null} if unknown.
     */
    public AdapterDisabledException(final String tenant) {
        super(tenant, HttpURLConnection.HTTP_FORBIDDEN, getLocalizedMessage(MESSAGE_KEY));
    }
}
