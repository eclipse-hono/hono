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

package org.eclipse.hono.service.http;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.opentracing.Span;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * A collection of utility methods for processing HTTP requests.
 *
 */
public final class HttpUtils {

    /**
     * The <em>application/json</em> content type.
     */
    public static final String CONTENT_TYPE_JSON = "application/json";
    /**
     * The <em>application/json; charset=utf-8</em> content type.
     */
    public static final String CONTENT_TYPE_JSON_UTF8 = "application/json; charset=utf-8";
    /**
     * The <em>application/json; charset=utf-8</em> content type.
     */
    public static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    /**
     * The <em>text/plain; charset=utf-8</em> content type.
     */
    public static final String CONTENT_TYPE_TEXT_UTF8 = "text/plain; charset=utf-8";

    /**
     * 429 Too Many Requests.
     * Refer <a href="https://tools.ietf.org/html/rfc6585#section-4"> RFC 6585, Section 4 </a>.
     */
    public static final int HTTP_TOO_MANY_REQUESTS = 429;
    /**
     * Constant for the "io.vertx.web.router.setup.lenient" system property name. Setting such a property
     * to {@code true} will allow handlers to be added to routes in any order, ignoring the order restrictions
     * defined based on the handler type.
     */
    public static final String SYSTEM_PROPERTY_ROUTER_SETUP_LENIENT = "io.vertx.web.router.setup.lenient";

    private static final Logger LOG = LoggerFactory.getLogger(HttpUtils.class);

    private HttpUtils() {
        // prevent instantiation
    }

