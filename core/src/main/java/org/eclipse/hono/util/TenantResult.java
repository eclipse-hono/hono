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

package org.eclipse.hono.util;

/**
 * A container for the result returned by Hono's Tenant API.
 *
 * @param <T> The concrete type of the payload that is conveyed in the result.
 *
 */
public final class TenantResult<T> extends RequestResponseResult<T> {

    private TenantResult(final int status, final T payload, final CacheDirective cacheDirective) {
        super(status, payload, cacheDirective);
    }

    /**
     * Creates a new result for a status code.
     *
     * @param status The status code indicating the outcome of the request.
     * @param <T> The type of the payload conveyed in the result.
     * @return The result.
     */
    public static <T> TenantResult<T> from(final int status) {
        return new TenantResult<>(status, null, null);
    }

    /**
     * Creates a new result for a status code and payload.
     *
     * @param status The status code indicating the outcome of the request.
     * @param payload The payload to convey to the sender of the request.
     * @param <T> The type of the payload conveyed in the result.
     * @return The result.
     */
    public static <T> TenantResult<T> from(final int status, final T payload) {
        return new TenantResult<>(status, payload, null);
    }

    /**
     * Creates a new result for a status code and payload.
     *
     * @param status The status code indicating the outcome of the request.
     * @param payload The payload to convey to the sender of the request.
     * @param <T> The type of the payload conveyed in the result.
     * @param cacheDirective Restrictions regarding the caching of the payload.
     * @return The result.
     */
    public static <T> TenantResult<T> from(final int status, final T payload, final CacheDirective cacheDirective) {
        return new TenantResult<>(status, payload, cacheDirective);
    }
}
