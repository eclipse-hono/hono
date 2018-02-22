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

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.hono.service.AbstractEndpoint;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.vertx.core.Vertx;


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

    // path parameters for capturing parts of the URI path
    protected static final String PARAM_TENANT_ID = "tenant_id";
    protected static final String PARAM_DEVICE_ID = "device_id";


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
     * The payload is parsed to ensure it is valid JSON and is put to the RoutingContext ctx with the
     * key {@link #KEY_REQUEST_BODY}.
     *
     * @param ctx The routing context to retrieve the JSON request body from.
     */
    protected void extractRequiredJsonPayload(final RoutingContext ctx) {

        final MIMEHeader contentType = ctx.parsedHeaders().contentType();
        if (contentType == null) {
            ctx.response().setStatusMessage("Missing Content-Type header");
            ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
        } else if (!HttpUtils.CONTENT_TYPE_JSON.equalsIgnoreCase(contentType.value())) {
            ctx.response().setStatusMessage("Unsupported Content-Type");
            ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
        } else {
            try {
                if (ctx.getBody() != null) {
                    ctx.put(KEY_REQUEST_BODY, ctx.getBodyAsJson());
                    ctx.next();
                } else {
                    ctx.response().setStatusMessage("Empty body");
                    ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
                }
            } catch (final DecodeException e) {
                ctx.response().setStatusMessage("Invalid JSON");
                ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
            }
        }
    }

    /**
     * Set the body of the response for the HTTP request as JSON. It is retrieved from the passed in JSON result (received as answer from a request to the event bus) by
     * getting the key {@link RequestResponseApiConstants#FIELD_PAYLOAD}.
     *
     * @param jsonResult The JSON result returned from the processing event bus consumer.
     * @param response The HTTP response to which the body is written.
     */
    protected void setResponseBody(final JsonObject jsonResult, final HttpServerResponse response) {
        final JsonObject msg = jsonResult.getJsonObject(RequestResponseApiConstants.FIELD_PAYLOAD);
        if (msg != null) {
            final String body = msg.encodePrettily();
            response.putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON_UFT8)
                    .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length()))
                    .write(body);
        }
    }

    /**
     * Get a response handler that implements the default behaviour for responding to the HTTP request (except for adding an object).
     *
     * @param ctx The routing context of the request.
     * @return BiConsumer&lt;Integer, JsonObject&gt; A consumer for the status and the JSON object that implements the default behaviour for responding to the HTTP request.
     * @throws NullPointerException If ctx is null.
     */
    protected final BiConsumer<Integer, JsonObject> getDefaultResponseHandler(final RoutingContext ctx) {
        return getDefaultResponseHandler(ctx, null, null);
    }

    /**
     * Get a response handler that implements the default behaviour for responding to the HTTP request.
     *
     * @param ctx The routing context of the request.
     * @param setResponseBodyForStatus A Predicate that defines for which status a response body shall be set. Maybe null.
     * @param responseHandler A handler that enables post processing the http response, e.g. to set the HTTP location header {@link HttpHeaders#LOCATION}
     *                       in case of an add action. Maybe null.
     * @throws NullPointerException If ctx is null.
     */
    protected final BiConsumer<Integer, JsonObject> getDefaultResponseHandler(
            final RoutingContext ctx,
            final Predicate<Integer> setResponseBodyForStatus,
            final Handler<HttpServerResponse> responseHandler
            ) {

        final HttpServerResponse response = ctx.response();

        return (status, jsonResult) -> {
            response.setStatusCode(status);
            if (status >= 400) {
                setResponseBody(jsonResult, response);
            } else if (setResponseBodyForStatus != null) {
                if (setResponseBodyForStatus.test(status)) {
                    if (responseHandler != null) {
                        responseHandler.handle(response);
                    }
                    setResponseBody(jsonResult, response);
                }
            }
            response.end();
        };
    }

    /**
     * Sending a request message to a consumer on the event bus for further processing.
     *
     * @param ctx The routing context of the request.
     * @param requestMsg The JSON object to send via the event bus.
     * @param responseHandler The handler to be invoked for the response received as answer from the event bus.
     * @throws NullPointerException If ctx is null.
     */
    protected final void sendAction(final RoutingContext ctx, final JsonObject requestMsg, final BiConsumer<Integer, JsonObject> responseHandler) {

        vertx.eventBus().send(getEventBusAddress(), requestMsg,
                invocation -> {
                    if (invocation.failed()) {
                        HttpUtils.serviceUnavailable(ctx, 2);
                    } else {
                        final JsonObject jsonResult = (JsonObject) invocation.result().body();
                        final Integer status = jsonResult.getInteger(MessageHelper.APP_PROPERTY_STATUS);
                        responseHandler.accept(status, jsonResult);
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

}
