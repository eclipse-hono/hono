/*******************************************************************************
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import io.vertx.core.http.HttpClientResponse;
import org.eclipse.hono.client.ServiceInvocationException;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A vert.x based HTTP client for invoking generic CRUD operations on HTTP APIs.
 *
 */
public final class CrudHttpClient {
    /**
     * The content type indicating an application specific JSON payload.
     */
    public static final String CONTENT_TYPE_JSON = "application/json";
    /**
     * The example value to be used as the origin of the call.
     */
    public static final String ORIGIN_URI = "http://hono.eclipse.org";

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
    private final HttpClient client;
    private final Context context;
    private final HttpClientOptions options;

    /**
     * Creates a new client for a host and port.
     * 
     * @param vertx The vert.x instance to use.
     * @param defaultHost The host to invoke the operations on by default.
     * @param defaultPort The port to send requests to by default.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public CrudHttpClient(final Vertx vertx, final String defaultHost, final int defaultPort) {
        this(vertx, new HttpClientOptions().setDefaultHost(defaultHost).setDefaultPort(defaultPort));
    }

    /**
     * Creates a new client for a host and port.
     * 
     * @param vertx The vert.x instance to use.
     * @param options The client options to use for connecting to servers.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public CrudHttpClient(final Vertx vertx, final HttpClientOptions options) {
        this.options = Objects.requireNonNull(options);
        this.client = vertx.createHttpClient(options);
        this.context = vertx.getOrCreateContext();
    }

    private RequestOptions createRequestOptions() {
        return new RequestOptions()
                .setSsl(options.isSsl())
                .setHost(options.getDefaultHost())
                .setPort(options.getDefaultPort());
    }

    /**
     * Gets options for a resource using an HTTP OPTIONS request.
     * 
     * @param uri The resource URI.
     * @param requestHeaders The headers to include in the request.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}. In that case the
     *         future will contain the response headers.
     * @throws NullPointerException if URI or predicate are {@code null}.
     */
    public Future<MultiMap> options(
            final String uri,
            final MultiMap requestHeaders,
            final IntPredicate successPredicate) {

        Objects.requireNonNull(uri);
        return options(createRequestOptions().setURI(uri), requestHeaders, successPredicate);
    }

    /**
     * Gets options for a resource using an HTTP OPTIONS request.
     * 
     * @param requestOptions The options to use for the request.
     * @param requestHeaders The headers to include in the request.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}. In that case the
     *         future will contain the response headers.
     * @throws NullPointerException if options or predicate are {@code null}.
     */
    public Future<MultiMap> options(
            final RequestOptions requestOptions,
            final MultiMap requestHeaders,
            final IntPredicate successPredicate) {

        Objects.requireNonNull(requestOptions);
        Objects.requireNonNull(successPredicate);

        final Promise<MultiMap> result = Promise.promise();

        context.runOnContext(go -> {
            @SuppressWarnings("deprecation")
            final HttpClientRequest req = client.options(requestOptions)
                    .handler(response -> {
                        if (successPredicate.test(response.statusCode())) {
                            result.tryComplete(response.headers());
                        } else {
                            result.tryFail(newUnexpectedResponseStatusException(response.statusCode()));
                        }
                    }).exceptionHandler(result::tryFail);

                if (requestHeaders != null) {
                    req.headers().addAll(requestHeaders);
                }
                req.end();
        });
        return result.future();
    }

    /**
     * Creates a resource using an HTTP POST request.
     * <p>
     * The content type in the request is set to <em>application/json</em>.
     * 
     * @param uri The URI to post to.
     * @param body The body to post.
     * @param successPredicate A predicate on the HTTP response for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if URI or predicate are {@code null}.
     */
    public Future<MultiMap> create(final String uri, final JsonObject body,
            final Predicate<HttpClientResponse> successPredicate) {
        return create(uri, body, CONTENT_TYPE_JSON, successPredicate, false);
    }

