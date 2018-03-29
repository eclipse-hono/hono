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

package org.eclipse.hono.tests;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.hono.client.ServiceInvocationException;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;

/**
 * A vert.x based HTTP client for invoking generic CRUD operations on HTTP APIs.
 *
 */
public final class CrudHttpClient {

    private static final String CONTENT_TYPE_JSON = "application/json";

    private final Vertx vertx;
    private final String host;
    private final int port;

    /**
     * Creates a new client for a host and port.
     * 
     * @param vertx The vert.x instance to use.
     * @param host The host to invoke the operations on.
     * @param port The port that the service is bound to.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public CrudHttpClient(final Vertx vertx, final String host, final int port) {
        this.vertx = Objects.requireNonNull(vertx);
        this.host = Objects.requireNonNull(host);
        this.port = port;
    }

    /**
     * Gets options for a resource using an HTTP OPTIONS.
     * 
     * @param uri The resource URI.
     * @param requestHeaders The headers to include in the request.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}. In that case the
     *         future will contain the response headers.
     */
    public Future<MultiMap> options(
            final String uri,
            final MultiMap requestHeaders,
            final Predicate<Integer> successPredicate) {

        final Future<MultiMap> result = Future.future();

        final HttpClientRequest req = vertx.createHttpClient()
            .options(port, host, uri)
            .handler(response -> {
                if (successPredicate.test(response.statusCode())) {
                    result.complete(response.headers());
                } else {
                    result.fail(new ServiceInvocationException(response.statusCode()));
                }
            }).exceptionHandler(result::fail);

        if (requestHeaders != null) {
            req.headers().addAll(requestHeaders);
        }
        req.end();
        return result;
    }

    /**
     * Creates a resource using an HTTP POST.
     * <p>
     * The content type in the request is set to <em>application/json</em>.
     * 
     * @param uri The URI to post to.
     * @param body The body to post.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     */
    public Future<Void> create(final String uri, final JsonObject body, final Predicate<Integer> successPredicate) {
        return create(uri, body, CONTENT_TYPE_JSON, successPredicate);
    }

    /**
     * Creates a resource using HTTP POST.
     * 
     * @param uri The URI to post to.
     * @param body The body to post (may be {@code null}).
     * @param contentType The content type to set in the request (may be {@code null}).
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     */
    public Future<Void> create(final String uri, final JsonObject body, final String contentType, final Predicate<Integer> successPredicate) {

        return create(uri, Optional.ofNullable(body).map(json -> json.toBuffer()).orElse(null), contentType, successPredicate);
    }

    /**
     * Creates a resource using HTTP POST.
     * 
     * @param uri The URI to post to.
     * @param body The body to post (may be {@code null}).
     * @param contentType The content type to set in the request (may be {@code null}).
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     */
    public Future<Void> create(final String uri, final Buffer body, final String contentType, final Predicate<Integer> successPredicate) {

        final MultiMap headers = Optional.ofNullable(contentType)
                .map(ct -> MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.CONTENT_TYPE, contentType))
                .orElse(null);

        return create(uri, body, headers, successPredicate).compose(ok -> Future.succeededFuture());
    }

    /**
     * Creates a resource using HTTP POST.
     * 
     * @param uri The URI to post to.
     * @param body The body to post (may be {@code null}).
     * @param requestHeaders The headers to include in the request (may be {@code null}).
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     */
    public Future<MultiMap> create(
            final String uri,
            final Buffer body,
            final MultiMap requestHeaders,
            final Predicate<Integer> successPredicate) {

        Objects.requireNonNull(uri);
        Objects.requireNonNull(successPredicate);

        final Future<MultiMap> result = Future.future();

        final HttpClientRequest req = vertx.createHttpClient()
            .post(port, host, uri)
            .handler(response -> {
                if (successPredicate.test(response.statusCode())) {
                    result.complete(response.headers());
                } else {
                    result.fail(new ServiceInvocationException(response.statusCode()));
                }
            }).exceptionHandler(result::fail);

        if (requestHeaders != null) {
            req.headers().addAll(requestHeaders);
        }
        if (body == null) {
            req.end();
        } else {
            req.end(body);
        }
        return result;
    }

    /**
     * Updates a resource using HTTP PUT.
     * <p>
     * The content type in the request is set to <em>application/json</em>.
     * 
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     */
    public Future<Void> update(final String uri, final JsonObject body, final Predicate<Integer> successPredicate) {
        return update(uri, body, CONTENT_TYPE_JSON, successPredicate);
    }

    /**
     * Updates a resource using HTTP PUT.
     * 
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param contentType The content type to set in the request.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     */
    public Future<Void> update(final String uri, final JsonObject body, final String contentType, final Predicate<Integer> successPredicate) {
        return update(uri, Optional.ofNullable(body).map(json -> json.toBuffer()).orElse(null), contentType, successPredicate);
    }

    /**
     * Updates a resource using HTTP PUT.
     * 
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param contentType The content type to set in the request.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     */
    public Future<Void> update(final String uri, final Buffer body, final String contentType, final Predicate<Integer> successPredicate) {

        final MultiMap headers = Optional.ofNullable(contentType)
                .map(ct -> MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.CONTENT_TYPE, ct))
                .orElse(null);

        return update(uri, body, headers, successPredicate).compose(ok -> Future.succeededFuture());
    }

    /**
     * Updates a resource using HTTP PUT.
     * 
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param requestHeaders The headers to include in the request.
     * @param successPredicate A predicate on the response for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     */
    public Future<MultiMap> update(final String uri, final Buffer body, final MultiMap requestHeaders, final Predicate<Integer> successPredicate) {

        final Future<MultiMap> result = Future.future();

        final HttpClientRequest req = vertx.createHttpClient()
            .put(port, host, uri)
            .handler(response -> {
                if (successPredicate.test(response.statusCode())) {
                    result.complete(response.headers());
                } else {
                    result.fail(new ServiceInvocationException(response.statusCode()));
                }
            }).exceptionHandler(result::fail);

        if (requestHeaders != null) {
            req.headers().addAll(requestHeaders);
        }
        if (body == null) {
            req.end();
        } else {
            req.end(body);
        }
        return result;
    }

    /**
     * Retrieves a resource representation using HTTP GET.
     * 
     * @param uri The resource to retrieve.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}. In that case the
     *         future will contain the response body.
     */
    public Future<Buffer> get(final String uri, final Predicate<Integer> successPredicate) {

        final Future<Buffer> result = Future.future();

        vertx.createHttpClient()
            .get(port, host, uri)
            .handler(response -> {
                if (successPredicate.test(response.statusCode())) {
                    response.bodyHandler(body -> result.complete(body));
                } else {
                    result.fail(new ServiceInvocationException(response.statusCode()));
                }
            }).exceptionHandler(result::fail).end();

        return result;
    }

    /**
     * Deletes a resource using HTTP DELETE.
     * 
     * @param uri The resource to delete.
     * @param successPredicate A predicate on the returned HTTP status code for determining success.
     * @return A future that will succeed if the predicate evaluates to {@code true}.
     */
    public Future<Void> delete(final String uri, final Predicate<Integer> successPredicate) {

        final Future<Void> result = Future.future();

        vertx.createHttpClient()
            .delete(port, host, uri)
            .handler(response -> {
                if (successPredicate.test(response.statusCode())) {
                    result.complete();
                } else {
                    result.fail(new ServiceInvocationException(response.statusCode()));
                }
            }).exceptionHandler(result::fail).end();

        return result;
    }
}
