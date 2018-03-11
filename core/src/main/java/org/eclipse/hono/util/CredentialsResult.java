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

/**
 * A container for the result returned by Hono's credentials API.
 *
 * @param <T> denotes the concrete type of the payload that is conveyed in the result
 */
public final class CredentialsResult<T> extends RequestResponseResult<T> {

    private CredentialsResult(final int status, final T payload, final CacheDirective cacheDirective) {
        super(status, payload, cacheDirective);
    }

    /**
     * Creates a new result for a status code.
     * 
     * @param status The status code indicating the outcome of the request.
     * @param <T> The type of the payload that is conveyed in the result.
     * @return The result.
     */
    public static <T> CredentialsResult<T> from(final int status) {
        return new CredentialsResult<>(status, null, CacheDirective.noCacheDirective());
    }

    /**
     * Creates a new result for a status code and payload.
     * <p>
     * This method simply invokes {@link #from(int, Object, CacheDirective)}
     * with {@link CacheDirective#noCacheDirective()}.
     * 
     * @param status The status code indicating the outcome of the request.
     * @param payload The payload to convey to the sender of the request.
     * @param <T> The type of the payload that is conveyed in the result.
     * @return The result.
     */
    public static <T> CredentialsResult<T> from(final int status, final T payload) {
        return new CredentialsResult<>(status, payload, CacheDirective.noCacheDirective());
    }

    /**
     * Creates a new result for a status code and payload.
     * 
     * @param status The status code indicating the outcome of the request.
     * @param payload The payload to convey to the sender of the request.
     * @param cacheDirective Restrictions regarding the caching of the payload by
     *                       the receiver of the result (may be {@code null}).
     * @param <T> The type of the payload that is conveyed in the result.
     * @return The result.
     */
    public static <T> CredentialsResult<T> from(final int status, final T payload, final CacheDirective cacheDirective) {
        return new CredentialsResult<>(status, payload, cacheDirective);
    }
}
