/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.util;

import java.net.HttpURLConnection;

/**
 * A container for the result returned by a Hono API that implements the request response pattern.
 *
 * @param <T> denotes the concrete type of the payload that is part of the result
 */
public class RequestResponseResult<T> {

    private final int status;
    private final T payload;

    /**
     * Creates a new result for a status code and payload.
     * 
     * @param status The code indicating the outcome of processing the request.
     * @param payload The payload to convey to the sender of the request.
     */
    protected RequestResponseResult(final int status, final T payload) {
        this.status = status;
        this.payload = payload;
    }

    /**
     * Gets the status code indicating the outcome of the request.
     * 
     * @return The code.
     */
    public final int getStatus() {
        return status;
    }

    /**
     * Gets the payload to convey to the sender of the request.
     * 
     * @return The payload.
     */
    public final T getPayload() {
        return payload;
    }

    /**
     * Checks if this result's status is <em>OK</em>.
     * 
     * @return {@code true} if status == 200.
     */
    public final boolean isOk() {
        return HttpURLConnection.HTTP_OK == status;
    }

    /**
     * Checks if this result's status code represents
     * an error.
     * 
     * @return {@code true} if the result contains an error code.
     */
    public final boolean isError() {
        return status >= HttpURLConnection.HTTP_BAD_REQUEST &&
                status < 600;
    }
}
