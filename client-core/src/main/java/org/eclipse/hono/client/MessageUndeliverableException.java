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
 * A {@link ClientErrorException} indicating that a request message was not processed
 * and that the message should not be redelivered.
 */
public class MessageUndeliverableException extends ClientErrorException {

    /**
     * Resource key for the error message.
     */
    public static final String MESSAGE_KEY = "CLIENT_ERROR_MESSAGE_UNDELIVERABLE";

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new MessageUndeliverableException.
     * <p>
     * The exception will have a <em>404: Not found</em> status code
     * and its error message will be set to the localized message
     * with the key defined in {@link #MESSAGE_KEY}.
     */
    public MessageUndeliverableException() {
        super(HttpURLConnection.HTTP_NOT_FOUND, getLocalizedMessage(MESSAGE_KEY));
    }
}
