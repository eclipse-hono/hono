/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.tests;

import java.util.Objects;
import java.util.Optional;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.predicate.ResponsePredicate;

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

    private final WebClient client;
    private final Context context;
    private final WebClientOptions options;

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
     * @param httpClientOptions The HTTP client options to use for connecting to servers.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public CrudHttpClient(final Vertx vertx, final HttpClientOptions httpClientOptions) {
        this.options = new WebClientOptions(Objects.requireNonNull(httpClientOptions));
        this.client = WebClient.create(vertx, this.options);
        this.context = vertx.getOrCreateContext();
    }

    private static void addResponsePredicates(final HttpRequest<?> request, final ResponsePredicate ... successPredicates) {
        Optional.ofNullable(successPredicates).ifPresent(predicates -> {
            for (final ResponsePredicate predicate : predicates) {
                request.expect(predicate);
            }
        });
    }

    /**
     * Gets options for a resource using an HTTP OPTIONS request.
     *
     * @param uri The resource URI.
     * @param requestHeaders The headers to include in the request.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> options(
            final String uri,
            final MultiMap requestHeaders,
            final ResponsePredicate ... successPredicates) {

        Objects.requireNonNull(uri);
        return options(createRequestOptions().setURI(uri), requestHeaders, successPredicates);
    }

    /**
     * Gets options for a resource using an HTTP OPTIONS request.
     *
     * @param requestOptions The options to use for the request.
     * @param requestHeaders The headers to include in the request.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if options is {@code null}.
     */
    public Future<HttpResponse<Buffer>> options(
            final RequestOptions requestOptions,
            final MultiMap requestHeaders,
            final ResponsePredicate ... successPredicates) {

        Objects.requireNonNull(requestOptions);

        final Promise<HttpResponse<Buffer>> result = Promise.promise();

        context.runOnContext(go -> {
            final HttpRequest<Buffer> req = client.request(HttpMethod.OPTIONS, requestOptions);
            Optional.ofNullable(requestHeaders).ifPresent(req::putHeaders);
            addResponsePredicates(req, successPredicates);
            req.send(result);
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
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> create(
            final String uri,
            final JsonObject body,
            final ResponsePredicate ... successPredicates) {
        return create(uri, body, CONTENT_TYPE_JSON, successPredicates);
    }

    /**
     * Creates a resource using an HTTP POST request.
     *
     * @param uri The URI to post to.
     * @param body The body to post (may be {@code null}).
     * @param contentType The content type to set in the request (may be {@code null}).
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> create(
            final String uri,
            final JsonObject body,
            final String contentType,
            final ResponsePredicate ... successPredicates) {

        return create(uri,
                Optional.ofNullable(body).map(json -> json.toBuffer()).orElse(null),
                contentType,
                successPredicates);
    }

    /**
     * Creates a resource using an HTTP POST request.
     *
     * @param uri The URI to post to.
     * @param body The body to post (may be {@code null}).
     * @param contentType The content type to set in the request (may be {@code null}).
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> create(
            final String uri,
            final Buffer body,
            final String contentType,
            final ResponsePredicate ... successPredicates) {

        final MultiMap requestHeaders = Optional.ofNullable(contentType)
                .map(ct -> MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.CONTENT_TYPE, contentType))
                .orElse(null);

        return create(
                createRequestOptions().setURI(uri).setHeaders(requestHeaders),
                body,
                successPredicates);
    }

    /**
     * Creates a resource using an HTTP POST request.
     *
     * @param uri The URI to post to.
     * @param body The body to post (may be {@code null}).
     * @param requestHeaders The headers to include in the request (may be {@code null}).
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> create(
            final String uri,
            final Buffer body,
            final MultiMap requestHeaders,
            final ResponsePredicate ... successPredicates) {

        Objects.requireNonNull(uri);

        return create(
                createRequestOptions().setURI(uri).setHeaders(requestHeaders),
                body,
                successPredicates);
    }

    /**
     * Creates a resource using an HTTP POST request.
     *
     * @param requestOptions The options to use for the request.
     * @param body The body to post (may be {@code null}).
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if options is {@code null}.
     */
    public Future<HttpResponse<Buffer>> create(
            final RequestOptions requestOptions,
            final Buffer body,
            final ResponsePredicate ... successPredicates) {

        Objects.requireNonNull(requestOptions);

        final Promise<HttpResponse<Buffer>> result = Promise.promise();

        context.runOnContext(go -> {
            final HttpRequest<Buffer> req = client.request(HttpMethod.POST, requestOptions);
            addResponsePredicates(req, successPredicates);
            if (body == null) {
                req.send(result);
            } else {
                req.sendBuffer(body, result);
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
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> update(
            final String uri,
            final JsonObject body,
            final ResponsePredicate ... successPredicates) {
        return update(uri, body, CONTENT_TYPE_JSON, successPredicates);
    }

    /**
     * Updates a resource using an HTTP PUT request.
     * <p>
     * The content type in the request is set to <em>application/json</em>.
     *
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> update(
            final String uri,
            final JsonArray body,
            final ResponsePredicate ... successPredicates) {
        return update(uri, body, CONTENT_TYPE_JSON, successPredicates);
    }

    /**
     * Updates a resource using an HTTP PUT request.
     *
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param contentType The content type to set in the request.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> update(
            final String uri,
            final JsonObject body,
            final String contentType,
            final ResponsePredicate ... successPredicates) {
        return update(uri, Optional.ofNullable(body).map(json -> json.toBuffer()).orElse(null), contentType, successPredicates);

    }

    /**
     * Updates a resource using an HTTP PUT request.
     *
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param contentType The content type to set in the request.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> update(
            final String uri,
            final JsonArray body,
            final String contentType,
            final ResponsePredicate ... successPredicates) {
        return update(uri, Optional.ofNullable(body).map(json -> json.toBuffer()).orElse(null), contentType, successPredicates);

    }

    /**
     * Updates a resource using an HTTP PUT request.
     *
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param contentType The content type to set in the request.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> update(
            final String uri,
            final Buffer body,
            final String contentType,
            final ResponsePredicate ... successPredicates) {

        final MultiMap headers = Optional.ofNullable(contentType)
                .map(ct -> MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.CONTENT_TYPE, ct))
                .orElse(null);

        return update(uri, body, headers, successPredicates);
    }

    /**
     * Updates a resource using an HTTP PUT request.
     *
     * @param uri The resource to update.
     * @param body The content to update the resource with.
     * @param requestHeaders The headers to include in the request.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> update(
            final String uri,
            final Buffer body,
            final MultiMap requestHeaders,
            final ResponsePredicate ... successPredicates) {

        Objects.requireNonNull(uri);

        return update(
                createRequestOptions().setURI(uri).setHeaders(requestHeaders),
                body,
                successPredicates);
    }

    /**
     * Updates a resource using an HTTP PUT request.
     *
     * @param requestOptions The options to use for the request.
     * @param body The content to update the resource with.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if options is {@code null}.
     */
    public Future<HttpResponse<Buffer>> update(
            final RequestOptions requestOptions,
            final Buffer body,
            final ResponsePredicate ... successPredicates) {

        Objects.requireNonNull(requestOptions);

        final Promise<HttpResponse<Buffer>> result = Promise.promise();

        context.runOnContext(go -> {
            final HttpRequest<Buffer> req = client.request(HttpMethod.PUT, requestOptions);
            addResponsePredicates(req, successPredicates);
            if (body == null) {
                req.send(result);
            } else {
                req.sendBuffer(body, result);
            }
        });
        return result.future();
    }

    /**
     * Retrieves a resource representation using an HTTP GET request.
     *
     * @param uri The resource to retrieve.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> get(final String uri, final ResponsePredicate ... successPredicates) {

        Objects.requireNonNull(uri);
        return get(createRequestOptions().setURI(uri), successPredicates);
    }

    /**
     * Retrieves a resource representation using an HTTP GET request.
     *
     * @param uri The resource to retrieve.
     * @param requestHeaders The headers to include in the request.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> get(
            final String uri,
            final MultiMap requestHeaders,
            final ResponsePredicate ... successPredicates) {

        Objects.requireNonNull(uri);
        return get(createRequestOptions().setURI(uri).setHeaders(requestHeaders), successPredicates);
    }

    /**
     * Retrieves a resource representation using a HTTP GET request.
     *
     * @param uri The resource to retrieve.
     * @param requestHeaders The headers to include in the request.
     * @param queryParams The query parameters for the request.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> get(
            final String uri,
            final MultiMap requestHeaders,
            final MultiMap queryParams,
            final ResponsePredicate ... successPredicates) {

        Objects.requireNonNull(uri);
        return get(createRequestOptions().setURI(uri).setHeaders(requestHeaders), queryParams, successPredicates);
    }

    /**
     * Retrieves a resource representation using an HTTP GET request.
     *
     * @param requestOptions The options to use for the request.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     */
    public Future<HttpResponse<Buffer>> get(
            final RequestOptions requestOptions,
            final ResponsePredicate... successPredicates) {

        return get(requestOptions, null, successPredicates);
    }

    /**
     * Retrieves a resource representation using a HTTP GET request.
     *
     * @param requestOptions The options to use for the request.
     * @param queryParams The query parameters for the request.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if options is {@code null}.
     */
    public Future<HttpResponse<Buffer>> get(
            final RequestOptions requestOptions,
            final MultiMap queryParams,
            final ResponsePredicate... successPredicates) {

        Objects.requireNonNull(requestOptions);

        final Promise<HttpResponse<Buffer>> result = Promise.promise();

        context.runOnContext(go -> {
            final HttpRequest<Buffer> req = client.request(HttpMethod.GET, requestOptions);
            addResponsePredicates(req, successPredicates);
            Optional.ofNullable(queryParams)
                    .ifPresent(params -> req.queryParams().addAll(queryParams));
            req.send(result);
        });

        return result.future();
    }

    /**
     * Deletes a resource using an HTTP DELETE request.
     *
     * @param uri The resource to delete.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> delete(final String uri, final ResponsePredicate ... successPredicates) {

        Objects.requireNonNull(uri);

        return delete(createRequestOptions().setURI(uri), successPredicates);
    }

    /**
     * Deletes a resource using an HTTP DELETE request.
     *
     * @param uri The resource to delete.
     * @param requestHeaders The headers to include in the request.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if URI is {@code null}.
     */
    public Future<HttpResponse<Buffer>> delete(
            final String uri,
            final MultiMap requestHeaders,
            final ResponsePredicate ... successPredicates) {

        Objects.requireNonNull(uri);

        return delete(createRequestOptions().setURI(uri).setHeaders(requestHeaders), successPredicates);
    }

    /**
     * Deletes a resource using an HTTP DELETE request.
     *
     * @param requestOptions The options to use for the request.
     * @param successPredicates Checks on the HTTP response that need to pass for the request
     *                          to be considered successful.
     * @return A future indicating the outcome of the request. The future will be completed with the
     *         HTTP response if all checks on the response have succeeded.
     *         Otherwise the future will be failed with the error produced by the first failing
     *         predicate.
     * @throws NullPointerException if options is {@code null}.
     */
    public Future<HttpResponse<Buffer>> delete(
            final RequestOptions requestOptions,
            final ResponsePredicate ... successPredicates) {

        Objects.requireNonNull(requestOptions);

        final Promise<HttpResponse<Buffer>> result = Promise.promise();

        context.runOnContext(go -> {
            final HttpRequest<Buffer> req = client.request(HttpMethod.DELETE, requestOptions);
            addResponsePredicates(req, successPredicates);
            req.send(result);
        });

        return result.future();
    }

    private RequestOptions createRequestOptions() {
        return new RequestOptions()
                .setSsl(options.isSsl())
                .setHost(options.getDefaultHost())
                .setPort(options.getDefaultPort());
    }

    @Override
    public String toString() {
        return String.format("CrudHttpClient [%s:%d]", options.getDefaultHost(), options.getDefaultPort());
    }
}
