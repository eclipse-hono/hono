/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.client.amqp.connection;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ResourceLimitExceededException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;

/**
 * Utility methods for converting AMQP 1.0 error conditions to
 * {@link org.eclipse.hono.client.ServiceInvocationException}s.
 *
 */
public final class ErrorConverter {

    private ErrorConverter() {
        // prevent instantiation
    }

    /**
     * Creates an exception for an AMQP error condition that occurred during a message transfer.
     *
     * @param error The error condition.
     * @return The exception.
     * @throws NullPointerException if error is {@code null}.
     */
    public static ServiceInvocationException fromTransferError(final ErrorCondition error) {

        Objects.requireNonNull(error);
        return fromTransferError(error.getCondition(), error.getDescription());
    }

    /**
     * Creates an exception for an AMQP error condition that occurred during a message transfer.
     *
     * @param condition The error condition.
     * @param description The error description or {@code null} if not available.
     * @return The exception.
     * @throws NullPointerException if error is {@code null}.
     */
    public static ServiceInvocationException fromTransferError(final Symbol condition, final String description) {

        Objects.requireNonNull(condition);

        if (AmqpError.RESOURCE_LIMIT_EXCEEDED.equals(condition)) {
            return new ResourceLimitExceededException(description);
        } else if (AmqpError.UNAUTHORIZED_ACCESS.equals(condition)) {
            return new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, description);
        } else if (AmqpError.INTERNAL_ERROR.equals(condition)) {
            return new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, description);
        } else {
            return new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, description);
        }
    }

    /**
     * Creates an exception for an AMQP error condition that occurred when attaching a link.
     *
     * @param error The error condition.
     * @return The exception.
     * @throws NullPointerException if error is {@code null}.
     */
    public static ServiceInvocationException fromAttachError(final ErrorCondition error) {

        Objects.requireNonNull(error);
        return fromAttachError(error.getCondition(), error.getDescription());
    }

    /**
     * Creates an exception for an AMQP error condition that occurred when attaching a link.
     *
     * @param condition The error condition.
     * @param description The error description or {@code null} if not available.
     * @return The exception.
     * @throws NullPointerException if error is {@code null}.
     */
    public static ServiceInvocationException fromAttachError(final Symbol condition, final String description) {

        Objects.requireNonNull(condition);

        if (AmqpError.RESOURCE_LIMIT_EXCEEDED.equals(condition)) {
            return new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, description);
        } else if (AmqpError.UNAUTHORIZED_ACCESS.equals(condition)) {
            return new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, description);
        } else if (AmqpError.INTERNAL_ERROR.equals(condition)) {
            return new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, description);
        } else if (AmqpUtils.AMQP_BAD_REQUEST.equals(condition)) {
            return new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, description);
        } else {
            return new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, description);
        }
    }
}