    /**
     * Creates a resource using an HTTP POST request.
     * 
     * @param uri The URI to post to.
     * @param body The body to post (may be {@code null}).
     * @param contentType The content type to set in the request (may be {@code null}).
     * @param successPredicate A predicate on the HTTP response for determining success.
     * @param checkCorsHeaders Whether to set and check CORS headers.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if URI, content type or predicate are {@code null}.
     */
    public Future<MultiMap> create(final String uri, final JsonObject body, final String contentType,
            final Predicate<HttpClientResponse> successPredicate, final boolean checkCorsHeaders) {

        return create(uri,
                Optional.ofNullable(body).map(json -> json.toBuffer()).orElse(null),
                contentType,
                successPredicate,
                checkCorsHeaders);
    }

    /**
     * Creates a resource using an HTTP POST request.
     * 
     * @param uri The URI to post to.
     * @param body The body to post (may be {@code null}).
     * @param contentType The content type to set in the request (may be {@code null}).
     * @param successPredicate A predicate on the HTTP response for determining success.
     * @param checkCorsHeaders Whether to set and check CORS headers.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if URI or predicate are {@code null}.
     */
    public Future<MultiMap> create(final String uri, final Buffer body, final String contentType,
            final Predicate<HttpClientResponse> successPredicate, final boolean checkCorsHeaders) {

        final MultiMap requestHeaders = Optional.ofNullable(contentType)
                .map(ct -> MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.CONTENT_TYPE, contentType))
                .orElse(null);

