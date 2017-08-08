/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.http;

import static java.net.HttpURLConnection.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

/**
 * A collection of utility methods for processing HTTP requests.
 *
 */
public abstract class HttpEndpointUtils {

    private static final Logger LOG = LoggerFactory.getLogger(HttpEndpointUtils.class);

    private HttpEndpointUtils() {
        // prevent instantiation
    }

    /**
     * Ends a response with HTTP status code 400 (Bad Request) and an optional message.
     * <p>
     * The content type of the message will be <em>text/plain</em>.
     *
     * @param response The HTTP response to write to.
     * @param msg The message to write to the response's body (may be {@code null}).
     * @throws NullPointerException if response is {@code null}.
     */
    public static void badRequest(final HttpServerResponse response, final String msg) {
        badRequest(response, msg, null);
    }

    /**
     * Ends a response with HTTP status code 400 (Bad Request) and an optional message.
     *
     * @param response The HTTP response to write to.
     * @param msg The message to write to the response's body (may be {@code null}).
     * @param contentType The content type of the message (if {@code null}, then <em>text/plain</em> is used}.
     * @throws NullPointerException if response is {@code null}.
     */
    public static void badRequest(final HttpServerResponse response, final String msg, final String contentType) {
        LOG.debug("Bad request: {}", msg);
        endWithStatus(response, HTTP_BAD_REQUEST, null, msg, contentType);
    }

    /**
     * Ends a response with HTTP status code 500 (Internal Error) and an optional message.
     * <p>
     * The content type of the message will be <em>text/plain</em>.
     *
     * @param response The HTTP response to write to.
     * @param msg The message to write to the response's body (may be {@code null}).
     * @throws NullPointerException if response is {@code null}.
     */
    public static void internalServerError(final HttpServerResponse response, final String msg) {
        LOG.debug("Internal server error: {}", msg);
        endWithStatus(response, HTTP_INTERNAL_ERROR, null, msg, null);
    }

    /**
     * Ends a response with HTTP status code 503 (Service Unavailable) and sets the <em>Retry-After</em> HTTP header to
     * a given number of seconds.
     *
     * @param response The HTTP response to write to.
     * @param retryAfterSeconds The number of seconds to set in the header.
     */
    public static void serviceUnavailable(final HttpServerResponse response, final int retryAfterSeconds) {
        serviceUnavailable(response, retryAfterSeconds, null, null);
    }

    /**
     * Ends a response with HTTP status code 503 (Service Unavailable) and sets the <em>Retry-After</em> HTTP header to
     * a given number of seconds.
     *
     * @param response The HTTP response to write to.
     * @param retryAfterSeconds The number of seconds to set in the header.
     * @param detail The message to write to the response's body (may be {@code null}).
     * @param contentType The content type of the message (if {@code null}, then <em>text/plain</em> is used}.
     * @throws NullPointerException if response is {@code null}.
     */
    public static void serviceUnavailable(final HttpServerResponse response, final int retryAfterSeconds,
                                             final String detail, final String contentType) {

        LOG.debug("Service unavailable: {}", detail);
        Map<CharSequence, CharSequence> headers = new HashMap<>(2);
        headers.put(HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "text/plain");
        headers.put(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        endWithStatus(response, HTTP_UNAVAILABLE, headers, detail, contentType);
    }

    /**
     * Ends a response with a given HTTP status code and detail message.
     *
     * @param response The HTTP response to write to.
     * @param status The status code to write to the response.
     * @param headers HTTP headers to set on the response (may be {@code null}).
     * @param detail The message to write to the response's body (may be {@code null}).
     * @param contentType The content type of the message (if {@code null}, then <em>text/plain</em> is used}.
     * @throws NullPointerException if response is {@code null}.
     */
    public static void endWithStatus(final HttpServerResponse response, final int status,
                                        Map<CharSequence, CharSequence> headers, final String detail, final String contentType) {

        Objects.requireNonNull(response);
        response.setStatusCode(status);
        if (headers != null) {
            for (Map.Entry<CharSequence, CharSequence> header : headers.entrySet()) {
                response.putHeader(header.getKey(), header.getValue());
            }
        }
        if (detail != null) {
            if (contentType != null) {
                response.putHeader(HttpHeaders.CONTENT_TYPE, contentType);
            } else {
                response.putHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
            }
            response.end(detail);
        } else {
            response.end();
        }
    }

    /**
     * Gets the value of the <em>Content-Type</em> HTTP header for a request.
     *
     * @param ctx The routing context containing the HTTP request.
     * @return The content type or {@code null} if the request doesn't contain a <em>Content-Type</em> header.
     * @throws NullPointerException if context is {@code null}.
     */
    public static String getContentType(final RoutingContext ctx) {

        return Objects.requireNonNull(ctx).request().getHeader(HttpHeaders.CONTENT_TYPE);
    }
}
