/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.management;

import java.net.HttpURLConnection;
import java.util.Optional;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractDelegatingHttpEndpoint;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.RegistryManagementConstants;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;


/**
 * A base class for implementing HTTP endpoints for Hono's Device Registry Management API.
 *
 * @param <S> The type of service this endpoint delegates to.
 * @param <T> The type of configuration properties this endpoint supports.
 */
public abstract class AbstractDelegatingRegistryHttpEndpoint<S, T extends ServiceConfigProperties> extends AbstractDelegatingHttpEndpoint<S, T> {

    /**
     * Creates an endpoint for a service instance.
     *
     * @param vertx The vert.x instance to use.
     * @param service The service to delegate to.
     * @throws NullPointerException if any of the parameters are {@code null};
     */
    protected AbstractDelegatingRegistryHttpEndpoint(final Vertx vertx, final S service) {
        super(vertx, service);
    }

    /**
     * Writes a response based on generic result.
     * <p>
     * The behavior is as follows:
     * <ol>
     * <li>Set the status code on the response.</li>
     * <li>If the status code represents an error condition (i.e. the code is &gt;= 400),
     * then the JSON object passed in the result is written to the response body.</li>
     * <li>If the result is created (the code is = 201), the JSON object is written to the response body and the given custom handler is
     * invoked (if not {@code null}).</li>
     * <li>Sets the status of the tracing span and finishes it.</li>
     * <li>Ends a response.</li>
     * </ol>
     *
     * @param ctx The routing context of the request.
     * @param result The generic result of the operation.
     * @param customHandler An (optional) handler for post processing successful HTTP response, e.g. to set any additional HTTP
     *                      headers. The handler <em>must not</em> write to response body. May be {@code null}.
     * @param span The active OpenTracing span for this operation. The status of the response is logged and span is finished.
     */
    protected final void writeResponse(final RoutingContext ctx, final Result<?> result, final Handler<HttpServerResponse> customHandler, final Span span) {
        final int status = result.getStatus();
        final HttpServerResponse response = ctx.response();
        response.setStatusCode(status);
        if (status >= 400) {
            HttpUtils.setResponseBody(response, JsonObject.mapFrom(result.getPayload()));
        } else if (status == HttpURLConnection.HTTP_CREATED) {
            if (customHandler != null) {
                customHandler.handle(response);
            }
            HttpUtils.setResponseBody(response, JsonObject.mapFrom(result.getPayload()));
        }
        Tags.HTTP_STATUS.set(span, status);
        span.finish();
        response.end();
    }

    /**
     * Writes a response based on operation result (including the resource version).
     * <p>
     * Sets ETAG header and then calls {@link #writeResponse}
     *
     * @param ctx The routing context of the request.
     * @param result The operation result of the operation.
     * @param customHandler An (optional) handler for post processing successful HTTP response, e.g. to set any additional HTTP
     *                      headers. The handler <em>must not</em> write to response body. May be {@code null}.
     * @param span The active OpenTracing span for this operation. The status of the response is logged and span is finished.
     */
    protected final void writeOperationResponse(final RoutingContext ctx, final OperationResult<?> result, final Handler<HttpServerResponse> customHandler, final Span span) {
        result.getResourceVersion().ifPresent(v -> ctx.response().putHeader(HttpHeaders.ETAG, v));
        writeResponse(ctx, result, customHandler, span);
    }

    /**
     * Gets the payload from a request body.
     * <p>
     * The returned JSON object contains the given payload (if not {@code null}).
     * If the given payload does not contain an <em>enabled</em> property, then
     * it is added with value {@code true} to the returned object.
     *
     * @param payload The payload from the request message.
     * @return The payload (never {@code null}).
     */
    protected final JsonObject getRequestPayload(final JsonObject payload) {

        return Optional.ofNullable(payload).map(pl -> {
            final Object obj = pl.getValue(RegistryManagementConstants.FIELD_ENABLED);
            if (obj instanceof Boolean) {
                return pl;
            } else {
                return pl.copy().put(RegistryManagementConstants.FIELD_ENABLED, Boolean.TRUE);
            }
        }).orElse(new JsonObject().put(RegistryManagementConstants.FIELD_ENABLED, Boolean.TRUE));
    }

}
