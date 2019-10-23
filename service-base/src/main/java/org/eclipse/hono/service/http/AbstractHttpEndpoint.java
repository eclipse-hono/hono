/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntPredicate;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.AbstractEndpoint;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.opentracing.contrib.vertx.ext.web.TracingHandler;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
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
public abstract class AbstractHttpEndpoint<T> extends AbstractEndpoint implements HttpEndpoint {

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
     * Get the event bus address used for the HTTP endpoint. Each HTTP endpoint should have it's own, unique address that
     * is returned by implementing this method.
     *
     * @return The event bus address for processing HTTP requests.
     */
    protected abstract String getEventBusAddress();

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
            final var payload = body.getBodyAsJsonArray();
            if (payload != null && payload.getList() == null) {
                // work around eclipse-vertx/vert.x#2993
                return null;
            }
            return payload;
        });
    }

    /**
     * Get a response handler that implements the default behavior for responding to the HTTP request (except for adding an object).
     *
     * @param ctx The routing context of the request.
     * @return BiConsumer&lt;Integer, JsonObject&gt; A consumer for the status and the JSON object that implements the default behavior for responding to the HTTP request.
     * @throws NullPointerException If ctx is null.
     */
    protected final BiConsumer<Integer, EventBusMessage> getDefaultResponseHandler(final RoutingContext ctx) {
        return getDefaultResponseHandler(ctx, status -> false , (Handler<HttpServerResponse>) null);
    }

    /**
     * Gets a response handler that implements the default behavior for responding to an HTTP request.
     * <p>
     * The default behavior is as follows:
     * <ol>
     * <li>Set the status code on the response.</li>
     * <li>If the status code represents an error condition (i.e. the code is &gt;= 400),
     * then the JSON object passed in to the returned handler is written to the response body.</li>
     * <li>Otherwise, if the given filter evaluates to {@code true} for the status code,
     * the JSON object is written to the response body and the given custom handler is
     * invoked (if not {@code null}).</li>
     * </ol>
     *
     * @param ctx The routing context of the request.
     * @param successfulOutcomeFilter A predicate that evaluates to {@code true} for the status code(s) representing a
     *                           successful outcome.
     * @param customHandler An (optional) handler for post processing the HTTP response, e.g. to set any additional HTTP
     *                        headers. The handler <em>must not</em> write to response body. May be {@code null}.
     * @return The created handler for processing responses.
     * @throws NullPointerException If routing context or filter is {@code null}.
     */
    protected final BiConsumer<Integer, EventBusMessage> getDefaultResponseHandler(
            final RoutingContext ctx,
            final IntPredicate successfulOutcomeFilter,
            final Handler<HttpServerResponse> customHandler) {

        Objects.requireNonNull(successfulOutcomeFilter);
        final HttpServerResponse response = ctx.response();

        return (status, responseMessage) -> {
            response.setStatusCode(status);
            if (status >= 400) {
                HttpUtils.setResponseBody(response, responseMessage.getJsonPayload());
            } else if (successfulOutcomeFilter.test(status)) {
                HttpUtils.setResponseBody(response, responseMessage.getJsonPayload());
                if (customHandler != null) {
                    customHandler.handle(response);
                }
            }
            response.end();
        };
    }

    /**
     * Gets a response handler that implements the default behavior for responding to an HTTP request.
     * <p>
     * The default behavior is as follows:
     * <ol>
     * <li>Set the status code on the response.</li>
     * <li>If the status code represents an error condition (i.e. the code is &gt;= 400),
     * then the JSON object passed in to the returned handler is written to the response body.</li>
     * <li>Otherwise, if the given filter evaluates to {@code true} for the status code,
     * the given custom handler is invoked (if not {@code null}), then
     * the JSON object is written to the response body and </li>
     * </ol>
     *
     * @param ctx The routing context of the request.
     * @param successfulOutcomeFilter A predicate that evaluates to {@code true} for the status code(s) representing a
     *                           successful outcome.
     * @param customHandler An (optional) handler for post processing the HTTP response, e.g. to set any additional HTTP
     *                        headers. The handler <em>must not</em> write to response body. May be {@code null}.
     * @return The created handler for processing responses.
     * @throws NullPointerException If routing context or filter is {@code null}.
     */
    protected final BiConsumer<Integer, EventBusMessage> getDefaultResponseHandler(
            final RoutingContext ctx,
            final IntPredicate successfulOutcomeFilter,
            final BiConsumer<HttpServerResponse, EventBusMessage> customHandler) {

        Objects.requireNonNull(successfulOutcomeFilter);
        final HttpServerResponse response = ctx.response();

        return (status, result) -> {
            response.setStatusCode(status);
            if (status >= 400) {
                HttpUtils.setResponseBody(response, result.getJsonPayload());
            } else if (successfulOutcomeFilter.test(status)) {
                if (customHandler != null) {
                    customHandler.accept(response, result);
                }
                HttpUtils.setResponseBody(response, result.getJsonPayload());
            }
            response.end();
        };
    }

    /**
     * Sends a request message to an address via the vert.x event bus for further processing.
     * <p>
     * The address is determined by invoking {@link #getEventBusAddress()}.
     *
     * @param ctx The routing context of the request.
     * @param requestMsg The JSON object to send via the event bus.
     * @param responseHandler The handler to be invoked for the message received in response to the request.
     *                        <p>
     *                        The handler will be invoked with the <em>status code</em> retrieved from the
     *                        {@link MessageHelper#APP_PROPERTY_STATUS} field and the <em>payload</em>
     *                        retrieved from the {@link RequestResponseApiConstants#FIELD_PAYLOAD} field.
     * @throws NullPointerException If the routing context is {@code null}.
     */
    protected final void sendAction(final RoutingContext ctx, final JsonObject requestMsg,
            final BiConsumer<Integer, EventBusMessage> responseHandler) {

        final DeliveryOptions options = createEventBusMessageDeliveryOptions(TracingHandler.serverSpanContext(ctx));
        vertx.eventBus().request(getEventBusAddress(), requestMsg, options, invocation -> {
            if (invocation.failed()) {
                HttpUtils.serviceUnavailable(ctx, 2);
            } else {
                final EventBusMessage response = EventBusMessage.fromJson((JsonObject) invocation.result().body());

                final Integer status = response.getStatus();

                final String version = response.getResourceVersion();
                if (! Strings.isNullOrEmpty(version)) {
                    ctx.response().putHeader(HttpHeaders.ETAG, version);
                }

                responseHandler.accept(status, response);
            }
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
}