        return create(
                createRequestOptions().setURI(uri),
                body,
                requestHeaders,
                successPredicate,
                checkCorsHeaders);
    }

    /**
     * Creates a resource using an HTTP POST request.
     * 
     * @param uri The URI to post to.
     * @param body The body to post (may be {@code null}).
     * @param requestHeaders The headers to include in the request (may be {@code null}).
     * @param successPredicate A predicate on the HTTP response for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if URI or predicate are {@code null}.
     */
    public Future<MultiMap> create(
            final String uri,
            final Buffer body,
            final MultiMap requestHeaders,
            final Predicate<HttpClientResponse> successPredicate) {

        Objects.requireNonNull(uri);
        Objects.requireNonNull(successPredicate);

        return create(
                createRequestOptions().setURI(uri),
                body,
                requestHeaders,
                successPredicate,
                false);
    }

    /**
     * Creates a resource using an HTTP POST request.
     * 
     * @param requestOptions The options to use for the request.
     * @param body The body to post (may be {@code null}).
     * @param requestHeaders The headers to include in the request (may be {@code null}).
     * @param successPredicate A predicate on the HTTP response for determining success.
     * @param checkCorsHeaders Whether to set and check CORS headers.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if options or predicate are {@code null}.
     */
    public Future<MultiMap> create(
            final RequestOptions requestOptions,
            final Buffer body,
            final MultiMap requestHeaders,
            final Predicate<HttpClientResponse> successPredicate,
            final boolean checkCorsHeaders) {

        Objects.requireNonNull(requestOptions);
        Objects.requireNonNull(successPredicate);

        final Promise<MultiMap> result = Promise.promise();

        context.runOnContext(go -> {
            @SuppressWarnings("deprecation")
            final HttpClientRequest req = client.post(requestOptions)
                    .handler(response -> {
                        LOGGER.trace("response status code {}", response.statusCode());
                        if (successPredicate.test(response)) {
                            if (checkCorsHeaders) {
                                checkCorsHeaders(response, result.future());
                            }
                            result.tryComplete(response.headers());
                        } else {
                            result.tryFail(newUnexpectedResponseStatusException(response.statusCode()));
                        }
                    }).exceptionHandler(result::tryFail);

                if (requestHeaders != null) {
                    req.headers().addAll(requestHeaders);
                }
                if (checkCorsHeaders) {
                    req.headers().add(HttpHeaders.ORIGIN, ORIGIN_URI);
                }
                if (body == null) {
                    req.end();
                } else {
                    req.end(body);
                }
        });
        return result.future();
    }

    /**
     * Updates a resource using an HTTP PUT request.
     * <p>
     * The content type in the request is set to <em>application/json</em>.
     * 
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if URI or predicate are {@code null}.
     */
    public Future<MultiMap> update(final String uri, final JsonObject body, final IntPredicate successPredicate) {
        return update(uri, body, CONTENT_TYPE_JSON, successPredicate);
    }

    /**
     * Updates a resource using an HTTP PUT request.
     * <p>
     * The content type in the request is set to <em>application/json</em>.
     *
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if URI or predicate are {@code null}.
     */
    public Future<MultiMap> update(final String uri, final JsonArray body, final IntPredicate successPredicate) {
        return update(uri, body, CONTENT_TYPE_JSON, successPredicate);
    }

    /**
     * Updates a resource using an HTTP PUT request.
     * 
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param contentType The content type to set in the request.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if URI or predicate are {@code null}.
     */
    public Future<MultiMap> update(final String uri, final JsonObject body, final String contentType,
            final IntPredicate successPredicate) {
        return update(uri, Optional.ofNullable(body).map(json -> json.toBuffer()).orElse(null), contentType, successPredicate, true);

    }

    /**
     * Updates a resource using an HTTP PUT request.
     *
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param contentType The content type to set in the request.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if URI or predicate are {@code null}.
     */
    public Future<MultiMap> update(final String uri, final JsonArray body, final String contentType,
            final IntPredicate successPredicate) {
        return update(uri, Optional.ofNullable(body).map(json -> json.toBuffer()).orElse(null), contentType, successPredicate, true);

    }

    /**
     * Updates a resource using an HTTP PUT request.
     * 
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param contentType The content type to set in the request.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @param checkCorsHeaders Whether to set and check CORS headers.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if URI or predicate are {@code null}.
     */
    public Future<MultiMap> update(final String uri, final Buffer body, final String contentType,
            final IntPredicate successPredicate, final boolean checkCorsHeaders) {

        final MultiMap headers = Optional.ofNullable(contentType)
                .map(ct -> MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.CONTENT_TYPE, ct))
                .orElse(null);

        return update(uri, body, headers, successPredicate, checkCorsHeaders);
    }

    /**
     * Updates a resource using an HTTP PUT request.
     * 
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param requestHeaders The headers to include in the request.
     * @param successPredicate A predicate on the response for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if URI or predicate are {@code null}.
     */
    public Future<MultiMap> update(
            final String uri,
            final Buffer body,
            final MultiMap requestHeaders,
            final IntPredicate successPredicate) {

        Objects.requireNonNull(uri);
        Objects.requireNonNull(successPredicate);

        return update(
                createRequestOptions().setURI(uri),
                body,
                requestHeaders,
                successPredicate,
                false);
    }

    /**
     * Updates a resource using an HTTP PUT request.
     *
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param requestHeaders The headers to include in the request.
     * @param successPredicate A predicate on the response for determining success.
     * @param checkCorsHeaders Whether to set and check CORS headers.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if URI or predicate are {@code null}.
     */
    public Future<MultiMap> update(
            final String uri,
            final Buffer body,
            final MultiMap requestHeaders,
            final IntPredicate successPredicate,
            final boolean checkCorsHeaders) {

        Objects.requireNonNull(uri);
        Objects.requireNonNull(successPredicate);

        return update(
                createRequestOptions().setURI(uri),
                body,
                requestHeaders,
                successPredicate,
                checkCorsHeaders);
    }

    /**
     * Updates a resource using an HTTP PUT request.
     * 
     * @param requestOptions The options to use for the request.
     * @param body The content to update the resource with.
     * @param requestHeaders The headers to include in the request.
     * @param successPredicate A predicate on the response for determining success.
     * @param checkCorsHeaders Whether to set and check CORS headers.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if options or predicate are {@code null}.
     */
    public Future<MultiMap> update(
            final RequestOptions requestOptions,
            final Buffer body,
            final MultiMap requestHeaders,
            final IntPredicate successPredicate,
            final boolean checkCorsHeaders) {

        Objects.requireNonNull(requestOptions);
        Objects.requireNonNull(successPredicate);

        final Promise<MultiMap> result = Promise.promise();

        context.runOnContext(go -> {
            @SuppressWarnings("deprecation")
            final HttpClientRequest req = client.put(requestOptions)
                    .handler(response -> {
                        if (successPredicate.test(response.statusCode())) {
                            if (checkCorsHeaders) {
                                checkCorsHeaders(response, result.future());
                            }
                            result.tryComplete(response.headers());
                        } else {
                            result.tryFail(newUnexpectedResponseStatusException(response.statusCode()));
                        }
                    }).exceptionHandler(result::tryFail);

                if (requestHeaders != null) {
                    req.headers().addAll(requestHeaders);
                }
                if (checkCorsHeaders) {
                    req.headers().add(HttpHeaders.ORIGIN, ORIGIN_URI);
                }
                if (body == null) {
                    req.end();
                } else {
                    req.end(body);
                }
        });
        return result.future();
    }

    /**
     * Retrieves a resource representation using an HTTP GET request.
     * 
     * @param uri The resource to retrieve.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}. In that case the
     *         future will contain the response body.
     * @throws NullPointerException if URI or predicate are {@code null}.
     */
    public Future<Buffer> get(final String uri, final IntPredicate successPredicate) {

        Objects.requireNonNull(uri);
        Objects.requireNonNull(successPredicate);
        return get(createRequestOptions().setURI(uri), successPredicate);
    }

    /**
     * Retrieves a resource representation using an HTTP GET request.
     * 
     * @param requestOptions The options to use for the request.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}. In that case the
     *         future will contain the response body.
     * @throws NullPointerException if options or predicate are {@code null}.
     */
    public Future<Buffer> get(final RequestOptions requestOptions, final IntPredicate successPredicate) {

        Objects.requireNonNull(requestOptions);
        Objects.requireNonNull(successPredicate);

        final Promise<Buffer> result = Promise.promise();

        context.runOnContext(go -> {
            @SuppressWarnings("deprecation")
            final HttpClientRequest req = client.get(requestOptions)
            .handler(response -> {
                if (successPredicate.test(response.statusCode())) {
                    if (response.statusCode() < 400) {
                        checkCorsHeaders(response, result.future());
                    }
                    response.bodyHandler(body -> result.tryComplete(body));
                } else {
                    result.tryFail(newUnexpectedResponseStatusException(response.statusCode()));
                }
            }).exceptionHandler(result::tryFail);
            req.headers().add(HttpHeaders.ORIGIN, ORIGIN_URI);
            req.end();
        });

        return result.future();
    }

    /**
     * Deletes a resource using an HTTP DELETE request.
     * 
     * @param uri The resource to delete.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if URI or predicate are {@code null}.
     */
    public Future<Void> delete(final String uri, final IntPredicate successPredicate) {

        Objects.requireNonNull(uri);
        Objects.requireNonNull(successPredicate);

        return delete(createRequestOptions().setURI(uri), successPredicate);
    }

    /**
     * Deletes a resource using an HTTP DELETE request.
     * 
     * @param requestOptions The options to use for the request.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     * @throws NullPointerException if options or predicate are {@code null}.
     */
    public Future<Void> delete(final RequestOptions requestOptions, final IntPredicate successPredicate) {

        Objects.requireNonNull(requestOptions);
        Objects.requireNonNull(successPredicate);

        final Promise<Void> result = Promise.promise();

        context.runOnContext(go -> {
            @SuppressWarnings("deprecation")
            final HttpClientRequest req = client.delete(requestOptions)
            .handler(response -> {
                LOGGER.debug("got response [status: {}]", response.statusCode());
                if (successPredicate.test(response.statusCode())) {
                    checkCorsHeaders(response, result.future());
                    result.tryComplete();
                } else {
                    result.tryFail(newUnexpectedResponseStatusException(response.statusCode()));
                }
            }).exceptionHandler(result::tryFail);
            req.headers().add(HttpHeaders.ORIGIN, ORIGIN_URI);
            req.end();
        });

        return result.future();
    }

    /**
     * Checks if the the response have proper headers set.
     *
     * @param response The response to check.
     * @param result the result will fail in case headers are not set
     */
    private void checkCorsHeaders(final HttpClientResponse response, final Future result) {
        final MultiMap headers = response.headers();
        if (!"*".equals(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))) {
            result.fail("Response does not contain proper Access-Control-Allow-Origin header");
        }
    }

    private static ServiceInvocationException newUnexpectedResponseStatusException(final int statusCode) {
        if (statusCode >= 400 && statusCode < 600) {
            return new ServiceInvocationException(statusCode);
        }
        return new ServiceInvocationException(HttpURLConnection.HTTP_PRECON_FAILED,
                "Unexpected response status " + statusCode);
    }

    @Override
    public String toString() {
        return String.format("CrudHttpClient [%s:%d]", options.getDefaultHost(), options.getDefaultPort());
    }
}
