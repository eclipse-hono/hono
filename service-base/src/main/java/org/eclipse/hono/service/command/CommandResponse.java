/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.command;

import io.vertx.core.buffer.Buffer;

/**
 * A wrapper around payload that has been sent by a device in
 * response to a command.
 *
 */
public final class CommandResponse {

    private final Buffer payload;
    private final int status;
    private final String replyTo;
    private final String correlationId;

    private CommandResponse(final Buffer payload, final int status, final String correlationId, final String replyTo) {
        this.payload = payload;
        this.status = status;
        this.replyTo = replyTo;
        this.correlationId = correlationId;
    }

    /**
     * Creates a response for a request ID.
     * 
     * @param requestId The request ID of the command that this is the response for.
     * @param payload The payload of the response.
     * @param status The HTTP status code indicating the outcome of the command.
     * @return The response or {@code null} if the request ID could not be parsed, the status is {@code null} or if the
     *         status code is &lt; 200 or &gt;= 600.
     */
    public static CommandResponse from(final String requestId, final Buffer payload, final Integer status) {

        if (requestId == null) {
            return null;
        } else if (status == null) {
            return null;
        } else if (status < 200 || status >= 600) {
            return null;
        } else if (requestId.length() < 2) {
            return null;
        } else {
            try {
                final int lengthStringOne = Integer.parseInt(requestId.substring(0, 2), 16);
                return new CommandResponse(
                        payload,
                        status,
                        requestId.substring(2, 2 + lengthStringOne), // correlation ID
                        requestId.substring(2 + lengthStringOne)); // reply-to ID
            } catch (NumberFormatException | StringIndexOutOfBoundsException se) {
                return null;
            }
        }
    }

    /**
     * Gets the reply-to identifier that has bee extracted from the request ID.
     * 
     * @return The identifier or {@code null} if the request ID could not be parsed.
     */
    public String getReplyToId() {
        return replyTo;
    }

    /**
     * Gets the correlation identifier that has bee extracted from the request ID.
     * 
     * @return The identifier or {@code null} if the request ID could not be parsed.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Gets the payload of the response message.
     * 
     * @return The payload or {@code null} if the response is empty.
     */
    public Buffer getPayload() {
        return payload;
    }

    /**
     * Gets the HTTP status code that indicates the outcome of
     * executing the command.
     * 
     * @return The status code.
     */
    public int getStatus() {
        return status;
    }
}