    /**
     * Fails a routing context with HTTP status code 400 (Bad Request) and an optional message.
     *
     * @param ctx The vert.x routing context to fail.
     * @param msg The message to write to the response's body (may be {@code null}).
     * @throws NullPointerException if routing context is {@code null}.
     */
    public static void badRequest(final RoutingContext ctx, final String msg) {
        failWithHeaders(
                ctx,
                new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, msg),
                null);
    }

    /**
     * Fails a routing context with HTTP status code 404 (Not Found) and an optional message.
     *
     * @param ctx The vert.x routing context to fail.
     * @param msg The message to write to the response's body (may be {@code null}).
     * @throws NullPointerException if routing context is {@code null}.
     */
    public static void notFound(final RoutingContext ctx, final String msg) {
        failWithHeaders(
                ctx,
                new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, msg),
                null);
    }

    /**
     * Fails a routing context with HTTP status code 500 (Internal Error) and an optional message.
     *
     * @param ctx The vert.x routing context to fail.
     * @param msg The message to write to the response's body (may be {@code null}).
     * @throws NullPointerException if routing context is {@code null}.
     */
    public static void internalServerError(final RoutingContext ctx, final String msg) {
        failWithHeaders(
                ctx, 
                new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, msg),
                null);
    }

    /**
     * Fails a routing context with HTTP status code 503 (Service Unavailable) and sets the <em>Retry-After</em> HTTP header
     * to a given number of seconds.
     *
     * @param ctx The vert.x routing context to fail.
     * @param retryAfterSeconds The number of seconds to set in the header.
     * @throws NullPointerException if routing context is {@code null}.
     */
    public static void serviceUnavailable(final RoutingContext ctx, final int retryAfterSeconds) {
        serviceUnavailable(ctx, retryAfterSeconds, null);
    }

    /**
     * Fails a routing context with HTTP status code 503 (Service Unavailable) and sets the <em>Retry-After</em> HTTP header
     * to a given number of seconds.
     *
     * @param ctx The vert.x routing context to fail.
     * @param retryAfterSeconds The number of seconds to set in the header.
     * @param detail The message to write to the response's body (may be {@code null}).
     * @throws NullPointerException if routing context is {@code null}.
     */
    public static void serviceUnavailable(final RoutingContext ctx, final int retryAfterSeconds, final String detail) {

        final Map<CharSequence, CharSequence> headers = new HashMap<>(1);
        headers.put(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        failWithHeaders(
                ctx,
                new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, detail),
                headers);
    }

    /**
     * Fails a routing context with HTTP status code 401 (Unauthorized) and an optional message.
     *
     * @param ctx The vert.x routing context to fail.
     * @param authenticateHeaderValue The value to send to the client in the <em>WWW-Authenticate</em> header.
     * @throws NullPointerException if routing context is {@code null}.
     */
    public static void unauthorized(final RoutingContext ctx, final String authenticateHeaderValue) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(authenticateHeaderValue);
        final Map<CharSequence, CharSequence> headers = new HashMap<>();
        headers.put("WWW-Authenticate", authenticateHeaderValue);
        failWithHeaders(
                ctx,
                new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED),
                headers);
    }

    /**
     * Fails a request with a given error.
     *
     * @param ctx The request context to fail.
     * @param error The reason for the failure.
     * @throws NullPointerException if request context or error are {@code null}.
     */
    public static void fail(
            final RoutingContext ctx,
            final ServiceInvocationException error) {

        failWithHeaders(ctx, error, null);
    }

    /**
     * Fails a request with a given error.
     * <p>
     * This method does nothing if the context is already failed
     * or if the response has already ended.
     *
     * @param ctx The request context to fail.
     * @param error The reason for the failure.
     * @param headers HTTP headers to set on the response (may be {@code null}).
     * @throws NullPointerException if request context or error are {@code null}.
     */
    public static void failWithHeaders(
            final RoutingContext ctx,
            final ServiceInvocationException error,
            final Map<CharSequence, CharSequence> headers) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(error);

        // don't check for "ctx.response().closed()" here - such a request should still be failed so that failureHandlers get invoked
        if (ctx.failed() || ctx.response().ended()) {
            // nothing to do
        } else {
            // the check for "closed" here is to prevent an IllegalStateException in "putHeader" (vert.x < 3.8)
            if (headers != null && !ctx.response().closed()) {
                for (final Entry<CharSequence, CharSequence> header : headers.entrySet()) {
                    ctx.response().putHeader(header.getKey(), header.getValue());
                }
            }
            ctx.fail(error);
        }
    }

    /**
     * Gets the value of the {@link org.eclipse.hono.util.Constants#HEADER_COMMAND_RESPONSE_STATUS} HTTP header for a request.
     * If no such header can be found, the query is searched for containing a query parameter with the same key.
     *
     * @param ctx The routing context containing the HTTP request.
     * @return An Optional containing the command response status or an empty Optional if
     * <ul>
     *     <li>the request doesn't contain a {@link org.eclipse.hono.util.Constants#HEADER_COMMAND_RESPONSE_STATUS} header or query parameter.</li>
     *     <li>the contained value cannot be parsed as an Integer</li>
     * </ul>
     * @throws NullPointerException if context is {@code null}.
     */
    public static Optional<Integer> getCommandResponseStatus(final RoutingContext ctx) {
        Objects.requireNonNull(ctx);

        try {
            return Optional.ofNullable(ctx.request().getHeader(Constants.HEADER_COMMAND_RESPONSE_STATUS))
                    .map(Integer::parseInt)
                    .or(() -> Optional.ofNullable(ctx.request().getParam(Constants.HEADER_COMMAND_RESPONSE_STATUS))
                            .map(Integer::parseInt));
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }

    }

    /**
     * Writes a JSON array to an HTTP response body.
     * <p>
     * This method also sets the <em>content-length</em> header of the HTTP response
     * and sets the <em>content-type</em> header to {@link #CONTENT_TYPE_JSON_UTF8}
     * but does not end the response.
     *
     * @param response The HTTP response.
     * @param body The JSON array to serialize to the response body (may be {@code null}).
     * @throws NullPointerException if response is {@code null}.
     */
    public static void setResponseBody(final HttpServerResponse response, final JsonArray body) {
        Objects.requireNonNull(response);
        if (body != null) {
            setResponseBody(response, body.toBuffer(), CONTENT_TYPE_JSON_UTF8);
        }
    }

    /**
     * Writes a JSON object to an HTTP response body.
     * <p>
     * This method also sets the <em>content-length</em> header of the HTTP response
     * and sets the <em>content-type</em> header to {@link #CONTENT_TYPE_JSON_UTF8}
     * but does not end the response.
     *
     * @param response The HTTP response.
     * @param body The JSON object to serialize to the response body (may be {@code null}).
     * @throws NullPointerException if response is {@code null}.
     */
    public static void setResponseBody(final HttpServerResponse response, final JsonObject body) {
        Objects.requireNonNull(response);
        if (body != null) {
            setResponseBody(response, body.toBuffer(), CONTENT_TYPE_JSON_UTF8);
        }
    }

    /**
     * Writes a Buffer to an HTTP response body.
     * <p>
     * This method also sets the <em>content-length</em> header of the HTTP response
     * and sets the <em>content-type</em> header to {@link #CONTENT_TYPE_OCTET_STREAM}
     * but does not end the response.
     *
     * @param response The HTTP response.
     * @param buffer The Buffer to set as the response body (may be {@code null}).
     * @throws NullPointerException if response is {@code null}.
     */
    public static void setResponseBody(final HttpServerResponse response, final Buffer buffer) {
        setResponseBody(response, buffer, null);
    }

    /**
     * Writes a Buffer to an HTTP response body.
     * <p>
     * This method also sets the <em>content-length</em> and <em>content-type</em>
     * headers of the HTTP response accordingly but does not end the response.
     * <p>
     * If the response is already ended or closed or the buffer is {@code null}, this method
     * does nothing.
     *
     * @param response The HTTP response.
     * @param buffer The Buffer to set as the response body (may be {@code null}).
     * @param contentType The type of the content. If {@code null}, a default value of
     *                    {@link #CONTENT_TYPE_OCTET_STREAM} will be used.
     * @throws NullPointerException if response is {@code null}.
     */
    public static void setResponseBody(final HttpServerResponse response, final Buffer buffer, final String contentType) {

        Objects.requireNonNull(response);
        if (!response.ended() && !response.closed() && buffer != null) {
            response.putHeader(HttpHeaders.CONTENT_TYPE,
                    Objects.requireNonNullElse(contentType, CONTENT_TYPE_OCTET_STREAM));
            response.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(buffer.length()));
            response.write(buffer);
        }
    }

    /**
     * Test if an HTTP status code is considered an error.
     * <p>
     * The test will consider everything in the range of 400 (inclusive) to 600 (exclusive) an error.
     *
     * @param status the status to test
     * @return {@code true} if the status is an "error", {@code false} otherwise.
     */
    public static boolean isError(final int status) {
        return status >= HttpURLConnection.HTTP_BAD_REQUEST && status < 600;
    }

    /**
     * Gets the absolute URI of the given request.
     * <p>
     * In contrast to {@link HttpServerRequest#absoluteURI()} this method
     * won't return {@code null} if the URI is invalid (at least not if {@link HttpServerRequest#host()} is set).
     *
     * @param req The request to get the absolute URI from.
     * @return The absolute URI.
     * @throws NullPointerException if req is {@code null}.
     */
    public static String getAbsoluteURI(final HttpServerRequest req) {
        Objects.requireNonNull(req);
        final String uri = req.uri();
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            return uri;
        }
        final String host = req.host();
        if (host == null) {
            // fall back to using the original method which will use the serverOrigin as host
            // see io.vertx.core.http.impl.HttpUtils.absoluteURI(String, HttpServerRequest)
            return req.absoluteURI();
        }
        return req.scheme() + "://" + host + uri;
    }

    /**
     * Checks the URI of the given request and logs an error to the given
     * span if it is invalid.
     *
     * @param req The request to check the absolute URI for.
     * @param span The span to log to.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void logErrorIfInvalidURI(final HttpServerRequest req, final Span span) {
        Objects.requireNonNull(req);
        Objects.requireNonNull(span);
        try {
            new URI(req.uri());
        } catch (final URISyntaxException e) {
            LOG.debug("invalid request URI", e);
            TracingHelper.logError(span, "invalid request URI", e);
        }
    }

    /**
     * Invokes the next route on the given routing context, checking first whether the request path is invalid and
     * failing the context if that is the case.
     * <p>
     * This check prevents exceptions thrown for invalid request paths on invoking the next route, if the route has
     * {code useNormalizedPath} set to {@code true}, which is the default.
     *
     * @param ctx The routing context.
     */
    public static void nextRoute(final RoutingContext ctx) {
        try {
            ctx.normalizedPath();
        } catch (final IllegalArgumentException ex) {
            LOG.debug("invalid request path [{}]: {}", ctx.request().path(), ex.getMessage());
            ctx.fail(400, ex);
            return;
        }
        ctx.next();
    }

    /**
     * Adds an error handler for {@code 404} status requests to the given router.
     * The handler adds an error log entry in the request span and sets a response body (if it's not a HEAD request).
     *
     * @param router The router to add the error handler to.
     * @throws NullPointerException if router is {@code null}.
     */
    public static void addDefault404ErrorHandler(final Router router) {
        Objects.requireNonNull(router);
        router.errorHandler(404, ctx -> {
            if (!ctx.response().ended()) {
                ctx.response().setStatusCode(404);
                Optional.ofNullable(HttpServerSpanHelper.serverSpan(ctx))
                        .ifPresent(span -> TracingHelper.logError(span, HttpResponseStatus.valueOf(404).reasonPhrase()));
                if (!ctx.request().method().equals(HttpMethod.HEAD)) {
                    ctx.response()
                            .putHeader(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=utf-8")
                            .end("<html><body><h1>Resource not found</h1></body></html>");
                } else {
                    ctx.response().end();
                }
            }
        });
    }
}
