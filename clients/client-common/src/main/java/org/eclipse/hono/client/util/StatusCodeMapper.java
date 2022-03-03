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

package org.eclipse.hono.client.util;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ResourceConflictException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
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
     * @throws IllegalArgumentException if the result statusCode does not represent a valid error code (i.e. it is
     *                                  not &ge; 400 and &lt; 600)
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
     * @throws IllegalArgumentException if the result statusCode does not represent a valid error code (i.e. it is
     *                                  not &ge; 400 and &lt; 600)
     */
    public static final ServiceInvocationException from(final RegistrationResult result) {

        final String detailMessage = Optional.ofNullable(result.getPayload())
                .map(payload -> payload.getString(RegistrationConstants.FIELD_ERROR)).orElse(null);
        return StatusCodeMapper.from(result.getStatus(), detailMessage);
    }

    /**
     * Creates an exception for a status code and detail message.
     *
     * @param statusCode The status code to map.
     * @param detailMessage The detail message or {@code null} if no details are known.
     * @return The exception.
     * @throws IllegalArgumentException if the statusCode does not represent a valid error code (i.e. it is
     *         not &ge; 400 and &lt; 600)
     */
    public static final ServiceInvocationException from(final int statusCode, final String detailMessage) {

        return from(null, statusCode, detailMessage, null);
    }

    /**
     * Creates an exception for a status code and detail message.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param statusCode The status code to map.
     * @param detailMessage The detail message or {@code null} if no details are known.
     * @return The exception.
     * @throws IllegalArgumentException if the statusCode does not represent a valid error code (i.e. it is
     *                                  not &ge; 400 and &lt; 600)
     */
    public static final ServiceInvocationException from(
            final String tenant,
            final int statusCode,
            final String detailMessage) {

        return from(tenant, statusCode, detailMessage, null);
    }

    /**
     * Creates a new exception for a tenant, an error code, a detail message and a root cause.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param statusCode The status code to map.
     * @param detailMessage The detail message or {@code null} if no details are known.
     * @param cause The root cause.
     * @return The new exception.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 600.
     */
    public static final ServiceInvocationException from(
            final String tenant,
            final int statusCode,
            final String detailMessage,
            final Throwable cause) {

        if (statusCode >= 400 && statusCode < 500) {
            switch (statusCode) {
            case HttpURLConnection.HTTP_CONFLICT:
                return new ResourceConflictException(tenant, detailMessage, cause);
            default:
                return new ClientErrorException(tenant, statusCode, detailMessage, cause);
            }
        } else if (statusCode >= 500 && statusCode < 600) {
            return new ServerErrorException(tenant, statusCode, detailMessage, cause);
        } else {
            throw new IllegalArgumentException(String.format("illegal error code [%d], must be >= 400 and < 600", statusCode));
        }
    }

    /**
     * Maps the given exception to a {@link ServiceInvocationException} with a server error code.
     * <p>
     * A client error is mapped to a <em>503: Service unavailable</em> error. If the exception
     * already represents a server error, the exception itself is returned.
     * <p>
     * All other kinds of errors are mapped to a <em>500: Internal server error</em>.
     *
     * @param throwable The exception to map.
     * @return The mapped exception.
     * @throws NullPointerException if throwable is {@code null}.
     */
    public static final ServiceInvocationException toServerError(final Throwable throwable) {
        Objects.requireNonNull(throwable);
        if (throwable instanceof ServiceInvocationException) {
            final int errorCode = ((ServiceInvocationException) throwable).getErrorCode();
            if (errorCode >= 400 && errorCode < 500) {
                // a client error gets mapped to "service unavailable"
                return new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, throwable);
            }
            return (ServiceInvocationException) throwable;
        }
        return new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, throwable);
    }
}
