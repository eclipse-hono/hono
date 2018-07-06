/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
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

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.Constants;

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
    public static final String CONTENT_TYPE_JSON_UFT8 = "application/json; charset=utf-8";
    /**
     * The <em>application/json; charset=utf-8</em> content type.
     */
    public static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    /**
     * The <em>text/plain; charset=utf-8</em> content type.
     */
    public static final String CONTENT_TYPE_TEXT_UFT8 = "text/plain; charset=utf-8";

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
     * Ends a response with a given HTTP status code and detail message.
     *
     * @param ctx The vert.x routing context to fail.
     * @param status The status code to write to the response.
     * @param headers HTTP headers to set on the response (may be {@code null}).
     * @param detail The message to write to the response's body (may be {@code null}).
     * @throws NullPointerException if routing context is {@code null}.
     * @deprecated Use {@link #failWithHeaders(RoutingContext, ServiceInvocationException, Map)} instead.
     */
    public static void failWithStatus(
            final RoutingContext ctx,
            final int status,
            final Map<CharSequence, CharSequence> headers,
            final String detail) {

        failWithHeaders(ctx, new ServiceInvocationException(status, detail), headers);
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
        if (headers != null) {
            for (final Entry<CharSequence, CharSequence> header : headers.entrySet()) {
                ctx.response().putHeader(header.getKey(), header.getValue());
            }
        }
        ctx.fail(error);
    }

    /**
     * Gets the value of the <em>Content-Type</em> HTTP header for a request.
     * 
     * @param ctx The routing context containing the HTTP request.
     * @return The content type or {@code null} if the request doesn't contain a
     *         <em>Content-Type</em> header.
     * @throws NullPointerException if context is {@code null}.
     */
    public static String getContentType(final RoutingContext ctx) {

        return Objects.requireNonNull(ctx).parsedHeaders().contentType().value();
    }

    /**
     * Gets the value of the {@link org.eclipse.hono.util.Constants#HEADER_TIME_TIL_DISCONNECT} HTTP header for a request.
     * If no such header can be found, the query is searched for containing a query parameter with the same key.
     *
     * @param ctx The routing context containing the HTTP request.
     * @return The time til disconnect or {@code null} if
     * <ul>
     *     <li>the request doesn't contain a {@link org.eclipse.hono.util.Constants#HEADER_TIME_TIL_DISCONNECT} header or query parameter.</li>
     *     <li>the contained value cannot be parsed as an Integer</li>
     * </ul>
     * @throws NullPointerException if context is {@code null}.
     */
    public static Integer getTimeTilDisconnect(final RoutingContext ctx) {
        Objects.requireNonNull(ctx);

        try {
            Optional<String> timeTilDisconnectHeader = Optional.ofNullable(ctx.request().getHeader(Constants.HEADER_TIME_TIL_DISCONNECT));

            if (!timeTilDisconnectHeader.isPresent()) {
                timeTilDisconnectHeader = Optional.ofNullable(ctx.request().getParam(Constants.HEADER_TIME_TIL_DISCONNECT));
            }

            if (timeTilDisconnectHeader.isPresent()) {
                return Integer.parseInt(timeTilDisconnectHeader.get());
            }
        } catch (final NumberFormatException e) {
        }

        return null;
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
            return Optional.ofNullable(ctx.request().getHeader(Constants.HEADER_COMMAND_RESPONSE_STATUS)).map(cmdResponseStatusHeader ->
                    Optional.of(Integer.parseInt(cmdResponseStatusHeader))
            ).orElse(Optional.ofNullable(ctx.request().getParam(Constants.HEADER_COMMAND_RESPONSE_STATUS)).map(cmdResponseStatusParam ->
                    Optional.of(Integer.parseInt(cmdResponseStatusParam))
            ).orElse(
                    Optional.empty()
            ));
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }

    }

    /**
     * Writes a JSON object to an HTTP response body.
     * <p>
     * This method also sets the <em>content-type</em> and <em>content-length</em>
     * headers of the HTTP response accordingly but does not end the response.
     * 
     * @param response The HTTP response.
     * @param body The JSON object to serialize to the response body (may be {@code null}).
     * @throws NullPointerException if response is {@code null}.
     */
    public static void setResponseBody(final HttpServerResponse response, final JsonObject body) {
        Objects.requireNonNull(response);
        if (body != null) {
            response.putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON_UFT8);
            setResponseBody(response, body.toBuffer());
        }
    }

    /**
     * Writes a Buffer to an HTTP response body.
     * <p>
     * This method also sets the <em>content-type</em> and <em>content-length</em>
     * headers of the HTTP response accordingly but does not end the response.
     *
     * @param response The HTTP response.
     * @param buffer The Buffer to set as the response body (may be {@code null}).
     * @throws NullPointerException if response is {@code null}.
     */
    public static void setResponseBody(final HttpServerResponse response, final Buffer buffer) {
        Objects.requireNonNull(response);
        if (buffer != null) {
            response.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(buffer.length()))
                    .write(buffer);
        }
    }

}
