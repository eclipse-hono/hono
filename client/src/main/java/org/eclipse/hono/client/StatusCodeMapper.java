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

package org.eclipse.hono.client;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.RequestResponseResult;

/**
 * Utility class for mapping status codes to {@code ServiceInvocationException} instances.
 *
 */
public abstract class StatusCodeMapper {

    private StatusCodeMapper() {
    }

    /**
     * Creates an exception for a generic result of a service invocation.
     * 
     * @param result The result containing the status code.
     * @param <T> The type of the payload contained in the result.
     * @return The exception.
     * @throws NullPointerException if response is {@code null}.
     */
    public static final <T> ServiceInvocationException from(final RequestResponseResult<T> result) {

        return StatusCodeMapper.from(result.getStatus(), null);
    }

    /**
     * Creates an exception for a registration result.
     * 
     * @param result The result containing the status code.
     * @return The exception.
     * @throws NullPointerException if response is {@code null}.
     */
    public static final ServiceInvocationException from(final RegistrationResult result) {

        final String detailMessage = Optional.ofNullable(result.getPayload())
                .map(payload -> payload.getString(RegistrationConstants.FIELD_ERROR)).orElse(null);
        return StatusCodeMapper.from(result.getStatus(), detailMessage);
    }

    /**
     * Creates an exception for a status code and detail message.
     * 
     * @param statusCode The status code.
     * @param detailMessage The detail message.
     * @return The exception.
     * @throws NullPointerException if the response does not contain an error code.
     */
    public static final ServiceInvocationException from(final int statusCode, final String detailMessage) {

        if (200 <= statusCode && statusCode < 300) {
            throw new IllegalArgumentException("status code " + statusCode + " does not represent an error");
        } else if (400 <= statusCode && statusCode < 500) {
            return new ClientErrorException(statusCode, detailMessage);
        } else if (500 <= statusCode && statusCode < 600) {
            return new ServerErrorException(statusCode, detailMessage);
        } else {
            return new ServiceInvocationException(statusCode, detailMessage);
        }
    }

    /**
     * Creates an exception for an AMQP error condition.
     * 
     * @param error The error condition.
     * @return The exception.
     * @throws NullPointerException if error is {@code null}.
     */
    public static final ServiceInvocationException from(final ErrorCondition error) {

        Objects.requireNonNull(error);

        if (AmqpError.RESOURCE_LIMIT_EXCEEDED.equals(error.getCondition())) {
            return new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, error.getDescription());
        } else if (AmqpError.UNAUTHORIZED_ACCESS.equals(error.getCondition())) {
            return new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, error.getDescription());
        } else {
            return new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, error.getDescription());
        }
    }
}
