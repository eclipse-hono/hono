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

package org.eclipse.hono.client.impl;

import io.vertx.core.buffer.Buffer;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.RequestResponseResult;


/**
 * A result that contains a status code and a string payload.
 *
 */
public final class SimpleRequestResponseResult extends RequestResponseResult<Buffer> {

    private SimpleRequestResponseResult(final int status, final Buffer payload, final CacheDirective directive) {
        super(status, payload, directive);
    }

    /**
     * Creates a new instance for a status code and payload.
     * 
     * @param status The status code.
     * @param payload The payload.
     * @return The instance.
     */
    public static SimpleRequestResponseResult from(final int status, final Buffer payload) {
        return new SimpleRequestResponseResult(status, payload, null);
    }

    /**
     * Creates a new instance for a status code and payload.
     * 
     * @param status The status code.
     * @param payload The payload.
     * @param cacheDirective Restrictions regarding the caching of the payload by
     *                       the receiver of the result (may be {@code null}).
     * @return The instance.
     */
    public static SimpleRequestResponseResult from(final int status, final Buffer payload, final CacheDirective cacheDirective) {
        return new SimpleRequestResponseResult(status, payload, cacheDirective);
    }
}
