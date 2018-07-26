/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.command;

import java.util.function.Predicate;

import io.vertx.core.buffer.Buffer;

/**
 * A wrapper around payload that has been sent by a device in
 * response to a command.
 *
 */
public final class CommandResponse {

    private static final Predicate<Integer> INVALID_STATUS_CODE = code ->
        code == null || code < 200 || (code >= 300 && code < 400) || code >= 600;

    private final Buffer payload;
    private final String contentType;
    private final int status;
    private final String replyTo;
    private final String correlationId;

    private CommandResponse(final Buffer payload, final String contentType, final int status, final String correlationId, final String replyTo) {
        this.payload = payload;
        this.contentType = contentType;
        this.status = status;
        this.replyTo = replyTo;
        this.correlationId = correlationId;
    }

    /**
     * Creates an empty response for a request ID that contains only the status.
     *
     * @param requestId The request ID of the command that this is the response for.
     * @param deviceId The device ID of the device sending the response.
     * @param status The HTTP status code indicating the outcome of the command.
     * @return The response or {@code null} if the request ID could not be parsed, the status is {@code null} or if the
     *         status code is &lt; 200 or &gt;= 600.
     */
    public static CommandResponse from(final String requestId, final String deviceId, final Integer status) {
        return from(requestId, deviceId, null, null, status);
    }

    /**
     * Creates a response for a request ID.
     * 
     * @param requestId The request ID of the command that this is the response for.
     * @param deviceId The device ID of the device sending the response.
     * @param payload The payload of the response.
     * @param contentType The contentType of the response. Maybe {@code null} since it is not required.
     * @param status The HTTP status code indicating the outcome of the command.
     * @return The response or {@code null} if the request ID could not be parsed, the status is {@code null} or if the
     *         status code is &lt; 200 or &gt;= 600.
     */
    public static CommandResponse from(
            final String requestId,
            final String deviceId,
            final Buffer payload,
            final String contentType,
            final Integer status) {

        if (requestId == null) {
            return null;
        } else if (INVALID_STATUS_CODE.test(status)) {
            return null;
        } else if (requestId.length() < 3) {
            return null;
        } else {
            try {
                final boolean addDeviceIdToReply = "1".equals(requestId.substring(0, 1));
                final int lengthStringOne = Integer.parseInt(requestId.substring(1, 3), 16);
                final String replyId = requestId.substring(3 + lengthStringOne);
                return new CommandResponse(
                        payload,
                        contentType,
                        status,
                        requestId.substring(3, 3 + lengthStringOne), // correlation ID
                        addDeviceIdToReply ? deviceId + "/" + replyId : replyId);
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
     * Gets the contentType of the response message.
     *
     * @return The contentType or {@code null} if the contentType was not set for the response.
     */
    public String getContentType() {
        return contentType;
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
