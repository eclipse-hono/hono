/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Optional;

/**
 * A container for the result returned by a Hono API that implements the request response pattern.
 *
 * @param <T> The type of the payload contained in the result.
 */
public class RequestResponseResult<T> {

    private final int status;
    private final T payload;
    private final CacheDirective cacheDirective;
    private final Map<String, Object> responseProperties;

    /**
     * Creates a new result for a status code and payload.
     *
     * @param status The code indicating the outcome of processing the request.
     * @param payload The payload contained in the response message or {@code null}, if the response does not
     *                contain any payload data.
     * @param cacheDirective Restrictions regarding the caching of the payload by the receiver of the result
     *                       or {@code null} if no restrictions apply.
     * @param responseProperties Arbitrary additional properties conveyed in the response message or {@code null}, if
     *                           the response does not contain additional properties.
     */
    public RequestResponseResult(
            final int status,
            final T payload,
            final CacheDirective cacheDirective,
            final Map<String, Object> responseProperties) {

        this.status = status;
        this.payload = payload;
        this.cacheDirective = cacheDirective;
        this.responseProperties = Optional.ofNullable(responseProperties)
                .filter(props -> !props.isEmpty())
                .map(Map::copyOf)
                .orElse(Map.of());
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
     * Gets the cache directive specifying how the payload of this
     * response may be cached.
     *
     * @return The directive or {@code null} if not set.
     */
    public final CacheDirective getCacheDirective() {
        return cacheDirective;
    }

    /**
     * Gets read-only access to the response message's additional properties.
     *
     * @return An unmodifiable view on the (potentially empty) properties.
     */
    public final Map<String, Object> getResponseProperties() {
        return responseProperties;
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
     * Checks if this result's status is <em>Not Found</em>.
     *
     * @return {@code true} if status == 404.
     */
    public final boolean isNotFound() {
        return HttpURLConnection.HTTP_NOT_FOUND == status;
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
