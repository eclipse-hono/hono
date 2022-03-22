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

package org.eclipse.hono.client.amqp;

import java.util.Map;
import java.util.Optional;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.RequestResponseResult;

import io.vertx.core.buffer.Buffer;


/**
 * A result that contains a status code and a string payload.
 *
 */
public final class SimpleRequestResponseResult extends RequestResponseResult<Buffer> {

    private SimpleRequestResponseResult(
            final int status,
            final Buffer payload,
            final CacheDirective directive,
            final Map<String, Object> responseProperties) {
        super(status, payload, directive, responseProperties);
    }

    /**
     * Creates a new instance for a status code and payload.
     *
     * @param status The code indicating the outcome of processing the request.
     * @param payload The payload contained in the response message or {@code null}, if the response does not
     *                contain any payload data.
     * @param cacheDirective Restrictions regarding the caching of the payload by the receiver of the result
     *                       or {@code null} if no restrictions apply.
     * @param responseProperties Arbitrary additional properties conveyed in the response message or {@code null}, if
     *                           the response does not contain additional properties.
     * @return The instance.
     */
    public static SimpleRequestResponseResult from(
            final int status,
            final Buffer payload,
            final CacheDirective cacheDirective,
            final Map<String, Object> responseProperties) {
        return new SimpleRequestResponseResult(status, payload, cacheDirective, responseProperties);
    }

    /**
     * Creates a new instance for a message.
     *
     * @param message The message.
     * @return The instance.
     */
    public static SimpleRequestResponseResult from(final Message message) {
        return from(
                AmqpUtils.getStatus(message),
                AmqpUtils.getPayload(message),
                CacheDirective.from(AmqpUtils.getCacheDirective(message)),
                Optional.ofNullable(message.getApplicationProperties())
                    .map(ApplicationProperties::getValue)
                    .orElse(null));
    }

}
