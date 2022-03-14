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

import java.util.Map;

/**
 * A container for the result returned by Hono's credentials API.
 *
 * @param <T> denotes the concrete type of the payload that is conveyed in the result
 */
public final class CredentialsResult<T> extends RequestResponseResult<T> {

    private CredentialsResult(
            final int status,
            final T payload,
            final CacheDirective cacheDirective,
            final Map<String, Object> responseProperties) {
        super(status, payload, cacheDirective, responseProperties);
    }

    /**
     * Creates a new result for a status code.
     *
     * @param status The code indicating the outcome of processing the request.
     * @param <T> The type of the payload that is conveyed in the result.
     * @return The result.
     */
    public static <T> CredentialsResult<T> from(final int status) {
        return new CredentialsResult<>(status, null, CacheDirective.noCacheDirective(), null);
    }

    /**
     * Creates a new result for a status code and payload.
     * <p>
     * This method simply invokes {@link #from(int, Object, CacheDirective)}
     * with {@link CacheDirective#noCacheDirective()}.
     *
     * @param status The code indicating the outcome of processing the request.
     * @param payload The payload contained in the response message or {@code null}, if the response does not
     *                contain any payload data.
     * @param <T> The type of the payload that is conveyed in the result.
     * @return The result.
     */
    public static <T> CredentialsResult<T> from(final int status, final T payload) {
        return new CredentialsResult<>(status, payload, CacheDirective.noCacheDirective(), null);
    }

    /**
     * Creates a new result for a status code and payload.
     *
     * @param status The code indicating the outcome of processing the request.
     * @param payload The payload contained in the response message or {@code null}, if the response does not
     *                contain any payload data.
     * @param cacheDirective Restrictions regarding the caching of the payload by the receiver of the result
     *                       or {@code null} if no restrictions apply.
     * @param <T> The type of the payload that is conveyed in the result.
     * @return The result.
     */
    public static <T> CredentialsResult<T> from(final int status, final T payload, final CacheDirective cacheDirective) {
        return new CredentialsResult<>(status, payload, cacheDirective, null);
    }

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
     * @param <T> The type of the payload that is conveyed in the result.
     * @return The result.
     */
    public static <T> CredentialsResult<T> from(
            final int status,
            final T payload,
            final CacheDirective cacheDirective,
            final Map<String, Object> responseProperties) {
        return new CredentialsResult<>(status, payload, cacheDirective, responseProperties);
    }
}
