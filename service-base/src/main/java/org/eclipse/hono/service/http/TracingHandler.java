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

package org.eclipse.hono.service.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.hono.tracing.TracingHelper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

/**
 * Handler which creates tracing data for all server requests. It should be added to
 * {@link io.vertx.ext.web.Route#handler(Handler)} and {@link io.vertx.ext.web.Route#failureHandler(Handler)} as the
 * first in the chain.
 * <p>
 * This class has been copied from the <a href="https://github.com/opentracing-contrib/java-vertx-web">
 * OpenTracing Vert.x Web Instrumentation</a> project.
 * It has been adapted to support Opentracing 0.33 and slightly adapted to Hono's code style guide.
 *
 * @author Pavol Loffay
 */
public class TracingHandler implements Handler<RoutingContext> {

    public static final String CURRENT_SPAN = TracingHandler.class.getName() + ".severSpan";

    private static final Logger log = LoggerFactory.getLogger(TracingHandler.class);

    private final Tracer tracer;
    private final List<WebSpanDecorator> decorators;

    /**
     * Creates a new handler for an OpenTracing Tracer.
     *
     * @param tracer The tracer to use for tracking the processing of HTTP requests.
     */
    public TracingHandler(final Tracer tracer) {
        this(tracer, Collections.singletonList(new WebSpanDecorator.StandardTags()));
    }

    /**
     * Creates a new handler for an OpenTracing Tracer.
     *
     * @param tracer The tracer to use for tracking the processing of HTTP requests.
     * @param decorators The decorators to invoke before and after each HTTP request
     *                   gets processed.
     */
    public TracingHandler(final Tracer tracer, final List<WebSpanDecorator> decorators) {
        this.tracer = tracer;
        this.decorators = new ArrayList<>(decorators);
    }

    @Override
    public void handle(final RoutingContext routingContext) {
        if (routingContext.failed()) {
            handlerFailure(routingContext);
        } else {
            handlerNormal(routingContext);
        }
    }

    /**
     * Handles an HTTP request.
     *
     * @param routingContext The routing context for the request.
     */
    protected void handlerNormal(final RoutingContext routingContext) {

        // reroute
        final Object object = routingContext.get(CURRENT_SPAN);
        if (object instanceof Span) {
            final Span span = (Span) object;
            decorators.forEach(spanDecorator ->
                    spanDecorator.onReroute(routingContext.request(), span));

            // TODO in 3.3.3 it was sufficient to add this when creating the span
            routingContext.addBodyEndHandler(finishEndHandler(routingContext, span));
            routingContext.next();
            return;
        }

        final SpanContext extractedContext = TracingHelper.extractSpanContext(tracer, routingContext.request().headers());

        final Span span = tracer.buildSpan(routingContext.request().method().toString())
                .asChildOf(extractedContext)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .start();

        decorators.forEach(spanDecorator ->
                spanDecorator.onRequest(routingContext.request(), span));

        routingContext.put(CURRENT_SPAN, span);
        // TODO it's not guaranteed that body end handler is always called
        // https://github.com/vert-x3/vertx-web/issues/662
        routingContext.addBodyEndHandler(finishEndHandler(routingContext, span));
        routingContext.next();
    }

    /**
     * Handles a failed HTTP request.
     *
     * @param routingContext The routing context for the request.
     */
    protected void handlerFailure(final RoutingContext routingContext) {
        final Object object = routingContext.get(CURRENT_SPAN);
        if (object instanceof Span) {
            final Span span = (Span) object;
            routingContext.addBodyEndHandler(event -> decorators.forEach(spanDecorator ->
                    spanDecorator.onFailure(routingContext.failure(), routingContext.response(), span)));
        }

        routingContext.next();
    }

    private Handler<Void> finishEndHandler(final RoutingContext routingContext, final Span span) {
        return handler -> {
            decorators.forEach(spanDecorator ->
                    spanDecorator.onResponse(routingContext.request(), span));
            span.finish();
        };
    }

    /**
     * Helper method for accessing server span context associated with current request.
     *
     * @param routingContext routing context
     * @return server span context or null if not present
     */
    public static SpanContext serverSpanContext(final RoutingContext routingContext) {
        SpanContext serverContext = null;

        final Object object = routingContext.get(CURRENT_SPAN);
        if (object instanceof Span) {
            final Span span = (Span) object;
            serverContext = span.context();
        } else {
            log.error("Sever SpanContext is null or not an instance of SpanContext");
        }

        return serverContext;
    }
}
