/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.ext.web.RoutingContext;

/**
 * Helper class to store and retrieve the root span of a server HTTP request using the request routing context.
 */
public final class HttpServerSpanHelper {

    /**
     * The key of the routing context map used for storing the root span of an HTTP request.
     */
    public static final String ROUTING_CONTEXT_SPAN_KEY = HttpServerSpanHelper.class.getName() + ".serverSpan";

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerSpanHelper.class);

    private HttpServerSpanHelper() {
    }

    /**
     * Gets the active span created for an HTTP request and stores it in the routing context, so that it is available
     * via {@link #serverSpan(RoutingContext)}. Also applies the given tags on the active span.
     *
     * @param tracer The tracer instance.
     * @param customTags The custom tags to apply to the active span.
     * @param routingContext The routing context to set the span in.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void adoptActiveSpanIntoContext(
            final Tracer tracer,
            final Map<String, String> customTags,
            final RoutingContext routingContext) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(customTags);
        Objects.requireNonNull(routingContext);

        if (routingContext.get(ROUTING_CONTEXT_SPAN_KEY) == null) {
            Optional.ofNullable(tracer.activeSpan()).ifPresentOrElse(
                    span -> {
                        customTags.forEach(span::setTag);
                        routingContext.put(ROUTING_CONTEXT_SPAN_KEY, span);
                    },
                    () -> LOG.warn("no active span set"));
        }
    }

    /**
     * Gets the context of the HTTP server root span for the request associated with the given routing context.
     *
     * @param routingContext The routing context to get the span context from.
     * @return The server span context or {@code null} if not present.
     * @throws NullPointerException if routingContext is {@code null}.
     */
    public static SpanContext serverSpanContext(final RoutingContext routingContext) {
        return Optional.ofNullable(serverSpan(routingContext))
                .map(Span::context)
                .orElse(null);
    }

    /**
     * Gets the HTTP server root span for the request associated with the given routing context.
     *
     * @param routingContext The routing context to get the span from.
     * @return The server span or {@code null} if not present.
     * @throws NullPointerException if routingContext is {@code null}.
     */
    public static Span serverSpan(final RoutingContext routingContext) {
        Objects.requireNonNull(routingContext);
        return Optional.ofNullable(routingContext.get(ROUTING_CONTEXT_SPAN_KEY))
                .filter(Span.class::isInstance)
                .map(Span.class::cast)
                .orElse(null);
    }
}
