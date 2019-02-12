/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;

import io.vertx.core.buffer.Buffer;

/**
 * A container for the opaque result of a request-response service invocation.
 *
 */
public final class BufferResult extends RequestResponseResult<Buffer> {

    private final String contentType;

    private BufferResult(
            final int status,
            final String contentType,
            final Buffer payload,
            final ApplicationProperties applicationProperties) {

        super(status, payload, CacheDirective.noCacheDirective(), applicationProperties);
        this.contentType = contentType;
    }

    /**
     * Creates a new result for a status code.
     * 
     * @param status The status code.
     * @return The result.
     */
    public static BufferResult from(final int status) {
        return new BufferResult(status, null, null, null);
    }

    /**
     * Creates a new result for a status code and a payload.
     * 
     * @param status The status code.
     * @param payload The payload to include in the result.
     * @return The result.
     */
    public static BufferResult from(final int status, final Buffer payload) {

        return new BufferResult(status, null, payload, null);
    }

    /**
     * Creates a new result for a status code and payload.
     * 
     * @param status The status code.
     * @param contentType A media type describing the payload or {@code null} if unknown.
     * @param payload The payload to include in the result.
     * @return The result.
     */
    public static BufferResult from(
            final int status,
            final String contentType,
            final Buffer payload) {

        return new BufferResult(status, contentType, payload, null);
    }

    /**
     * Creates a new result for a status code and payload.
     * 
     * @param status The status code.
     * @param contentType A media type describing the payload or {@code null} if unknown.
     * @param payload The payload to include in the result.
     * @param applicationProperties Arbitrary properties conveyed in the response message's
     *                              <em>application-properties</em>.
     * @return The result.
     */
    public static BufferResult from(
            final int status,
            final String contentType,
            final Buffer payload,
            final ApplicationProperties applicationProperties) {

        return new BufferResult(status, contentType, payload, applicationProperties);
    }

    /**
     * Gets the type of the payload.
     * 
     * @return The media type describing the payload or {@code null} if unknown.
     */
    public String getContentType() {
        return contentType;
    }
}
