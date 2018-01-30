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

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.DecodeException;

/**
 * A container for the result returned by Hono's Tenant API.
 *
 */
public final class TenantResult extends RequestResponseResult<JsonObject> {
    private TenantResult(final int status, final JsonObject payload) {
        super(status, payload);
    }

    /**
     * Creates a new result for a status code.
     *
     * @param status The status code indicating the outcome of the request.
     * @return The result.
     */
    public static TenantResult from(final int status) {
        return new TenantResult(status, null);
    }

    /**
     * Creates a new result for a status code and payload.
     *
     * @param status The status code indicating the outcome of the request.
     * @param payload The payload to convey to the sender of the request.
     * @return The result.
     */
    public static TenantResult from(final int status, final JsonObject payload) {
        return new TenantResult(status, payload);
    }

    /**
     * Creates a new result for a status code and payload represented as String.
     *
     * @param status The status code indicating the outcome of the request.
     * @param payloadString The payload to convey to the sender of the request, represented as String.
     * @return The result.
     * @throws DecodeException if the given payload is not valid JSON.
     */
    public static TenantResult from(final int status, final String payloadString) {
        if (payloadString != null) {
            return new TenantResult(status, new JsonObject(payloadString));
        } else {
            return new TenantResult(status, null);
        }
    }
}
