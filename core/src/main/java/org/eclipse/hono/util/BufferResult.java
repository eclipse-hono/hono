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

import io.vertx.core.buffer.Buffer;

/**
 * A container for a RequestResponseResult with an opaque value.
 *
 */
public final class BufferResult extends RequestResponseResult<Buffer> {

    private BufferResult(final int status, final Buffer payload) {
        super(status, payload, CacheDirective.noCacheDirective());
    }

    /**
     * Creates a new result for a status code.
     * 
     * @param status The status code.
     * @return The result.
     */
    public static BufferResult from(final int status) {
        return new BufferResult(status, null);
    }

    /**
     * Creates a new result for a status code and a payload.
     * 
     * @param status The status code.
     * @param payload The payload to include in the result.
     * @return The result.
     */
    public static BufferResult from(final int status, final Buffer payload) {
        return new BufferResult(status, payload);
    }
}
