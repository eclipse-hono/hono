/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.service.http;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.Strings;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;


/**
 * A base class for HTTP based Hono endpoints which forward
 * incoming requests to a service implementation via the vert.x
 * event bus.
 *
 * @param <T> The type of configuration properties this endpoint understands.
 * @deprecated Use {@link AbstractHttpEndpoint} instead.
 */
@Deprecated(forRemoval = true)
public abstract class AbstractEventBusHttpEndpoint<T extends ServiceConfigProperties> extends AbstractHttpEndpoint<T>
        implements HttpEndpoint {

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    public AbstractEventBusHttpEndpoint(final Vertx vertx) {
        super(vertx);
    }

    /**
     * Get the event bus address used for the HTTP endpoint. Each HTTP endpoint should have it's own, unique address that
     * is returned by implementing this method.
     *
     * @return The event bus address for processing HTTP requests.
     */
    protected abstract String getEventBusAddress();

    /**
     * Get a response handler that implements the default behavior for responding to the HTTP request (except for adding an object).
     *
     * @param ctx The routing context of the request.
     * @return A consumer for the status and the JSON object that implements the default behavior for responding to the HTTP request.
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
     *                        {@link org.eclipse.hono.util.MessageHelper#APP_PROPERTY_STATUS} field and the <em>payload</em>
     *                        retrieved from the {@link org.eclipse.hono.util.RequestResponseApiConstants#FIELD_PAYLOAD} field.
     * @throws NullPointerException If the routing context is {@code null}.
     */
    protected final void sendAction(final RoutingContext ctx, final JsonObject requestMsg,
            final BiConsumer<Integer, EventBusMessage> responseHandler) {

        final DeliveryOptions options = createEventBusMessageDeliveryOptions(config.getSendTimeOut(),
                TracingHandler.serverSpanContext(ctx));
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
}
