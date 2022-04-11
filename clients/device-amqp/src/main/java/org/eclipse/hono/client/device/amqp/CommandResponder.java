/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.device.amqp;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;

/**
 * A sender to send back the response message of a command to Hono's AMQP adapter.
 */
public interface CommandResponder {
    /**
     * Sends a message in response to a command received from a business application.
     *
     * @param targetAddress The address to send to response to. This is usually the value of the command message's
     *            <em>reply-to</em> property.
     * @param correlationId The correlation-id property of the command message.
     * @param status The HTTP status code indicating the outcome of processing the command.
     * @param payload The payload to include in the response message or {@code null} if the response is empty.
     * @param contentType The media type describing the response message's content (may be {@code null}).
     * @param context The OpenTracing context to use for tracking the execution of the operation (may be {@code null}).
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been accepted (and settled) by the application.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException} if the
     *         message could not be sent or has not been accepted by the application.
     *
     * @throws NullPointerException if targetAddress or correlationId are {@code null}.
     */
    Future<ProtonDelivery> sendCommandResponse(
            String targetAddress,
            String correlationId,
            int status,
            Buffer payload,
            String contentType,
            SpanContext context);
}
