/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.client;

import java.net.HttpURLConnection;

/**
 * A {@link ServerErrorException} indicating that there is no consumer for the request message.
 */
public class NoConsumerException extends ServerErrorException {

    /**
     * Resource key for the client facing error message.
     */
    public static final String CLIENT_FACING_MESSAGE_KEY = "SERVER_ERROR_NO_CONSUMER";

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new NoConsumerException.
     * <p>
     * The exception will have a <em>503: Service unavailable</em> status code
     * and its client facing error message will be set to the localized message
     * with the key defined in {@link #CLIENT_FACING_MESSAGE_KEY}.
     *
     * @param msg The detail message.
     */
    public NoConsumerException(final String msg) {
        super(HttpURLConnection.HTTP_UNAVAILABLE, msg);
        setClientFacingMessageWithKey(CLIENT_FACING_MESSAGE_KEY);
    }
}
