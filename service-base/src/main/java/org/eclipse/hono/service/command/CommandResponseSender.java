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
package org.eclipse.hono.service.command;

import java.util.Map;

import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServiceInvocationException;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;

/**
 * A sender to send back the response message of a command.
 */
public interface CommandResponseSender extends MessageSender {

    /**
     * Sends a response message to a command back to the business application.
     *
     * @param correlationId The correlation id of the command.
     * @param contentType The content type describing the response message's payload (may be {@code null}).
     * @param payload The payload or {@code null}.
     * @param properties The properties or {@code null}.
     * @param status The status of the command, which was send to the device.
     * @param context The currently active OpenTracing span or {@code null} if no
     *         span is currently active. An implementation should use this as the
     *         parent for any new span(s) it creates for tracing the execution of
     *         this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been accepted (and settled)
     *         by the application.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} if the
     *         message could not be sent or has not been accepted by the application.
     * @throws NullPointerException if any of tenantId, deviceId, replyId or correlationId is {@code null}.
     */
    Future<ProtonDelivery> sendCommandResponse(
            String correlationId,
            String contentType,
            Buffer payload,
            Map<String, Object> properties,
            int status,
            SpanContext context);

    /**
     * Sends a response message to a command back to the business application.
     *
     * @param response The response.
     * @param context The currently active OpenTracing span or {@code null} if no
     *         span is currently active. An implementation should use this as the
     *         parent for any new span(s) it creates for tracing the execution of
     *         this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been accepted (and settled)
     *         by the application.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} if the
     *         message could not be sent or has not been accepted by the application.
     * @throws NullPointerException if response is {@code null}.
     */
    Future<ProtonDelivery> sendCommandResponse(CommandResponse response, SpanContext context);
}
