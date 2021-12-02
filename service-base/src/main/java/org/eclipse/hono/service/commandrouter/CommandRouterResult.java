/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.commandrouter;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.RequestResponseResult;

import io.vertx.core.json.JsonObject;

/**
 * A container for the result returned by Hono's Command Router API.
 *
 */
public final class CommandRouterResult extends RequestResponseResult<JsonObject> {

    private CommandRouterResult(
            final int status,
            final JsonObject payload,
            final CacheDirective cacheDirective,
            final ApplicationProperties applicationProperties) {
        super(status, payload, cacheDirective, applicationProperties);
    }

    /**
     * Creates a new result for a status code.
     *
     * @param status The status code.
     * @return The result.
     */
    public static CommandRouterResult from(final int status) {
        return new CommandRouterResult(status, null, null, null);
    }

    /**
     * Creates a new result for a status code and a payload.
     *
     * @param status The status code.
     * @param payload The payload to include in the result.
     * @return The result.
     */
    public static CommandRouterResult from(final int status, final JsonObject payload) {
        return new CommandRouterResult(status, payload, null, null);
    }

    /**
     * Creates a new result for a status code and a payload.
     *
     * @param status The status code.
     * @param payload The string representation of the JSON payload
     *                to include in the result (may be {@code null}).
     * @return The result.
     * @throws io.vertx.core.json.DecodeException if the given payload is not valid JSON.
     */
    public static CommandRouterResult from(final int status, final String payload) {
        if (payload != null) {
            return new CommandRouterResult(status, new JsonObject(payload), null, null);
        } else {
            return new CommandRouterResult(status, null, null, null);
        }
    }

    /**
     * Creates a new result for a status code and a payload.
     *
     * @param status The status code.
     * @param payload The payload to include in the result.
     * @param cacheDirective Restrictions regarding the caching of the payload.
     * @return The result.
     */
    public static CommandRouterResult from(final int status, final JsonObject payload, final CacheDirective cacheDirective) {
        return new CommandRouterResult(status, payload, cacheDirective, null);
    }

    /**
     * Creates a new result for a status code and a payload.
     *
     * @param status The status code.
     * @param payload The payload to include in the result.
     * @param cacheDirective Restrictions regarding the caching of the payload.
     * @param applicationProperties Arbitrary properties conveyed in the response message's
     *                              <em>application-properties</em>.
     * @return The result.
     */
    public static CommandRouterResult from(
            final int status,
            final JsonObject payload,
            final CacheDirective cacheDirective,
            final ApplicationProperties applicationProperties) {
        return new CommandRouterResult(status, payload, cacheDirective, applicationProperties);
    }
}
