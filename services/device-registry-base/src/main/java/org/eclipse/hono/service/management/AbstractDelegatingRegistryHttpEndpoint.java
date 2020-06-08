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

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractDelegatingHttpEndpoint;
import org.eclipse.hono.service.http.HttpUtils;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
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
     * Creates a predicate for testing values against a pattern.
     *
     * @param testPattern The pattern to test against.
     * @param optional {@code true} if the values to check are optional.
     * @return The predicate for testing values.
     *         The predicate's test method will return {@code true}
     *         <ul>
     *         <li>if the value matches the pattern or</li>
     *         <li>if the value is {@code null} and the <em>optional</em> flag is {@code true}.</li>
     *         </ul>
     *         Otherwise the predicate will throw an {@code IllegalArgumentException}
     *         containing a message indicating the cause of the failed check.
     */
    protected static Predicate<String> getPredicate(final Pattern testPattern, final boolean optional) {

        Objects.requireNonNull(testPattern);

        return value -> {
            final Matcher matcher = testPattern.matcher(Optional.ofNullable(value).orElse(""));
            if (matcher.matches()) {
                return true;
            } else if (value == null) {
                if (optional) {
                    return true;
                } else {
                    throw new IllegalArgumentException("mandatory value must not be null");
                }
            } else {
                throw new IllegalArgumentException(String.format("value does not match pattern [%s]", testPattern));
            }
        };
    }
    /**
     * Writes a response based on generic result.
     * <p>
     * This method delegates to {@link #writeResponse(RoutingContext, Result, BiConsumer, Span)}
     * passing {@code null} as the custom handler.
     *
     * @param ctx The routing context of the request.
     * @param result The generic result of the operation.
     * @param span The active OpenTracing span for this operation. The status of the response is logged and span is finished.
     */
    protected final void writeResponse(final RoutingContext ctx, final Result<?> result, final Span span) {
        writeResponse(ctx, result, (BiConsumer<MultiMap, Integer>) null, span);
    }

    /**
     * Writes a response based on generic result.
     * <p>
     * The behavior is as follows:
     * <ol>
     * <li>Set the status code on the response.</li>
     * <li>Try to serialize the object contained in the result to JSON and use it as the response body.</li>
     * <li>If the result is an {@code OperationResult} and contains a resource version, add an ETAG header
     * to the response using the version as its value.</li>
     * <li>If the handler is not {@code null}, invoke it with the response and the status code from the result.</li>
     * <li>Set the span's <em>http.status</em> tag to the result's status code.</li>
     * <li>End the response.</li>
     * </ol>
     *
     * @param ctx The context to write the response to.
     * @param result The generic result of the operation.
     * @param customHandler An (optional) handler for post processing successful HTTP response, e.g. to set any additional HTTP
     *                      headers. The handler <em>must not</em> write to response body. May be {@code null}.
     * @param span The active OpenTracing span for this operation.
     */
    protected final void writeResponse(final RoutingContext ctx, final Result<?> result, final BiConsumer<MultiMap, Integer> customHandler, final Span span) {
        final int status = result.getStatus();
        final HttpServerResponse response = ctx.response();
        response.setStatusCode(status);
        if (result instanceof OperationResult) {
            ((OperationResult<?>) result).getResourceVersion().ifPresent(version -> response.putHeader(HttpHeaders.ETAG, version));
        }
        if (customHandler != null) {
            customHandler.accept(response.headers(), status);
        }
        // once the body has been written, the response headers can no longer be changed
        HttpUtils.setResponseBody(response, asJson(result.getPayload()), HttpUtils.CONTENT_TYPE_JSON_UTF8);
        Tags.HTTP_STATUS.set(span, status);
        response.end();
    }

    private Buffer asJson(final Object obj) {
        try {
            return Json.encodeToBuffer(obj);
        } catch (final IllegalArgumentException e) {
            logger.debug("error serializing result object [type: {}] to JSON", obj.getClass().getName(), e);
            return null;
        }
    }
}
