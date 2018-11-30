/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.http;

import java.util.Optional;

import org.eclipse.hono.client.ServiceInvocationException;

import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.impl.HttpStatusException;

/**
 * Utility methods for implementing Hono specific {@link AuthHandler}s.
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
     * This method checks if the given exception is an {@code HttpStatusException}
     * and if so, tries to extract the root cause of the problem from its
     * <em>cause</em> field. If the root cause is a {@link ServiceInvocationException}
     * then its error code is used to fail the routing context, otherwise the status
     * code from the {@code HttpStatusException} is used. In all other cases, the
     * context is failed with a 500 error code.
     * 
     * @param ctx The routing context.
     * @param exception The cause of failure to process the request.
     * @param authenticateHeader The value to return in the HTTP Authenticate header.
     */
    public static void processException(
            final RoutingContext ctx,
            final Throwable exception,
            final String authenticateHeader) {


        if (exception instanceof HttpStatusException) {

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
                statusCode = ((HttpStatusException) exception).getStatusCode();
                payload = ((HttpStatusException) exception).getPayload();
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
                ctx.fail(failure);
                return;
            default:
                ctx.fail(failure);
                return;
            }
        }

        // fallback 500
        ctx.fail(exception);
    }

}
