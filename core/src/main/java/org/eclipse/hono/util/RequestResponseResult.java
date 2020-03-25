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

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;

/**
 * A container for the result returned by a Hono API that implements the request response pattern.
 *
 * @param <T> The type of the payload contained in the result.
 */
public class RequestResponseResult<T> {

    private final int status;
    private final T payload;
    private final CacheDirective cacheDirective;
    private final Map<String, Object> applicationProperties;

    /**
     * Creates a new result for a status code and payload.
     * 
     * @param status The code indicating the outcome of processing the request.
     * @param payload The payload to convey to the sender of the request (may be {@code null}).
     * @param directive Restrictions regarding the caching of the payload by
     *                       the receiver of the result (may be {@code null}).
     * @param applicationProperties Arbitrary properties conveyed in the response message's
     *                              <em>application-properties</em>.
     */
    protected RequestResponseResult(
            final int status,
            final T payload,
            final CacheDirective directive,
            final ApplicationProperties applicationProperties) {

        this.status = status;
        this.payload = payload;
        this.cacheDirective = directive;
        if (applicationProperties == null || applicationProperties.getValue().isEmpty()) {
            this.applicationProperties = Collections.emptyMap();
        } else {
            this.applicationProperties = Collections.unmodifiableMap(applicationProperties.getValue());
        }
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
     * Gets the value of a property conveyed in this response message's
     * <em>application-properties</em>.
     * 
     * @param <V> The expected value type.
     * @param key The key of the property.
     * @param type The expected value type.
     * @return The value if it is of the expected type or {@code null} otherwise.
     */
    @SuppressWarnings("unchecked")
    public final <V> V getApplicationProperty(final String key, final Class<V> type) {
        final Object value = applicationProperties.get(key);
        if (type.isInstance(value)) {
            return (V) value;
        } else {
            return null;
        }
    }

    /**
     * Gets read-only access to the response message's <em>application-properties</em>.
     * 
     * @return The unmodifiable map of the application properties. Never returns {@code null}.
     */
    public final Map<String, Object> getApplicationProperties() {
        return applicationProperties;
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
