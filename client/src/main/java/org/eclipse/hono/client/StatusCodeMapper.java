/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.util.Constants;
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
     * Checks if a given status code represents a successful invocation.
     * 
     * @param statusCode The code to check.
     * @return {@code true} if the code is not {@code null} and 200 =&lt; code &lt; 300.
     */
    public static final boolean isSuccessful(final Integer statusCode) {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }

    /**
     * Creates an exception for a generic result of a service invocation.
     * 
     * @param result The result containing the status code.
     * @return The exception.
     * @throws NullPointerException if result is {@code null}.
     * @throws IllegalArgumentException if the result statusCode does not represent a valid error code (i.e. it is not &ge; 400 and &lt; 600)
     */
    public static final ServiceInvocationException from(final RequestResponseResult<?> result) {

        return StatusCodeMapper.from(result.getStatus(), null);
    }

    /**
     * Creates an exception for a registration result.
     * 
     * @param result The result containing the status code.
     * @return The exception.
     * @throws NullPointerException if result is {@code null}.
     * @throws IllegalArgumentException if the result statusCode does not represent a valid error code (i.e. it is not &ge; 400 and &lt; 600)
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
     * @throws IllegalArgumentException if the statusCode does not represent a valid error code (i.e. it is not &ge; 400 and &lt; 600)
     */
    public static final ServiceInvocationException from(final int statusCode, final String detailMessage) {

        if (200 <= statusCode && statusCode < 300) {
            throw new IllegalArgumentException("status code " + statusCode + " does not represent an error");
        } else if (400 <= statusCode && statusCode < 500) {
            switch(statusCode) {
            case HttpURLConnection.HTTP_CONFLICT:
                return new ResourceConflictException(detailMessage);
            default:
                return new ClientErrorException(statusCode, detailMessage);
            }
        } else if (500 <= statusCode && statusCode < 600) {
            return new ServerErrorException(statusCode, detailMessage);
        } else {
            throw new IllegalArgumentException(String.format("illegal error code [%d], must be >= 400 and < 600", statusCode));
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
        return from(error.getCondition(), error.getDescription());
    }

    /**
     * Creates an exception for an AMQP error condition.
     * 
     * @param condition The error condition.
     * @param description The error description or {@code null} if not available.
     * @return The exception.
     * @throws NullPointerException if error is {@code null}.
     */
    public static final ServiceInvocationException from(final Symbol condition, final String description) {

        Objects.requireNonNull(condition);

        if (AmqpError.RESOURCE_LIMIT_EXCEEDED.equals(condition)) {
            return new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, description);
        } else if (AmqpError.UNAUTHORIZED_ACCESS.equals(condition)) {
            return new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, description);
        } else if (AmqpError.INTERNAL_ERROR.equals(condition)) {
            return new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, description);
        } else if (Constants.AMQP_BAD_REQUEST.equals(condition)) {
            return new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, description);
        } else {
            return new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, description);
        }
    }
}
