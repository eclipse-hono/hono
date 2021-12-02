/**
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.http;

import java.util.Optional;

import org.eclipse.hono.client.ServiceInvocationException;

import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

/**
 * Utility methods for implementing Hono specific {@link io.vertx.ext.web.handler.AuthenticationHandler}s.
 *
 */
public final class AuthHandlerTools {

    private AuthHandlerTools() {
        // prevent instantiation
    }

    /**
     * Processes an exception that occurred while trying to authenticate
     * a device.
     * <p>
     * This method checks if the given exception is an {@code HttpException}
     * and if so, tries to extract the root cause of the problem from its
     * <em>cause</em> field. If the root cause is a {@link ServiceInvocationException}
     * the routing context is failed with that exception (provided the status code
     * isn't 302, in which case the context is ended with a <em>Location</em> header).
     * <p>
     * In all other cases, the routing context is failed with the given exception.
     * <p>
     * Note that the routing context is failed with just the exception, no status
     * code. Setting the status code on the response corresponding to the exception
     * is to be done in the failure handler, e.g. as implemented in the
     * {@link org.eclipse.hono.service.http.DefaultFailureHandler}.
     *
     * @param ctx The routing context.
     * @param exception The cause of failure to process the request.
     * @param authenticateHeader The value to return in the HTTP Authenticate header.
     * @see io.vertx.ext.web.handler.impl.AuthenticationHandlerImpl
     */
    public static void processException(
            final RoutingContext ctx,
            final Throwable exception,
            final String authenticateHeader) {

        if (exception instanceof HttpException) {

            final Throwable failure = Optional.ofNullable(exception.getCause()).map(c -> {
                if (c instanceof ServiceInvocationException) {
                    // extract and use root cause
                    return c;
                } else {
                    return exception;
                }
            }).orElse(exception);

            final int statusCode;
            final String payload;

            if (failure instanceof ServiceInvocationException) {
                final ServiceInvocationException sie = (ServiceInvocationException) exception.getCause();
                statusCode = sie.getErrorCode();
                payload = null;
            } else {
                statusCode = ((HttpException) exception).getStatusCode();
                payload = ((HttpException) exception).getPayload();
            }

            switch (statusCode) {
            case 302:
                ctx.response()
                        .putHeader(HttpHeaders.LOCATION, payload)
                        .setStatusCode(302)
                        .end("Redirecting to " + payload + ".");
                return;
            case 401:
                if (authenticateHeader != null) {
                    ctx.response()
                            .putHeader("WWW-Authenticate", authenticateHeader);
                }
                // fall through
            default:
                // rely on DefaultFailureHandler to extract/apply the status code
                ctx.fail(failure);
            }

        } else {
            ctx.fail(exception);
        }

    }

}
