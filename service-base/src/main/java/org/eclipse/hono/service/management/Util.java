/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistryManagementConstants;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Utility class for the management HTTP API.
 */
public class Util {

    /**
     * Prevent instantiation.
     */
    private Util(){}

    /**
     * Creates a new <em>OpenTracing</em> span for tracing the execution of a service operation.
     *
     * @param operationName The operation name that the span should be created for.
     * @param spanContext Existing span context.
     * @param tracer the Tracer instance.
     * @param className The class name to insert in the Span.
     * @return The new {@code Span}.
     * @throws NullPointerException if operationName is {@code null}.
     */
    public static final Span newChildSpan(final String operationName, final SpanContext spanContext,
            final Tracer tracer, final String className) {
        return newChildSpan(operationName, spanContext, tracer, null, null, className);

    }

    /**
     * Creates a new <em>OpenTracing</em> span for tracing the execution of a service operation.
     * <p>
     * The returned span will already contain tags for the given tenant and device ids (if either is not {@code null}).
     *
     * @param operationName The operation name that the span should be created for.
     * @param spanContext Existing span context.
     * @param tracer the Tracer instance.
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param className The class name to insert in the Span.
     * @return The new {@code Span}.
     * @throws NullPointerException if operationName is {@code null}.
     */
    public static final Span newChildSpan(final String operationName, final SpanContext spanContext,
            final Tracer tracer, final String tenantId, final String deviceId, final String className) {
        Objects.requireNonNull(operationName);
        // we set the component tag to the class name because we have no access to
        // the name of the enclosing component we are running in
        final Tracer.SpanBuilder spanBuilder = TracingHelper.buildChildSpan(tracer, spanContext, operationName)
                .ignoreActiveSpan()
                .withTag(Tags.COMPONENT.getKey(), className)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
        if (tenantId != null) {
            spanBuilder.withTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        }
        if (deviceId != null) {
            spanBuilder.withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        }
        return spanBuilder.start();
    }

    /**
     * Creates a new <em>OpenTracing</em> span for tracing the execution of a tenant service operation.
     * <p>
     * The returned span will already contain tags for the given tenant id (if not {@code null}).
     *
     * @param operationName The operation name that the span should be created for.
     * @param spanContext Existing span context.
     * @param tracer the Tracer instance.
     * @param tenantId The tenant id.
     * @param className The class name to insert in the Span.
     * @return The new {@code Span}.
     * @throws NullPointerException if operationName is {@code null}.
     */
    public static final Span newChildSpan(final String operationName, final SpanContext spanContext,
            final Tracer tracer,
            final String tenantId, final String className) {
        return newChildSpan(operationName, spanContext, tracer, tenantId, null, className);
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
    public static final void writeResponse(final RoutingContext ctx, final Result<?> result, final Handler<HttpServerResponse> customHandler, final Span span) {
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
    public static final void writeOperationResponse(final RoutingContext ctx, final OperationResult<?> result, final Handler<HttpServerResponse> customHandler, final Span span) {
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
    public static final JsonObject getRequestPayload(final JsonObject payload) {

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
