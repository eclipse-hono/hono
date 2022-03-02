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

package org.eclipse.hono.client;

import java.net.HttpURLConnection;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Indicates an unexpected outcome of a (remote) service invocation.
 *
 */
public abstract class ServiceInvocationException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(ServiceInvocationException.class);

    private static final ResourceBundle resourceBundle;
    private static final String BUNDLE_NAME = ServiceInvocationException.class.getName() + "_messages";
    static {
        resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME, Locale.getDefault());
    }

    private final int errorCode;
    private final String tenant;

    /**
     * Creates a new exception for an error code.
     *
     * @param errorCode The code representing the erroneous outcome.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 600.
     */
    protected ServiceInvocationException(final int errorCode) {
        this(null, errorCode, null, null);
    }

    /**
     * Creates a new exception for an error code and a detail message.
     *
     * @param errorCode The code representing the erroneous outcome.
     * @param msg The detail message.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 600.
     */
    protected ServiceInvocationException(final int errorCode, final String msg) {
        this(null, errorCode, msg, null);
    }

    /**
     * Creates a new exception for an error code and a root cause.
     *
     * @param errorCode The code representing the erroneous outcome.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 600.
     */
    protected ServiceInvocationException(final int errorCode, final Throwable cause) {
        this(null, errorCode, null, cause);
    }

    /**
     * Creates a new exception for a tenant, an error code, a detail message and a root cause.
     *
     * @param tenant The tenant that the exception occurred in the scope of or {@code null} if unknown.
     * @param errorCode The code representing the erroneous outcome.
     * @param msg The detail message.
     * @param cause The root cause.
     * @throws IllegalArgumentException if the code is not &ge; 400 and &lt; 600.
     */
    protected ServiceInvocationException(final String tenant, final int errorCode, final String msg, final Throwable cause) {
        super(providedOrDefaultMessage(errorCode, msg), cause);
        if (errorCode < 400 || errorCode >= 600) {
            throw new IllegalArgumentException(String.format("illegal error code [%d], must be >= 400 and < 600", errorCode));
        } else {
            this.errorCode = errorCode;
        }
        this.tenant = tenant;
    }

    /**
     * Gets the code representing the erroneous outcome.
     *
     * @return The code.
     */
    public final int getErrorCode() {
        return errorCode;
    }

    /**
     * Gets the tenant that the exception occurred in the scope of.
     *
     * @return The tenant or {@code null} if unknown.
     */
    public final String getTenant() {
        return tenant;
    }

    /**
     * Provide a default message if none is provided.
     *
     * @param errorCode The error code
     * @param msg The detail message. May be {@code null}.
     * @return The provided message or the default message derived from the error code if {@code null} was provided as a
     *         message.
     */
    private static String providedOrDefaultMessage(final int errorCode, final String msg) {

        if (msg != null) {
            return msg;
        } else {
            return "Error Code: " + errorCode;
        }
    }

    /**
     * Extract the HTTP status code from an exception.
     *
     * @param t The exception to extract the code from.
     * @return The HTTP status code. Otherwise {@link HttpURLConnection#HTTP_INTERNAL_ERROR} 
     *         if the exception is {@code null} or not of type {@link ServiceInvocationException}.
     */
    public static int extractStatusCode(final Throwable t) {
        return Optional.ofNullable(t).map(cause -> {
            if (cause instanceof ServiceInvocationException) {
                return ((ServiceInvocationException) cause).getErrorCode();
            } else {
                return HttpURLConnection.HTTP_INTERNAL_ERROR;
            }
        }).orElse(HttpURLConnection.HTTP_INTERNAL_ERROR);
    }

    /**
     * Gets the localized error message with the given key.
     *
     * @param key The error message key.
     * @return The error message or the given key if no message was found.
     */
    public static String getLocalizedMessage(final String key) {
        try {
            return resourceBundle.getString(key);
        } catch (final MissingResourceException e) {
            LOG.debug("resource not found: {}", key);
            return key;
        }
    }

    /**
     * Gets the error message suitable to be propagated to an external client.
     *
     * @param t The exception to extract the message from, may be {@code null}.
     * @return The message or {@code null} if not set.
     */
    public static String getErrorMessageForExternalClient(final Throwable t) {
        if (t instanceof ServerErrorException) {
            final ServerErrorException serverError = (ServerErrorException) t;
            final String clientFacingMessage = serverError.getClientFacingMessage();
            if (clientFacingMessage != null) {
                return clientFacingMessage;
            }
            switch (serverError.getErrorCode()) {
            case HttpURLConnection.HTTP_INTERNAL_ERROR:
                return "Internal server error";
            case HttpURLConnection.HTTP_UNAVAILABLE:
                return "Temporarily unavailable";
            default:
                // fall through
            }
        }
        return Optional.ofNullable(t).map(Throwable::getMessage).orElse(null);
    }
}
