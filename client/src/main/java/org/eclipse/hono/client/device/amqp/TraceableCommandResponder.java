/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.util.Map;

import org.eclipse.hono.client.ServiceInvocationException;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.proton.ProtonDelivery;

/**
 * A sender to send back the response message of a command to Hono's AMQP adapter.
 */
public interface TraceableCommandResponder extends CommandResponder {

    /**
     * Sends a response message to a command back to the business application.
     *
     * @param deviceId The device ID of the device sending the response.
     * @param targetAddress The address at which the response is expected, i.e. replyTo property of the received command
     *            message.
     * @param correlationId The correlation-id property of the command message.
     * @param status The HTTP status code indicating the outcome of the command.
     * @param payload The payload of the response. May be {@code null} since it is not required.
     * @param contentType The contentType of the response. May be {@code null} since it is not required.
     * @param properties Optional application properties (may be {@code null}).
     *            <p>
     *            AMQP application properties that can be used for carrying data in the message other than the payload.
     * @param context The context to create the span in. If {@code null}, then the span is created without a parent.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been accepted (and settled) by the application.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} if the message could not be sent or
     *         has not been accepted by the application.
     *
     * @throws NullPointerException if any of deviceId, targetAddress, or correlationId is {@code null}.
     */
    Future<ProtonDelivery> sendCommandResponse(String deviceId, String targetAddress, String correlationId, int status,
            byte[] payload, String contentType, Map<String, ?> properties, SpanContext context);

}
