/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.opentracing.Span;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;


/**
 * A generic handler for {@link ServiceInvocationException}s that have caused
 * an HTTP request to fail.
 * <p>
 * This handler can be registered on a {@code io.vertx.ext.web.Route} using its
 * <em>failureHandler</em> method. The handler inspects the route's <em>failure</em>
 * property and sets the HTTP response's status code and body based on the exception
 * type. If the error is a {@code ServiceInvocationException} then the response's
 * status code and body are set to the exception's <em>errorCode</em> and <em>message</em>
 * property values respectively. Otherwise the status code is set to the one from the HTTP
 * response, if it is an error status code, or otherwise it is set to 500.
 */
public class DefaultFailureHandler implements Handler<RoutingContext> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultFailureHandler.class);
    private static final String ERROR_DETAIL_NOT_AVAILABLE = "N/A";

    /**
     * Handles routing failures.
     * <p>
     * This method simply delegates to the next handler if the response is already
     * ended or the context is not failed.
     *
     * @param ctx The failing routing context.
     */
    @Override
    public void handle(final RoutingContext ctx) {

        if (ctx.failed()) {

            if (ctx.response().ended()) {
                LOG.debug("skipping processing of failed route, response already ended");
            } else {
                final Span span = HttpServerSpanHelper.serverSpan(ctx);
                if (ctx.failure() != null) {
                    final int statusCode;
                    if (ctx.failure() instanceof ServiceInvocationException ex) {
                        statusCode = ex.getErrorCode();
                    } else if (ctx.failure() instanceof HttpException ex) {
                        statusCode = ex.getStatusCode();
                    } else if (ctx.statusCode() >= 400 && ctx.statusCode() < 600) {
                        statusCode = ctx.statusCode();
                    } else {
                        statusCode = HttpURLConnection.HTTP_INTERNAL_ERROR;
                    }
                    logError(ctx, statusCode);
                    Optional.ofNullable(span)
                            .ifPresent(s -> logErrorInTraceSpan(span, ctx.failure(), statusCode));
                    sendError(ctx.response(), statusCode, ctx.failure().getMessage());

                } else if (ctx.statusCode() != -1) {
                    logError(ctx, null);
                    if (span != null) {
                        final String message = HttpResponseStatus.valueOf(ctx.statusCode()).reasonPhrase();
                        TracingHelper.logError(span, message);
                    }
                    sendError(ctx.response(), ctx.statusCode(), null);

                } else {
                    logError(ctx, null);
                    Optional.ofNullable(span)
                            .ifPresent(s -> TracingHelper.logError(span, "unspecified error occurred"));
                    sendError(ctx.response(), HttpURLConnection.HTTP_INTERNAL_ERROR, "Internal Server Error");
                }
            }
        } else {
            LOG.debug("skipping processing of non-failed route");
            ctx.next();
        }
    }

    private static void logError(final RoutingContext ctx, final Integer mappedStatusCode) {
        if (LOG.isDebugEnabled()) {
            final int status = Optional.ofNullable(mappedStatusCode).orElseGet(ctx::statusCode);
            if (ctx.failure() instanceof ClientErrorException) {
                // skip printing error stacktrace here
                LOG.debug("handling failed route for request [method: {}, URI: {}, status: {}]; error: {}",
                        ctx.request().method(), HttpUtils.getAbsoluteURI(ctx.request()), status, ctx.failure().toString());
            } else {
                LOG.debug("handling failed route for request [method: {}, URI: {}, status: {}]",
                        ctx.request().method(), HttpUtils.getAbsoluteURI(ctx.request()), status, ctx.failure());
            }
        }
    }

    /**
     * Creates payload for an error message.
     * <p>
     * This default implementation creates a JSON object with a single <em>error</em> property.
     *
     * @param errorMessage The error message. If {@code null}, the payload will contain <em>N/A</em> as the error
     *                     property's value.
     * @return The payload.
     */
    protected Buffer createResponsePayload(final String errorMessage) {
        return new JsonObject()
                .put(RequestResponseApiConstants.FIELD_ERROR, Optional.ofNullable(errorMessage).orElse(ERROR_DETAIL_NOT_AVAILABLE))
                .toBuffer();
    }

    /**
     * Has to be invoked before the span is finished in response.end().
     */
    private void logErrorInTraceSpan(final Span span, final Throwable error, final int errorCode) {
        final boolean skipUnexpectedErrorCheck = errorCode >= 400 && errorCode < 500; // client error
        TracingHelper.logError(span, null, error, skipUnexpectedErrorCheck);
    }

    private void sendError(final HttpServerResponse response, final int errorCode, final String errorMessage) {

        response.setStatusCode(errorCode);
        HttpUtils.setResponseBody(response, createResponsePayload(errorMessage), HttpUtils.CONTENT_TYPE_JSON_UTF8);
        response.end();
    }
}
