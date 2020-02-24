/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import java.net.HttpURLConnection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.AbstractEndpoint;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;


/**
 * Base class for HTTP based Hono endpoints.
 *
 * @param <T> The type of configuration properties this endpoint understands.
 */
public abstract class AbstractHttpEndpoint<T extends ServiceConfigProperties> extends AbstractEndpoint
        implements HttpEndpoint {

    /**
     * The key that is used to put a valid JSON payload to the RoutingContext.
     */
    protected static final String KEY_REQUEST_BODY = "KEY_REQUEST_BODY";
    /**
     * The name of the URI path parameter for the tenant ID.
     */
    protected static final String PARAM_TENANT_ID = "tenant_id";
    /**
     * The name of the URI path parameter for the device ID.
     */
    protected static final String PARAM_DEVICE_ID = "device_id";
    /**
     * The key that is used to put the if-Match ETags values to the RoutingContext.
     */
    protected static final String KEY_RESOURCE_VERSION = "KEY_RESOURCE_VERSION";

    /**
     * The configuration properties for this endpoint.
     */
    protected T config;

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    public AbstractHttpEndpoint(final Vertx vertx) {
        super(vertx);
    }

    /**
     * Sets configuration properties.
     *
     * @param props The properties.
     * @throws NullPointerException if props is {@code null}.
     */
    @Qualifier(Constants.QUALIFIER_REST)
    @Autowired(required = false)
    public final void setConfiguration(final T props) {
        this.config = Objects.requireNonNull(props);
    }

    /**
     * Check the Content-Type of the request to be 'application/json' and extract the payload if this check was
     * successful.
     * <p>
     * The payload is parsed to ensure it is valid JSON and is put to the RoutingContext ctx with the key
     * {@link #KEY_REQUEST_BODY}.
     *
     * @param ctx The routing context to retrieve the JSON request body from.
     * @param payloadExtractor The extractor of the payload from the context.
     */
    protected final void extractRequiredJson(final RoutingContext ctx, final Function<RoutingContext, Object> payloadExtractor) {

        Objects.requireNonNull(payloadExtractor);

        final MIMEHeader contentType = ctx.parsedHeaders().contentType();
        if (contentType == null) {
            ctx.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "Missing Content-Type header"));
        } else if (!HttpUtils.CONTENT_TYPE_JSON.equalsIgnoreCase(contentType.value())) {
            ctx.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "Unsupported Content-Type"));
        } else {
            try {
                if (ctx.getBody() != null) {
                    final var payload = payloadExtractor.apply(ctx);
                    if (payload != null) {
                        ctx.put(KEY_REQUEST_BODY, payload);
                        ctx.next();
                    } else {
                        ctx.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "Null body"));
                    }
                } else {
                    ctx.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "Empty body"));
                }
            } catch (final DecodeException e) {
                ctx.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "Invalid JSON", e));
            }
        }

    }

    /**
     * Check the Content-Type of the request to be 'application/json' and extract the payload if this check was
     * successful.
     * <p>
     * The payload is parsed to ensure it is valid JSON and is put to the RoutingContext ctx with the key
     * {@link #KEY_REQUEST_BODY}.
     *
     * @param ctx The routing context to retrieve the JSON request body from.
     */
    protected final void extractRequiredJsonPayload(final RoutingContext ctx) {
        extractRequiredJson(ctx, RoutingContext::getBodyAsJson);
    }

    /**
     * Check if the payload is not null and call \
     * {@link #extractRequiredJson(RoutingContext, Function)}} to extract it accordingly.
     * <p>
     *
     * @param ctx The routing context to retrieve the JSON request body from.
     */
    protected final void extractOptionalJsonPayload(final RoutingContext ctx) {

        if (ctx.getBody().length() != 0) {
            extractRequiredJson(ctx, RoutingContext::getBodyAsJson);
        } else {
            ctx.put(KEY_REQUEST_BODY, new JsonObject());
            ctx.next();
        }
    }

    /**
     * Check the Content-Type of the request to be 'application/json' and extract the payload if this check was
     * successful.
     * <p>
     * The payload is parsed to ensure it is valid JSON and is put to the RoutingContext ctx with the
     * key {@link #KEY_REQUEST_BODY}.
     *
     * @param ctx The routing context to retrieve the JSON request body from.
     */
    protected final void extractRequiredJsonArrayPayload(final RoutingContext ctx) {
        extractRequiredJson(ctx, body -> {
            return body.getBodyAsJsonArray();
        });
    }

    /**
     * Get the tenantId from the standard parameter name {@link #PARAM_TENANT_ID}.
     *
     * @param ctx The routing context of the request.
     * @return The tenantId retrieved from the request.
     * @throws NullPointerException If ctx is null.
     */
    protected final String getTenantParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TENANT_ID);
    }

    /**
     * Get the deviceId from the standard parameter name {@link #PARAM_DEVICE_ID}.
     *
     * @param ctx The routing context of the request.
     * @return The deviceId retrieved from the request.
     * @throws NullPointerException If ctx is null.
     */
    protected final String getDeviceIdParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_DEVICE_ID);
    }

    /**
     * Get request parameter value and check if it has been set. If it's not set, fail the request.
     *
     * @param paramName The name of the parameter to get.
     * @param ctx The routing context of the request.
     * @param span The active OpenTracing span for this operation. In case of the missing mandatory parameter, the error is logged and the span is finished.
     *             Otherwise, the parameter is set as a span tag.
     * @return The value of the parameter if it's set or {@code null} otherwise.
     * @throws NullPointerException If ctx or paramName are {@code null}.
     */
    protected final String getMandatoryRequestParam(final String paramName, final RoutingContext ctx, final Span span) {
        return getRequestParam(paramName, ctx, span, false);
    }

    /**
     * Get request parameter value. Optionally, if parameter has not been set, fail the request.
     *
     * @param paramName The name of the parameter to get.
     * @param ctx The routing context of the request.
     * @param span The active OpenTracing span for this operation. In case of the missing mandatory parameter, the error is logged and the span is finished.
     *             Otherwise, the parameter is set as a span tag.
     * @param optional Whether to check if parameter has been set or not.
     * @return The value of the parameter if it's set or {@code null} otherwise.
     * @throws NullPointerException If ctx or paramName are {@code null}.
     */
    protected final String getRequestParam(final String paramName, final RoutingContext ctx, final Span span, final boolean optional) {
        final String value = ctx.request().getParam(paramName);
        if (!optional && value == null) {
            final String msg = String.format("Missing request parameter: %s", paramName);
            TracingHelper.logError(span, msg);
            HttpUtils.badRequest(ctx, msg);
            Tags.HTTP_STATUS.set(span, HttpURLConnection.HTTP_BAD_REQUEST);
            span.finish();
            return null;
        } else {
            span.setTag(paramName, value);
            return value;
        }
    }

    /**
     * Check the ETags values from the HTTP if-Match header if they exist,
     * and extract the values if this check was successful
     * <p>
     * The HTTP header "If-Match"is parsed and the values are put to the routingContext ctx with the
     * key {@link #KEY_RESOURCE_VERSION}.
     *
     * @param ctx The routing context of the request.
     */
    protected void extractIfMatchVersionParam(final RoutingContext ctx) {
        final String ifMatchHeader = ctx.request().getHeader(HttpHeaders.IF_MATCH);
        if (! Strings.isNullOrEmpty(ifMatchHeader)) {
                ctx.put(KEY_RESOURCE_VERSION, ifMatchHeader);
        }
        ctx.next();
    }

    /**
     * Creates default CORS handler that allows 'POST', 'GET', 'PUT' and 'DELETE' methods for the specified origin.
     *
     * @param allowedOrigin The allowed origin pattern.
     * @return The handler.
     */
    protected final CorsHandler createDefaultCorsHandler(final String allowedOrigin) {
        return createCorsHandler(allowedOrigin, EnumSet.of(
                HttpMethod.POST,
                HttpMethod.GET,
                HttpMethod.PUT,
                HttpMethod.DELETE)
        );
    }

    /**
     * Creates CORS Handler that allows HTTP methods for the specified origin.
     *
     * @param allowedOrigin The allowed origin pattern.
     * @param methods Set of allowed HTTP methods
     * @return The handler.
     */
    protected final CorsHandler createCorsHandler(final String allowedOrigin, final Set<HttpMethod> methods) {
        return CorsHandler.create(allowedOrigin)
                .allowedMethods(methods)
                .allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
                .allowedHeader(HttpHeaders.AUTHORIZATION.toString())
                .allowedHeader(HttpHeaders.IF_MATCH.toString())
                .exposedHeader(HttpHeaders.ETAG.toString());
    }

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
