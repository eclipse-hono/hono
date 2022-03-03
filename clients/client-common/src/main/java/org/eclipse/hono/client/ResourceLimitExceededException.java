/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.client;

import java.net.HttpURLConnection;

/**
 * A {@link ServerErrorException} indicating that a request message was not processed
 * because the client's resource limits have been exceeded.
 * <p>
 * Possible causes include the maximum capacity of a message queue being reached.
 */
public class ResourceLimitExceededException extends ServerErrorException {

    /**
     * Resource key for the error message.
     */
    public static final String MESSAGE_KEY = "SERVER_ERROR_RESOURCE_LIMIT_EXCEEDED";

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception.
     * <p>
     * The exception will have a <em>503: Unavailable</em> status code
     * and its client facing error message will be set to the localized message
     * with the key defined in {@link #MESSAGE_KEY}.
     */
    public ResourceLimitExceededException() {
        this(null);
    }

    /**
     * Creates a new exception.
     * <p>
     * The exception will have a <em>503: Unavailable</em> status code
     * and its client facing error message will be set to the localized message
     * with the key defined in {@link #MESSAGE_KEY}.
     *
     * @param message The (internal) detail message of the problem or {@code null}
     *                if no detail message is available.
     */
    public ResourceLimitExceededException(final String message) {
        super(HttpURLConnection.HTTP_UNAVAILABLE, message);
        setClientFacingMessageWithKey(MESSAGE_KEY);
    }
}
