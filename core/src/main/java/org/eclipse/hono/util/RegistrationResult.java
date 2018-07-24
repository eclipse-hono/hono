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

package org.eclipse.hono.util;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

/**
 * A container for the result returned by Hono's registration API.
 *
 */
public final class RegistrationResult extends RequestResponseResult<JsonObject> {

    private RegistrationResult(final int status, final JsonObject payload, final CacheDirective cacheDirective) {
        super(status, payload, cacheDirective);
    }

    /**
     * Creates a new result for a status code.
     * 
     * @param status The status code.
     * @return The result.
     */
    public static RegistrationResult from(final int status) {
        return new RegistrationResult(status, null, null);
    }

    /**
     * Creates a new result for a status code and a payload.
     * 
     * @param status The status code.
     * @param payload The payload to include in the result.
     * @return The result.
     */
    public static RegistrationResult from(final int status, final JsonObject payload) {
        return new RegistrationResult(status, payload, null);
    }

    /**
     * Creates a new result for a status code and a payload.
     * 
     * @param status The status code.
     * @param payload The payload to include in the result.
     * @param cacheDirective Restrictions regarding the caching of the payload.
     * @return The result.
     */
    public static RegistrationResult from(final int status, final JsonObject payload, final CacheDirective cacheDirective) {
        return new RegistrationResult(status, payload, cacheDirective);
    }

    /**
     * Creates a new result for a status code and a payload.
     * 
     * @param status The status code.
     * @param payload The string representation of the JSON payload
     *                to include in the result (may be {@code null}).
     * @return The result.
     * @throws DecodeException if the given payload is not valid JSON.
     */
    public static RegistrationResult from(final int status, final String payload) {
        if (payload != null) {
            return new RegistrationResult(status, new JsonObject(payload), null);
        } else {
            return new RegistrationResult(status, null, null);
        }
    }
}
