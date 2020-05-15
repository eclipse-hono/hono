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

package org.eclipse.hono.example.protocolgateway;

import java.util.Objects;

/**
 * This class encodes the reply-to-id and the correlation-id of a command into a single string and decodes it from a
 * command response back into the two values.
 * <p>
 * The reply-to-id as well as the correlation-id can be freely chosen by the backend application and are not restricted
 * regarding the used characters. Therefore no reserved delimiters can be used here, instead fixed positions are used.
 * The first two characters encode the length of the correlation-id, then the correlation-id is appended and at the end
 * the reply-to-id.
 * <p>
 * The maximal expected length is 255 characters.
 */
public class RequestId {

    /**
     * The maximal length of the correlation-id.
     */
    public static final int MAX_LENGTH_CORRELATION_ID_HEX = 2;

    private final String replyId;
    private final String correlationId;

    private RequestId(final String replyId, final String correlationId) {
        this.replyId = replyId;
        this.correlationId = correlationId;
    }

    /**
     * Gets the reply-id that has been decoded from a request-id.
     *
     * @return The reply-id.
     */
    public String getReplyId() {
        return replyId;
    }

    /**
     * Gets the correlation-id that has been decoded from a request-id.
     * 
     * @return The correlation-id.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Decodes the given request-id back into separate reply-id and correlation-id.
     * 
     * @param requestId The encoded request-id.
     * @return The object containing reply-id and correlation-id.
     * @throws IllegalArgumentException if parsing or decoding fails.
     */
    public static RequestId decode(final String requestId) {

        try {
            final int correlationIdEnd = Integer.parseInt(requestId.substring(0, MAX_LENGTH_CORRELATION_ID_HEX), 16)
                    + MAX_LENGTH_CORRELATION_ID_HEX;
            final String correlationId = requestId.substring(MAX_LENGTH_CORRELATION_ID_HEX, correlationIdEnd);
            final String replyId = requestId.substring(correlationIdEnd);

            return new RequestId(replyId, correlationId);
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("Failed to decode request-id [" + requestId + "]", e);
        }
    }

    /**
     * Combines the reply-id (which is part of the reply-to address) with the correlation-id into a single string.
     * 
     * @param replyTo the reply-to of the command message.
     * @param correlationId the correlation-id of the command message.
     * @return the encoded request-id.
     * @throws NullPointerException if any of the params is {@code null}.
     * @throws IllegalArgumentException if the parameters do not comply to Hono's Command and Control API or if the
     *             correlation-id is longer than 255 chars.
     * @see #decode(String)
     */
    public static String encode(final String replyTo, final Object correlationId) {
        Objects.requireNonNull(replyTo);
        Objects.requireNonNull(correlationId);

        if (!(correlationId instanceof String)) {
            throw new IllegalArgumentException("correlation-id must be a string");
        } else {
            final String correlationIdString = ((String) correlationId);
            if (correlationIdString.length() > 255) {
                throw new IllegalArgumentException("correlationId is too long");
            }

            final String[] replyToElements = replyTo.split("\\/");
            if (replyToElements.length <= 3) {
                throw new IllegalArgumentException("reply-to address is malformed");
            } else {
                return String.format("%02x%s%s", correlationIdString.length(), correlationIdString, replyToElements[3]);
            }
        }
    }

}
