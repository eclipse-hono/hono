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

package org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream;

import java.util.Objects;

import org.eclipse.hono.util.CommandConstants;

import io.vertx.core.buffer.Buffer;

/**
 * This class holds required data of a command response.
 */
public final class CommandResponseMessage extends DownstreamMessage {

    private final String replyId;
    private final String correlationId;
    private final int status;

    /**
     * Creates an instance.
     *
     * @param replyId The reply id of the command.
     * @param correlationId The correlation id (taken from the command).
     * @param status The outcome of the command as a HTTP status code.
     * @param payload The payload to be used.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if status does not represent a valid HTTP status code.
     */
    public CommandResponseMessage(final String replyId, final String correlationId, final String status,
            final Buffer payload) {
        super(payload);

        Objects.requireNonNull(replyId);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(status);

        this.replyId = replyId;
        this.correlationId = correlationId;

        final Integer statusCode = parseStatus(status);
        validateStatus(statusCode);
        this.status = statusCode;

    }

    /**
     * Gets the target address of the response.
     *
     * @param tenantId The tenant.
     * @param deviceId The device that sends the response.
     * @return The command reply address as expected by Hono's<em>Command &amp; Control API</em>.
     */
    public String getTargetAddress(final String tenantId, final String deviceId) {
        return String.format("%s/%s/%s/%s", CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, tenantId, deviceId,
                replyId);
    }

    /**
     * Gets the correlation id of the response.
     *
     * @return The correlation id.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Gets the status code indicating the outcome of the request.
     *
     * @return The code.
     */
    public int getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "command response [replyId=" + replyId +
                ", correlationId=" + correlationId +
                ", status=" + status +
                "]";
    }

    private void validateStatus(final int status) {
        if ((status < 200) || (status >= 600)) {
            throw new IllegalArgumentException("status is invalid");
        }
    }

    private Integer parseStatus(final String status) {
        try {
            return Integer.parseInt(status);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
